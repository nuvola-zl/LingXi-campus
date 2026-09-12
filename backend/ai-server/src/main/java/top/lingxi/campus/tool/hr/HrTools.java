package top.lingxi.campus.tool.hr;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import top.lingxi.campus.common.context.BaseContext;
import top.lingxi.campus.domain.hr.eneity.HrLeaveRequest;
import top.lingxi.campus.domain.hr.eneity.HrPunchRecord;
import top.lingxi.campus.hr.service.LeaveService;
import top.lingxi.campus.hr.service.PunchService;
import top.lingxi.campus.tool.DuplicateChecker;
import top.lingxi.campus.tool.ToolCallGateway;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 教务域工具：学生请销假 / 课堂补签
 *
 * 主题变更（灵犀校园）：HR 人事场景 → 教务场景。
 * 注意：参数值（annual/sick/personal、miss_punch/late）与审批规则不变，仅显示文案变更。
 *
 * 身份安全修复：用户 ID 不再作为模型可见参数，统一通过 ToolContext 从调用链注入
 * （ChatServiceImpl 发起调用时传入，线程无关）；BaseContext 仅作兜底。
 * 背景：流式 LLM 的工具回调发生在框架流处理线程，ThreadLocal 不传递，
 * 曾导致 userId 为 null 的回归。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class HrTools {

    private final LeaveService leaveService;
    private final PunchService punchService;
    private final DuplicateChecker duplicateChecker;
    private final ToolCallGateway toolGateway;

    @Tool(description = "提交学生请假申请。type支持：annual(长假/离校)、sick(病假)、personal(事假)")
    public String applyLeave(
            ToolContext toolContext,
            @ToolParam(description = "请假类型：annual长假/sick病假/personal事假") String type,
            @ToolParam(description = "开始日期，格式：yyyy-MM-dd") String startDate,
            @ToolParam(description = "结束日期，格式：yyyy-MM-dd") String endDate,
            @ToolParam(description = "请假天数，支持0.5") BigDecimal days,
            @ToolParam(description = "请假原因") String reason) {

        Long realUserId = resolveUserId(toolContext);
        Map<String, Object> args = Map.of(
                "type", type, "startDate", startDate, "endDate", endDate, "days", days);
        return toolGateway.execute("applyLeave", args,
                () -> doApplyLeave(realUserId, type, startDate, endDate, days, reason));
    }

    private String doApplyLeave(Long userId, String type, String startDate,
                                String endDate, BigDecimal days, String reason) {
        try {
            HrLeaveRequest request = leaveService.submitLeave(
                    userId, type,
                    LocalDate.parse(startDate),
                    LocalDate.parse(endDate),
                    days, reason
            );

            String status = request.getApproveType() == 1 ? "AI自动审批通过" : "等待辅导员审批";
            return String.format("请假申请 %s 已提交，%s", request.getRequestNo(), status);

        } catch (Exception e) {
            log.warn("请假申请被拦截或失败: {}", e.getMessage());
            return "提交失败：" + e.getMessage();
        }
    }

    @Tool(description = "提交课堂补签申请（漏签/迟到）")
    public String applyPunch(
            ToolContext toolContext,
            @ToolParam(description = "补签类型：miss_punch(漏签)/late(迟到)") String punchType,
            @ToolParam(description = "补签日期，格式：yyyy-MM-dd") String punchDate,
            @ToolParam(description = "原因说明") String reason) {

        Long realUserId = resolveUserId(toolContext);
        return toolGateway.execute("applyPunch",
                Map.of("punchType", punchType, "punchDate", punchDate),
                () -> doApplyPunch(realUserId, punchType, punchDate, reason));
    }

    private String doApplyPunch(Long userId, String punchType, String punchDate, String reason) {
        try {
            HrPunchRecord record = punchService.submitPunch(
                    userId, punchType, LocalDate.parse(punchDate), reason
            );

            String status = record.getIsManualReview() == 1
                    ? "等待老师审核"
                    : "AI自动通过，补签成功";
            return String.format("补签申请 %s 已提交，%s", record.getPunchNo(), status);

        } catch (Exception e) {
            log.warn("补签申请被拦截或失败: {}", e.getMessage());
            return "提交失败：" + e.getMessage();
        }
    }

    @Tool(description = "查询学生的请假记录列表")
    public String listLeaveRecords(ToolContext toolContext) {

        Long realUserId = resolveUserId(toolContext);
        return toolGateway.execute("listLeaveRecords", Map.of(),
                () -> doListLeaveRecords(realUserId));
    }

    private String doListLeaveRecords(Long userId) {
        List<HrLeaveRequest> list = leaveService.listByUser(userId);
        if (list.isEmpty()) return "暂无请假记录";

        String header = "您的请假记录如下（共 " + list.size() + " 条）：\n";
        return header + list.stream()
                .map(r -> String.format("[%s] %s %s~%s %s天 %s",
                        r.getRequestNo(),
                        typeName(r.getType()),
                        r.getStartDate(),
                        r.getEndDate(),
                        r.getDays(),
                        statusName(r.getStatus())))
                .collect(Collectors.joining("\n"));
    }

    @Tool(description = "查询学生的课堂补签记录列表")
    public String listPunchRecords(ToolContext toolContext) {

        Long realUserId = resolveUserId(toolContext);
        return toolGateway.execute("listPunchRecords", Map.of(),
                () -> doListPunchRecords(realUserId));
    }

    private String doListPunchRecords(Long userId) {
        List<HrPunchRecord> list = punchService.listByUser(userId);
        if (list.isEmpty()) return "暂无补签记录";

        return list.stream()
                .map(r -> String.format("[%s] %s %s %s",
                        r.getPunchNo(),
                        "miss_punch".equals(r.getPunchType()) ? "漏签" : "迟到",
                        r.getPunchDate(),
                        statusName(r.getStatus())))
                .collect(Collectors.joining("\n"));
    }

    @Tool(description = """
        取消指定单号的请假申请。
        仅支持取消状态为"待审批"的请假单，已通过的无法取消。
        参数 requestNo 是完整的请假单号（如 LEAVE-20260804-001）。
        如果用户说"取消刚才的"但没给单号，先调用 listLeaveRecords 列出待审批记录让用户确认。
        """)
    public String cancelLeave(
            ToolContext toolContext,
            @ToolParam(description = "请假单号") String requestNo) {

        Long realUserId = resolveUserId(toolContext);
        return toolGateway.execute("cancelLeave", Map.of("requestNo", requestNo),
                () -> doCancelLeave(realUserId, requestNo));
    }

    private String doCancelLeave(Long userId, String requestNo) {
        try {
            leaveService.cancelLeave(userId, requestNo);
            return String.format("请假申请 %s 已成功取消。", requestNo);
        } catch (Exception e) {
            log.warn("取消请假失败: {}", e.getMessage());
            return "取消失败：" + e.getMessage();
        }
    }

    /**
     * 从工具调用上下文解析用户 ID。
     * 优先 ToolContext（ChatServiceImpl 在发起调用时注入，随请求传递、线程无关）；
     * BaseContext 仅作兜底（流式工具回调线程上 ThreadLocal 可能丢失）。
     */
    private Long resolveUserId(ToolContext toolContext) {
        if (toolContext != null && toolContext.getContext() != null) {
            Object uid = toolContext.getContext().get("userId");
            if (uid instanceof Long l) return l;
            if (uid instanceof Number n) return n.longValue();
        }
        return BaseContext.getCurrentId();
    }

    private String typeName(String type) {
        return switch (type) {
            case "annual" -> "长假";
            case "sick" -> "病假";
            case "personal" -> "事假";
            default -> type;
        };
    }

    private String statusName(Integer status) {
        return switch (status) {
            case 1 -> "待审批";
            case 2 -> "已通过";
            case 3 -> "已拒绝";
            case 4 -> "已取消";
            default -> "未知";
        };
    }
}