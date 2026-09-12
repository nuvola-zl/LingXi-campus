package top.lingxi.campus.tool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.lingxi.campus.domain.admin.eneity.AdminDeviceRequest;
import top.lingxi.campus.domain.admin.mapper.AdminDeviceRequestMapper;
import top.lingxi.campus.domain.biz.ticket.entity.BizTicket;
import top.lingxi.campus.domain.biz.ticket.mapper.BizTicketMapper;
import top.lingxi.campus.domain.hr.eneity.HrLeaveRequest;
import top.lingxi.campus.domain.hr.mapper.HrLeaveRequestMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 跨域提交查重组件（防重复提交，幂等治理）
 *
 * 主题变更（灵犀校园）：文案改为校园语义，查重逻辑不变
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DuplicateChecker {

    private final AdminDeviceRequestMapper deviceRequestMapper;
    private final HrLeaveRequestMapper leaveMapper;
    private final BizTicketMapper ticketMapper;

    // ==================== 器材借用 ====================

    /**
     * 检查是否有重复的器材借用
     * @return 重复提示语，null 表示无重复
     */
    public String checkDuplicateDeviceRequest(Long userId, String deviceType) {
        QueryWrapper<AdminDeviceRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .eq("device_type", deviceType)
                .in("status", List.of(2, 3, 4)); // 购置中/准备中/待领取 = 未完成

        AdminDeviceRequest existing = deviceRequestMapper.selectOne(
                wrapper.orderByDesc("created_at").last("LIMIT 1")
        );

        if (existing != null) {
            String statusText = switch (existing.getStatus()) {
                case 2 -> "购置中";
                case 3 -> "准备中";
                case 4 -> "待领取";
                default -> "处理中";
            };
            return String.format(
                    "您已有一个 %s 借用单（%s）正在处理中，当前状态：%s，请勿重复申请。",
                    deviceType, existing.getRequestNo(), statusText
            );
        }
        return null;
    }

    // ==================== 请假 ====================

    /**
     * 检查同一时间段是否已有重叠请假
     * @return 重复提示语，null 表示无重复
     */
    public String checkDuplicateLeave(Long userId, LocalDate startDate, LocalDate endDate) {
        QueryWrapper<HrLeaveRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .in("status", List.of(1, 2)) //只检查"待审批"和"已通过"，已拒绝(status=3)允许重新申请
                .and(w -> w.le("start_date", endDate).ge("end_date", startDate));

        List<HrLeaveRequest> list = leaveMapper.selectList(wrapper);
        if (!list.isEmpty()) {
            HrLeaveRequest first = list.get(0);
            return String.format(
                    "您在 %s 至 %s 已有请假申请（单号：%s），请勿重复提交。如需修改请先取消原申请。",
                    first.getStartDate(), first.getEndDate(), first.getRequestNo()
            );
        }
        return null;
    }

    // ==================== 报修工单 ====================

    /**
     * 检查近期是否有相似未关闭报修单
     * @return 重复提示语，null 表示无重复
     */
    public String checkDuplicateTicket(Long userId, String title, String description) {
        // 查该用户 24 小时内未关闭的报修单
        QueryWrapper<BizTicket> wrapper = new QueryWrapper<>();
        wrapper.eq("requester_id", userId)
                .ne("status", 4)
                .ge("created_at", LocalDateTime.now().minusHours(24))
                .orderByDesc("created_at");

        List<BizTicket> recent = ticketMapper.selectList(wrapper);
        for (BizTicket existing : recent) {
            if (isDuplicateTitle(existing.getTitle(), title)) {
                return String.format(
                        "您近期已提交过类似问题的报修单（%s：%s），后勤老师正在处理中，请勿重复创建。如需催单请说\"催一下我的报修单\"。",
                        existing.getTicketNo(), existing.getTitle()
                );
            }
        }
        return null;
    }

    /** 标题重复判定：精确相等，或一方包含另一方（短标题 ≥4 字防误判） */
    private boolean isDuplicateTitle(String existing, String candidate) {
        if (existing == null || candidate == null) return false;
        String a = existing.trim();
        String b = candidate.trim();
        if (a.equals(b)) return true;
        if (a.length() >= 4 && b.contains(a)) return true;
        if (b.length() >= 4 && a.contains(b)) return true;
        return false;
    }
}