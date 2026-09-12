package top.lingxi.campus.itAgent.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import top.lingxi.campus.itAgent.context.ChatContext;
import top.lingxi.campus.itAgent.ticket.user.service.ITicketQueryService;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketActionHandler implements IntentHandler {

    private final ITicketQueryService ticketQueryService;

    @Override
    public List<String> getDomains() {
        return List.of("IT");
    }

    @Override
    public int getOrder() {
        return 2;
    }

    @Override
    public boolean supports(ChatContext ctx) {
        String intent = ctx.getIntent() != null ? ctx.getIntent().getIntent() : null;
        return "ticket_query".equals(intent)
                || "ticket_urgent".equals(intent)
                || "ticket_close".equals(intent);
    }

    @Override
    public Flux<String> handle(ChatContext ctx) {
        Long userId = ctx.getUserId();
        String intent = ctx.getIntent().getIntent();
        String extractedTicketNo = ctx.getIntent().getExtractedTicketNo();

        String reply;
        switch (intent) {
            case "ticket_query" -> {
                if (extractedTicketNo != null) {
                    reply = ticketQueryService.buildReplyByTicketNo(userId, extractedTicketNo);
                } else {
                    reply = ticketQueryService.buildReply(userId);
                }
            }
            case "ticket_urgent" -> reply = ticketQueryService.urgentLatestTicket(userId);
            case "ticket_close" -> reply = ticketQueryService.closeLatestTicket(userId);
            default -> reply = "未知工单操作。";
        }

        ctx.setResponseContent(reply);
        return Flux.just(reply);
    }
}