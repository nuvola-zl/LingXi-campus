package top.lingxi.campus.admin.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.lingxi.campus.domain.admin.eneity.AdminDeviceDetail;
import top.lingxi.campus.domain.admin.eneity.AdminDeviceInventory;
import top.lingxi.campus.domain.admin.eneity.AdminDeviceRequest;
import top.lingxi.campus.domain.admin.mapper.AdminDeviceDetailMapper;
import top.lingxi.campus.domain.admin.mapper.AdminDeviceInventoryMapper;
import top.lingxi.campus.domain.admin.mapper.AdminDeviceRequestMapper;

@Slf4j
@Service
public class DeviceCompensationService {

    @Autowired
    private AdminDeviceRequestMapper requestMapper;

    @Autowired
    private AdminDeviceInventoryMapper inventoryMapper;

    @Autowired
    private AdminDeviceDetailMapper detailMapper;

    /**
     * 补偿失败申领单的资源：释放设备 + 回滚库存
     * 幂等设计：重复执行无副作用
     */
    @Transactional
    public void compensate(String requestNo) {
        AdminDeviceRequest request = requestMapper.selectOne(
                new QueryWrapper<AdminDeviceRequest>().eq("request_no", requestNo)
        );

        if (request == null || request.getStatus() != 6) {
            log.info("[{}] 非失败状态或单号不存在，跳过补偿", requestNo);
            return;
        }

        if (request.getAllocatedDeviceId() == null) {
            log.info("[{}] 未分配设备，无需补偿", requestNo);
            return;
        }

        Long deviceId = request.getAllocatedDeviceId();

        boolean deviceReleased = false;

        // 1. 释放设备（只有 status=2 已分配 才释放，防止重复补偿）
        AdminDeviceDetail device = detailMapper.selectById(deviceId);
        if (device != null && device.getStatus() == 2) {
            LambdaUpdateWrapper<AdminDeviceDetail> deviceWrapper = new LambdaUpdateWrapper<>();
            deviceWrapper.eq(AdminDeviceDetail::getId, deviceId)
                    .set(AdminDeviceDetail::getStatus, 1)
                    .set(AdminDeviceDetail::getAssignedUserId, null)
                    .set(AdminDeviceDetail::getAssignedAt, null);
            detailMapper.update(null, deviceWrapper);
            deviceReleased = true;
            log.info("[{}] 设备 {} 已释放回库存", requestNo, deviceId);
        }

        // 2. 回滚库存（只有真正释放了设备才+1，防止重复补偿导致库存虚增）
        if (deviceReleased) {
            AdminDeviceInventory inventory = inventoryMapper.selectOne(
                    new QueryWrapper<AdminDeviceInventory>().eq("device_type", request.getDeviceType())
            );
            if (inventory != null) {
                inventory.setAvailableCount(inventory.getAvailableCount() + 1);
                inventoryMapper.updateById(inventory);
                log.info("[{}] 库存已回滚，available_count={}", requestNo, inventory.getAvailableCount());
            }
        }

       // 3. 清空申领单的设备分配记录（标记为已补偿，避免下次重复扫描）
        LambdaUpdateWrapper<AdminDeviceRequest> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AdminDeviceRequest::getId, request.getId())
                .set(AdminDeviceRequest::getAllocatedDeviceId, null);

        requestMapper.update(null, wrapper);

        log.info("[{}] 失败申领单资源补偿完成", requestNo);;
    }
}