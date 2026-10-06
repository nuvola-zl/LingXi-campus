package top.lingxi.campus.itAgent.agent.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.lingxi.campus.itAgent.ticket.user.service.ITicketQueryService;

import java.util.Map;

/**
 * 查询报修单进度工具（query_ticket）
 * v3：查单从"流程外 Handler"变为"Agent 工具"，支持建单中途查询且不打断流程
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketQueryTool implements AgentTool {

    private final ITicketQueryService ticketQueryService;

    @Override
    public String name() {
        return "query_ticket";
    }

    @Override
    public String description() {
        return "查询报修单处理进度。参数: ticketNo(单号,可选,不传则查最近一张)。用户身份由系统自动识别，无需提供";
    }
    @Override
    public String execute(Map<String, Object> args) {
        try {
            Long userId = Long.valueOf(args.get("userId").toString());
            String ticketNo = args.get("ticketNo") != null
                    ? args.get("ticketNo").toString() : null;

            String reply = ticketNo != null
                    ? ticketQueryService.buildReplyByTicketNo(userId, ticketNo)
                    : ticketQueryService.buildReply(userId);

            log.info("[TicketQueryTool] 查询成功: userId={}, ticketNo={}", userId, ticketNo);
            return reply;
        } catch (Exception e) {
            log.error("[TicketQueryTool] 查询失败", e);
            return "查询报修单失败: " + e.getMessage();
        }
    }
}