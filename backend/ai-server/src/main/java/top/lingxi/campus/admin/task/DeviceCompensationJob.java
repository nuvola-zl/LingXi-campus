package top.lingxi.campus.admin.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.lingxi.campus.domain.admin.eneity.AdminDeviceRequest;
import top.lingxi.campus.domain.admin.mapper.AdminDeviceRequestMapper;

import java.util.List;

/**
 * 设备申领补偿任务（每 10 分钟）
 *
 * 重构变更（DAG 移除）：
 * - 移除"死锁单扫描 + DAG 恢复"逻辑（那是为 DAG 挂起等待擦屁股的，编排已删，死锁不复存在）
 * - 保留失败单资源回收（Saga 补偿思想，与编排无关，幂等）
 */
@Slf4j
@Component
public class DeviceCompensationJob {

    @Autowired
    private AdminDeviceRequestMapper requestMapper;

    @Autowired
    private DeviceCompensationService compensationService;

    @Scheduled(fixedRate = 10 * 60 * 1000)
    public void run() {
        // 回收失败单子的资源：释放已分配设备 + 回滚库存
        List<AdminDeviceRequest> list = requestMapper.selectList(
                new QueryWrapper<AdminDeviceRequest>()
                        .eq("status", 6)              // 处理失败
                        .isNotNull("allocated_device_id")
        );

        if (list.isEmpty()) {
            return;
        }

        log.info("【补偿任务】扫描到 {} 个需要补偿的失败申领单", list.size());
        for (AdminDeviceRequest request : list) {
            try {
                compensationService.compensate(request.getRequestNo());
            } catch (Exception e) {
                log.error("[{}] 补偿执行异常", request.getRequestNo(), e);
            }
        }
    }
}