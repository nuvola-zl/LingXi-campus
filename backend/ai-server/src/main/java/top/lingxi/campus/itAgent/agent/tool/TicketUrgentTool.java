package top.lingxi.campus.itAgent.agent.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.lingxi.campus.itAgent.ticket.user.service.ITicketQueryService;

import java.util.Map;

/**
 * 催单工具（urgent_ticket）：将用户最近的报修单标记加急
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketUrgentTool implements AgentTool {

    private final ITicketQueryService ticketQueryService;

    @Override
    public String name() {
        return "urgent_ticket";
    }

    @Override
    public String description() {
        return "催单/加急用户最近的报修单。无需参数。用户身份由系统自动识别";
    }
    @Override
    public String execute(Map<String, Object> args) {
        try {
            Long userId = Long.valueOf(args.get("userId").toString());
            String reply = ticketQueryService.urgentLatestTicket(userId);
            log.info("[TicketUrgentTool] 催单成功: userId={}", userId);
            return reply;
        } catch (Exception e) {
            log.error("[TicketUrgentTool] 催单失败", e);
            return "催单失败: " + e.getMessage();
        }
    }
}