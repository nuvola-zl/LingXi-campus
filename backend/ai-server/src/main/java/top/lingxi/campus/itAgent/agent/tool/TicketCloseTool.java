package top.lingxi.campus.itAgent.agent.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.lingxi.campus.itAgent.ticket.user.service.ITicketQueryService;

import java.util.Map;

/**
 * 关单工具（close_ticket）：关闭用户最近的报修单（用户确认问题已解决时）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketCloseTool implements AgentTool {

    private final ITicketQueryService ticketQueryService;

    @Override
    public String name() {
        return "close_ticket";
    }

    @Override
    public String description() {
        return "关闭用户最近的报修单（用户明确表示问题已解决时使用）。无需参数。用户身份由系统自动识别";
    }

    @Override
    public String execute(Map<String, Object> args) {
        try {
            Long userId = Long.valueOf(args.get("userId").toString());
            String reply = ticketQueryService.closeLatestTicket(userId);
            log.info("[TicketCloseTool] 关单成功: userId={}", userId);
            return reply;
        } catch (Exception e) {
            log.error("[TicketCloseTool] 关单失败", e);
            return "关闭报修单失败: " + e.getMessage();
        }
    }
}