package top.lingxi.campus.admin.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import top.lingxi.campus.admin.user.service.DeviceRequestService;
import top.lingxi.campus.common.context.BaseContext;
import top.lingxi.campus.common.exception.BusinessException;
import top.lingxi.campus.common.exception.ErrorCode;
import top.lingxi.campus.common.result.Result;
import top.lingxi.campus.domain.admin.eneity.*;
import top.lingxi.campus.domain.admin.mapper.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
public class DeviceUserController {

    private final DeviceRequestService deviceRequestService;

    private final AdminDeviceRequestMapper requestMapper;

    private final AdminDeviceInventoryMapper inventoryMapper;

    private final AdminDeviceDetailMapper detailMapper;

    private final AdminPurchaseRequestMapper purchaseMapper;

    private final AdminDeviceReturnMapper returnMapper;


    /**
     * 获取当前用户的所有申领记录
     */
    @GetMapping("/my-requests")
    public Result<List<AdminDeviceRequest>> myRequests() {
        Long currentUserId = BaseContext.getCurrentId();
        QueryWrapper<AdminDeviceRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", currentUserId)
                .orderByDesc("created_at");
        return Result.success(requestMapper.selectList(wrapper));
    }

    /**
     * 用户确认已领取设备（扫码或点击确认）
     */
    @PostMapping("/{requestNo}/confirmReceive")
    public Result<Void> confirmReceive(@PathVariable String requestNo) {
        // 1. 校验：只能本人或管理员操作
        Long currentUserId = BaseContext.getCurrentId();
        AdminDeviceRequest request = deviceRequestService.getByRequestNo(requestNo);

        if (request == null) throw new BusinessException(ErrorCode.DEVICE_REQUEST_NOT_FOUND);
        if (!request.getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.DEVICE_REQUEST_NO_PERMISSION);
        }
        if (request.getStatus() != 4) {
            throw new BusinessException(ErrorCode.DEVICE_REQUEST_STATUS_INVALID);
        }

        // 2. 完成申领
        deviceRequestService.completeRequest(requestNo);
        return Result.success();
    }

    /**
     * 用户取消申领
     */
    @PostMapping("/{requestNo}/cancel")
    public Result<Void> cancelRequest(@PathVariable String requestNo) {
        if (!StringUtils.hasText(requestNo)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        Long currentUserId = BaseContext.getCurrentId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        AdminDeviceRequest request = deviceRequestService.getByRequestNo(requestNo);
        if (request == null) {
            throw new BusinessException(ErrorCode.DEVICE_REQUEST_NOT_FOUND);
        }

        if (!Objects.equals(request.getUserId(), currentUserId)) {
            throw new BusinessException(ErrorCode.DEVICE_REQUEST_NO_PERMISSION);
        }


        // 根据状态走不同取消逻辑
        switch (request.getStatus()) {
            case 2: // 采购中
                cancelPurchase(request);
                break;
            case 3: // 准备中（DAG 正在跑）
                throw new BusinessException(ErrorCode.DEVICE_REQUEST_STATUS_INVALID);
                           case 4: // 待领取
                cancelAllocated(request);
                break;
            case 5: // 已完成
                throw new BusinessException(ErrorCode.DEVICE_REQUEST_STATUS_INVALID);
            default:
                throw new BusinessException(ErrorCode.DEVICE_REQUEST_STATUS_INVALID);
        }

        return Result.success();
    }

    /**
     * 取消采购中的单子
     */
    @Transactional
    public void cancelPurchase(AdminDeviceRequest request) {
        // 1. 改申领单状态
        request.setStatus(6); // 6=已取消
        requestMapper.updateById(request);

        // 2. 取消关联采购单（如果还在待审批/已批准状态）
        if (request.getPurchaseOrderNo() != null) {
            AdminPurchaseRequest purchase = purchaseMapper.selectOne(
                    new QueryWrapper<AdminPurchaseRequest>()
                            .eq("order_no", request.getPurchaseOrderNo())
            );
            if (purchase != null && purchase.getStatus() <= 2) { // 1待审批 2已批准
                purchase.setStatus(5); // 5=已取消
                purchaseMapper.updateById(purchase);
            }
            // 如果采购单 status=3(采购中) 或 4(已到货)，则不能取消采购，只能标记申领单取消
        }
    }

    /**
     * 取消待领取的单子（设备已分配但未领走）
     */
    @Transactional
    public void cancelAllocated(AdminDeviceRequest request) {
        // 1. 回滚设备（LambdaUpdateWrapper 强制 set null，绕过 MP update-strategy: not_null）
        if (request.getAllocatedDeviceId() != null) {
            LambdaUpdateWrapper<AdminDeviceDetail> deviceWrapper = new LambdaUpdateWrapper<>();
            deviceWrapper.eq(AdminDeviceDetail::getId, request.getAllocatedDeviceId())
                    .set(AdminDeviceDetail::getStatus, 1)
                    .set(AdminDeviceDetail::getAssignedUserId, null)
                    .set(AdminDeviceDetail::getAssignedAt, null);
            detailMapper.update(null, deviceWrapper);
        }

        // 2. 回滚库存
        AdminDeviceInventory inventory = inventoryMapper.selectOne(
                new QueryWrapper<AdminDeviceInventory>().eq("device_type", request.getDeviceType())
        );
        if (inventory != null) {
            inventory.setAvailableCount(inventory.getAvailableCount() + 1);
            inventoryMapper.updateById(inventory);
        }

        // 3. 改申领单状态，同时清空 allocatedDeviceId 防止补偿任务重复释放
        LambdaUpdateWrapper<AdminDeviceRequest> requestWrapper = new LambdaUpdateWrapper<>();
        requestWrapper.eq(AdminDeviceRequest::getId, request.getId())
                .set(AdminDeviceRequest::getStatus, 6)
                .set(AdminDeviceRequest::getAllocatedDeviceId, null);
        requestMapper.update(null, requestWrapper);
    }

    @PostMapping("/{requestNo}/return")
    public Result<String> returnDevice(
            @PathVariable String requestNo,
            @RequestParam String reason) {

        Long currentUserId = BaseContext.getCurrentId();
        AdminDeviceRequest request = deviceRequestService.getByRequestNo(requestNo);

        if (request == null) throw new BusinessException(ErrorCode.DEVICE_REQUEST_NOT_FOUND);
        if (!java.util.Objects.equals(request.getUserId(), currentUserId)) throw new BusinessException(ErrorCode.DEVICE_REQUEST_NO_PERMISSION);
        if (request.getStatus() != 5) throw new BusinessException(ErrorCode.DEVICE_REQUEST_STATUS_INVALID);

        // 创建设备归还记录
        AdminDeviceReturn returnRecord = new AdminDeviceReturn();
        returnRecord.setRequestNo(requestNo);
        returnRecord.setUserId(currentUserId);
        returnRecord.setDeviceId(request.getAllocatedDeviceId());
        returnRecord.setReturnReason(reason);
        returnRecord.setStatus(1); // 1=待管理员确认
        returnMapper.insert(returnRecord);

        // 把申领单状态改成"归还中"
        request.setStatus(7);
        requestMapper.updateById(request);

        return Result.success("归还申请已提交，等待管理员确认入库");
    }


}