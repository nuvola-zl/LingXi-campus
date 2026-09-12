package top.lingxi.campus.admin.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import top.lingxi.campus.domain.admin.eneity.AdminDeviceDetail;
import top.lingxi.campus.domain.admin.eneity.AdminDeviceInventory;
import top.lingxi.campus.domain.admin.eneity.AdminDeviceRequest;
import top.lingxi.campus.domain.admin.mapper.AdminDeviceDetailMapper;
import top.lingxi.campus.domain.admin.mapper.AdminDeviceInventoryMapper;
import top.lingxi.campus.domain.admin.mapper.AdminDeviceRequestMapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备超时回收任务（每天凌晨 2 点）
 *
 * 重构变更：修复 inventory 为 null 时的 NPE（新设备类型首次回收时库存记录可能不存在）
 */
@Component
@Slf4j
public class DeviceTimeoutJob {

    @Autowired
    private AdminDeviceRequestMapper requestMapper;

    @Autowired
    private AdminDeviceDetailMapper detailMapper;

    @Autowired
    private AdminDeviceInventoryMapper inventoryMapper;

    /**
     * 每天凌晨 2 点执行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void autoRecycle() {
        // 扫描待领取超过 7 天的
        LocalDateTime deadline = LocalDateTime.now().minusDays(7);

        List<AdminDeviceRequest> list = requestMapper.selectList(
                new QueryWrapper<AdminDeviceRequest>()
                        .eq("status", 4) // 待领取
                        .lt("created_at", deadline)
        );

        for (AdminDeviceRequest request : list) {
            log.info("自动回收超时未领设备: {}", request.getRequestNo());

            // 1. 回滚设备
            if (request.getAllocatedDeviceId() != null) {
                LambdaUpdateWrapper<AdminDeviceDetail> deviceWrapper = new LambdaUpdateWrapper<>();
                deviceWrapper.eq(AdminDeviceDetail::getId, request.getAllocatedDeviceId())
                        .set(AdminDeviceDetail::getStatus, 1)
                        .set(AdminDeviceDetail::getAssignedUserId, null);
                detailMapper.update(null, deviceWrapper);
            }

            // 2. 回滚库存（修复：库存记录可能不存在，需判空）
            AdminDeviceInventory inv = inventoryMapper.selectOne(
                    new QueryWrapper<AdminDeviceInventory>().eq("device_type", request.getDeviceType())
            );
            if (inv != null) {
                inv.setAvailableCount(inv.getAvailableCount() + 1);
                inventoryMapper.updateById(inv);
            } else {
                log.warn("回收时未找到库存记录，跳过库存回滚: deviceType={}", request.getDeviceType());
            }

            // 3. 取消申领单
            request.setStatus(6); // 已取消
            requestMapper.updateById(request);

            // 可选：发通知给用户"您的申领单因超时未领取已自动取消"
        }
    }
}