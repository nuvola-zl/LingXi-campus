package top.lingxi.campus.admin.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.lingxi.campus.admin.user.service.DeviceRequestService;
import top.lingxi.campus.common.context.BaseContext;
import top.lingxi.campus.common.id.service.SerialNumberService;
import top.lingxi.campus.domain.admin.eneity.AdminDeviceDetail;
import top.lingxi.campus.domain.admin.eneity.AdminDeviceInventory;
import top.lingxi.campus.domain.admin.eneity.AdminDeviceRequest;
import top.lingxi.campus.domain.admin.eneity.AdminDeviceReturn;
import top.lingxi.campus.domain.admin.eneity.AdminPurchaseRequest;
import top.lingxi.campus.domain.admin.mapper.AdminDeviceDetailMapper;
import top.lingxi.campus.domain.admin.mapper.AdminDeviceInventoryMapper;
import top.lingxi.campus.domain.admin.mapper.AdminDeviceRequestMapper;
import top.lingxi.campus.domain.admin.mapper.AdminDeviceReturnMapper;
import top.lingxi.campus.domain.admin.mapper.AdminPurchaseRequestMapper;

import java.time.LocalDateTime;

/**
 * 设备申领服务
 *
 * 重构变更（DAG 移除，业务能力保留）：
 * 1. onPurchaseArrived：入库后【直接执行分配】（替代原 DAG resumeOnArrived 恢复编排），
 *    申领单从"采购中"直达"待领取"；分配失败（极端并发）保留"准备中"由管理员处理
 * 2. 新增 confirmReturn：从 AdminManageController 下移的设备归还确认业务
 * 3. 构造注入统一（原为字段注入）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceRequestServiceImpl implements DeviceRequestService {

    private final AdminDeviceRequestMapper deviceRequestMapper;
    private final AdminDeviceInventoryMapper inventoryMapper;
    private final AdminDeviceDetailMapper detailMapper;
    private final AdminPurchaseRequestMapper purchaseMapper;
    private final AdminDeviceReturnMapper returnMapper;
    private final SerialNumberService serialService;

    /**
     * 提交设备申领（不做分配，分配由调用方按需触发）
     */
    @Override
    @Transactional
    public AdminDeviceRequest submitRequest(Long userId, String deviceType, String reason) {
        String requestNo = serialService.generate("DEV");

        QueryWrapper<AdminDeviceInventory> wrapper = new QueryWrapper<>();
        wrapper.eq("device_type", deviceType);
        AdminDeviceInventory inventory = inventoryMapper.selectOne(wrapper);

        boolean hasStock = inventory != null && inventory.getAvailableCount() > 0;

        AdminDeviceRequest request = new AdminDeviceRequest();
        request.setRequestNo(requestNo);
        request.setUserId(userId);
        request.setDeviceType(deviceType);
        request.setReason(reason);
        request.setStatus(hasStock ? 3 : 2); // 3准备中(有库存)  2采购中(无库存)
        request.setStockStatus(hasStock ? 1 : 2);
        request.setCreatedAt(LocalDateTime.now());

        deviceRequestMapper.insert(request);
        return request;
    }

    /**
     * 分配设备（悲观锁防超卖）
     */
    @Override
    @Transactional
    public void allocateDevice(String requestNo) {
        AdminDeviceRequest request = getByRequestNo(requestNo);

        // 1. 悲观锁查库存
        AdminDeviceInventory inventory = inventoryMapper.selectForUpdate(request.getDeviceType());
        if (inventory == null || inventory.getAvailableCount() <= 0) {
            throw new RuntimeException("库存不足");
        }

        // 2. 分配设备
        AdminDeviceDetail device = detailMapper.findAvailable(request.getDeviceType());
        if (device == null) {
            throw new RuntimeException("无可用设备");
        }
        device.setStatus(2);
        device.setAssignedUserId(request.getUserId());
        device.setAssignedAt(LocalDateTime.now());
        detailMapper.updateById(device);

        // 3. 扣减库存
        inventory.setAvailableCount(inventory.getAvailableCount() - 1);
        inventoryMapper.updateById(inventory);

        // 4. 更新申领单为待领取
        request.setAllocatedDeviceId(device.getId());
        request.setStatus(4); // 4=待领取
        deviceRequestMapper.updateById(request);
    }

    /**
     * 发起采购（无库存申领的同步后续步骤，替代原 DAG createPurchase 节点）
     */
    @Override
    @Transactional
    public String createPurchaseOrder(String requestNo) {
        AdminDeviceRequest request = getByRequestNo(requestNo);

        String orderNo = serialService.generate("PO");

        AdminPurchaseRequest purchase = new AdminPurchaseRequest();
        purchase.setOrderNo(orderNo);
        purchase.setDeviceType(request.getDeviceType());
        purchase.setQuantity(1);
        purchase.setReason("设备申领自动采购: " + requestNo);
        purchase.setStatus(1); // 待审批
        purchase.setCreatedAt(LocalDateTime.now());

        purchaseMapper.insert(purchase);

        request.setPurchaseOrderNo(orderNo);
        deviceRequestMapper.updateById(request);

        return orderNo;
    }

    /**
     * 采购到货处理：入库 + 【直接分配】（替代原 DAG 恢复编排）
     */
    @Override
    @Transactional
    public void onPurchaseArrived(String orderNo) {
        // 1. 乐观锁更新采购单状态（status != 4 才更新，防重复到货处理）
        LambdaUpdateWrapper<AdminPurchaseRequest> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(AdminPurchaseRequest::getOrderNo, orderNo)
                .ne(AdminPurchaseRequest::getStatus, 4)
                .set(AdminPurchaseRequest::getStatus, 4)
                .set(AdminPurchaseRequest::getArrivedAt, LocalDateTime.now());

        int updated = purchaseMapper.update(null, updateWrapper);
        if (updated == 0) {
            return;
        }

        AdminPurchaseRequest purchase = getPurchaseByNo(orderNo);

        // 2. 创建设备明细
        for (int i = 0; i < purchase.getQuantity(); i++) {
            AdminDeviceDetail device = new AdminDeviceDetail();
            device.setDeviceType(purchase.getDeviceType());
            device.setSn(serialService.generate("SN"));
            device.setStatus(1);
            device.setLocation("仓库");
            detailMapper.insert(device);
        }

        // 3. 更新库存
        QueryWrapper<AdminDeviceInventory> wrapper = new QueryWrapper<>();
        wrapper.eq("device_type", purchase.getDeviceType());
        AdminDeviceInventory inventory = inventoryMapper.selectOne(wrapper);

        if (inventory == null) {
            inventory = new AdminDeviceInventory();
            inventory.setDeviceType(purchase.getDeviceType());
            inventory.setModel(purchase.getDeviceType());
            inventory.setTotalCount(purchase.getQuantity());
            inventory.setAvailableCount(purchase.getQuantity());
            inventory.setStatus(1);
            inventoryMapper.insert(inventory);
        } else {
            inventory.setTotalCount(inventory.getTotalCount() + purchase.getQuantity());
            inventory.setAvailableCount(inventory.getAvailableCount() + purchase.getQuantity());
            inventory.setUpdatedAt(LocalDateTime.now());
            inventoryMapper.updateById(inventory);
        }

        // 4. 关联申领单：标记有库存，并尝试直接分配（替代原 resumeOnArrived + DAG 编排）
        AdminDeviceRequest request = getByPurchaseOrderNo(orderNo);
        if (request != null) {
            request.setStockStatus(1);
            request.setStatus(3); // 准备中
            deviceRequestMapper.updateById(request);

            try {
                allocateDevice(request.getRequestNo());
                log.info("采购到货后自动分配成功: requestNo={}", request.getRequestNo());
            } catch (Exception e) {
                // 极端情况（如并发分配导致库存不足）：保留"准备中"，管理员可手动处理
                log.error("采购到货后自动分配失败（保留准备中状态）: requestNo={}",
                        request.getRequestNo(), e);
            }
        }
    }

    /**
     * 管理员确认归还入库（从 AdminManageController 下移）
     */
    @Override
    @Transactional
    public void confirmReturn(Long returnId, String remark) {
        AdminDeviceReturn record = returnMapper.selectById(returnId);
        if (record == null) {
            throw new RuntimeException("归还记录不存在");
        }
        if (record.getStatus() != 1) {
            throw new RuntimeException("该归还记录已处理");
        }

        // 1. 设备重新入库（LambdaUpdateWrapper 强制 set null，绕过 MP not_null 策略）
        LambdaUpdateWrapper<AdminDeviceDetail> deviceWrapper = new LambdaUpdateWrapper<>();
        deviceWrapper.eq(AdminDeviceDetail::getId, record.getDeviceId())
                .set(AdminDeviceDetail::getStatus, 1)
                .set(AdminDeviceDetail::getAssignedUserId, null)
                .set(AdminDeviceDetail::getAssignedAt, null);
        detailMapper.update(null, deviceWrapper);

        // 2. 库存 +1（totalCount 不变，归还非新增采购）
        AdminDeviceRequest request = getByRequestNo(record.getRequestNo());
        if (request != null) {
            AdminDeviceInventory inventory = inventoryMapper.selectOne(
                    new QueryWrapper<AdminDeviceInventory>().eq("device_type", request.getDeviceType())
            );
            if (inventory != null) {
                inventory.setAvailableCount(inventory.getAvailableCount() + 1);
                inventoryMapper.updateById(inventory);
            }
        }

        // 3. 更新归还记录
        record.setStatus(2); // 已入库
        record.setConfirmedBy(BaseContext.getCurrentId());
        record.setConfirmedAt(LocalDateTime.now());
        record.setRemark(remark);
        returnMapper.updateById(record);

        // 4. 更新原申领单：清空设备关联
        AdminDeviceRequest req = getByRequestNo(record.getRequestNo());
        if (req != null) {
            LambdaUpdateWrapper<AdminDeviceRequest> reqWrapper = new LambdaUpdateWrapper<>();
            reqWrapper.eq(AdminDeviceRequest::getId, req.getId())
                    .set(AdminDeviceRequest::getStatus, 5)
                    .set(AdminDeviceRequest::getAllocatedDeviceId, null);
            deviceRequestMapper.update(null, reqWrapper);
        }
    }

    /**
     * 完成申领
     */
    @Override
    @Transactional
    public void completeRequest(String requestNo) {
        AdminDeviceRequest request = getByRequestNo(requestNo);
        request.setStatus(5); // 已完成
        request.setCompletedAt(LocalDateTime.now());
        deviceRequestMapper.updateById(request);
    }

    @Override
    public AdminDeviceRequest getByRequestNo(String requestNo) {
        QueryWrapper<AdminDeviceRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("request_no", requestNo);
        return deviceRequestMapper.selectOne(wrapper);
    }

    private AdminPurchaseRequest getPurchaseByNo(String orderNo) {
        QueryWrapper<AdminPurchaseRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("order_no", orderNo);
        return purchaseMapper.selectOne(wrapper);
    }

    @Override
    public AdminDeviceRequest getByPurchaseOrderNo(String purchaseOrderNo) {
        QueryWrapper<AdminDeviceRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("purchase_order_no", purchaseOrderNo);
        return deviceRequestMapper.selectOne(wrapper);
    }
}