package top.lingxi.campus.itAgent.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import top.lingxi.campus.itAgent.agent.service.AgentDialogService;
import top.lingxi.campus.itAgent.context.ChatContext;
import top.lingxi.campus.itAgent.state.TicketCreateState;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketCreateHandler implements IntentHandler {

    private final AgentDialogService agentDialogService;

    @Override
    public List<String> getDomains() {
        return List.of("IT");
    }

    @Override
    public int getOrder() {
        return 1;
    }

    @Override
    public boolean supports(ChatContext ctx) {
        Long userId = ctx.getUserId();
        if (userId == null) return false;

        String intent = ctx.getIntent() != null ? ctx.getIntent().getIntent() : null;

        // 如果有进行中的 Agent 对话，优先拦截（除非用户想查/催/关工单）
        if (agentDialogService.hasOngoingDialog(userId)) {
            // ticket_query / ticket_urgent / ticket_close 放行给 TicketActionHandler
            if ("ticket_query".equals(intent)
                    || "ticket_urgent".equals(intent)
                    || "ticket_close".equals(intent)) {
                return false;
            }

            TicketCreateState state = agentDialogService.getState(userId);
            if (state != null) {
                Long stateSessionId = state.getSessionId();
                Long currentSessionId = ctx.getFinalSessionId();
                if (stateSessionId == null || stateSessionId.equals(currentSessionId)) {
                    return true;
                }
                log.warn("Agent脏状态清理: userId={}", userId);
                agentDialogService.clearState(userId);
            }
        }

        // ticket_abandon 只有在确实有进行中的 Agent 对话时才拦截，
        // 否则让 DefaultChatHandler 当普通聊天处理（避免无对话时说"已取消"的突兀回复）
        if ("ticket_abandon".equals(intent)) {
            return agentDialogService.hasOngoingDialog(userId);
        }

        return "ticket_create".equals(intent);
    }

    @Override
    public Flux<String> handle(ChatContext ctx) {
        Long userId = ctx.getUserId();
        String prompt = ctx.getPrompt();
        Long sessionId = ctx.getFinalSessionId();
        String intent = ctx.getIntent() != null ? ctx.getIntent().getIntent() : null;

        // 放弃：只有确实存在进行中的 Agent 对话时才执行取消
        if ("ticket_abandon".equals(intent)) {
            if (!agentDialogService.hasOngoingDialog(userId)) {
                return Flux.empty(); // 防御：supports() 已过滤，不应走到这里
            }
            agentDialogService.clearState(userId);
            log.info("Agent对话已取消: userId={}, sessionId={}", userId, sessionId);
            return Flux.just("好的，已取消。如果还有其他问题，随时告诉我。");
        }

        // 进行中的对话：继续 Agent 流程
        if (agentDialogService.hasOngoingDialog(userId)) {
            return agentDialogService.continueDialog(userId, prompt, sessionId);
        }

        // 全新对话：启动 Agent
        return agentDialogService.startDialog(userId, prompt, sessionId);
    }
}
