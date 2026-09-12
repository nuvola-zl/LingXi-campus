package top.lingxi.campus.itAgent.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import top.lingxi.campus.itAgent.agent.core.AgentDecision;
import top.lingxi.campus.itAgent.agent.core.AgentEvent;
import top.lingxi.campus.itAgent.agent.core.AgentOrchestrator;
import top.lingxi.campus.itAgent.agent.core.DialogContext;
import top.lingxi.campus.itAgent.agent.core.DialogState;
import top.lingxi.campus.itAgent.state.TicketCreateState;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollectingState implements DialogState {

    private final AgentOrchestrator orchestrator;

    @Override
    public String name() {
        return "COLLECTING";
    }

    @Override
    public Flux<AgentEvent> handle(DialogContext ctx, String userMessage) {
        TicketCreateState state = ctx.getState();
        state.setStatus("COLLECTING");

        // 1. 记录历史
        state.addHistory("user", userMessage);

        // 2. 核心：让 Orchestrator 判断用户意图和输入质量
        return orchestrator.decide(ctx, userMessage, "COLLECTING")
                .flatMapMany(decision -> {
                    log.info("[CollectingState] 执行决策: action={}, target={}, reasoning={}",
                            decision.action(), decision.targetState(), decision.reasoning());
                    return executeDecision(ctx, userMessage, decision);
                })
                .onErrorResume(e -> {
                    log.error("[CollectingState] Orchestrator 异常", e);
                    return Flux.just(AgentEvent.respond("请详细描述一下问题的具体现象，方便我帮您创建工单。"));
                });
    }

    // ==================== 决策执行器 ====================

    private Flux<AgentEvent> executeDecision(DialogContext ctx, String userMessage, AgentDecision d) {
        // COMPLETE：取消或跑题，结束对话
        if (d.isComplete()) {
            String content = d.content() != null ? d.content() : "好的，已为您取消。如果还有其他问题，随时告诉我。";
            ctx.getState().addHistory("ai", content);
            return Flux.just(AgentEvent.respond(content), AgentEvent.complete());
        }

        // RESPOND：描述不够或需要追问，保持在 COLLECTING
        if (d.isRespond()) {
            String content = d.content() != null ? d.content()
                    : "为了能准确帮您创建工单，能否描述一下具体现象？比如错误提示、发生时间、影响范围等。";
            ctx.getState().addHistory("ai", content);
            return Flux.just(AgentEvent.respond(content));
        }

        // TRANSITION→CONFIRMING：描述足够，准备建单
        if (d.isTransition() && "CONFIRMING".equals(d.targetState())) {
            return handleTransitionToConfirming(ctx, userMessage, d);
        }

        // 其他目标状态（理论上不应出现，但兜底）
        if (d.isTransition()) {
            String content = d.content() != null ? d.content() : "好的，请继续。";
            ctx.getState().addHistory("ai", content);
            return Flux.just(AgentEvent.respond(content), AgentEvent.transition(d.targetState()));
        }

        // 兜底
        return Flux.just(AgentEvent.respond("请详细描述一下问题的具体现象。"));
    }

    // ==================== 业务逻辑：准备确认数据 ====================

    private Flux<AgentEvent> handleTransitionToConfirming(DialogContext ctx, String userMessage, AgentDecision d) {
        TicketCreateState state = ctx.getState();

        // 拼接完整描述（含原始问题、建议、历史）
        String enrichedDesc = buildEnrichedDescription(state, userMessage);
        state.setDescription(enrichedDesc);

        // 智能提取标题
        String title = extractTitle(userMessage);
        state.setTitle(title);
        // 在 handleTransitionToConfirming 里替换 state.setPriority(2);
        int priority = isUrgent(userMessage, state.getOriginalMessage()) ? 1 : 2;
        state.setPriority(priority);

        // 确认文案：优先用 Orchestrator 生成的，否则自己拼接
        String confirmText = d.content() != null && !d.content().isBlank()
                ? d.content()
                : String.format(
                "请确认以下报修单信息：\n【标题】%s\n【描述】%s\n【优先级】普通\n\n回复\"确认\"创建报修单，回复\"取消\"放弃。",
                state.getTitle(), userMessage.trim());

        state.addHistory("ai", confirmText);

        return Flux.just(
                AgentEvent.respond(confirmText),
                AgentEvent.transition("CONFIRMING")
        );
    }

    // ==================== 原有业务方法（不变）====================

    private String buildEnrichedDescription(TicketCreateState state, String userDesc) {
        StringBuilder sb = new StringBuilder();

        if (state.getOriginalMessage() != null && !state.getOriginalMessage().isBlank()) {
            sb.append("【原始问题】\n").append(state.getOriginalMessage().trim()).append("\n");
        }

        if (state.getSuggestionContent() != null && !state.getSuggestionContent().isBlank()) {
            sb.append("\n【AI 建议方案】\n").append(state.getSuggestionContent().trim()).append("\n");
        }

        if (state.getConversationHistory() != null && !state.getConversationHistory().isEmpty()) {
            sb.append("\n【对话过程】\n");
            for (TicketCreateState.ChatTurn turn : state.getConversationHistory()) {
                String roleLabel = "ai".equals(turn.getRole()) ? "AI" : "用户";
                sb.append(roleLabel).append(": ").append(turn.getContent()).append("\n");
            }
        }

        sb.append("\n【用户补充描述】\n").append(userDesc.trim());
        return sb.toString().trim();
    }

    private String extractTitle(String desc) {
        String cleaned = desc.replaceAll("[\\n\\r]", " ").trim();
        if (cleaned.length() <= 15) return cleaned;

        int firstPunct = cleaned.indexOf('，');
        if (firstPunct == -1) firstPunct = cleaned.indexOf('。');
        if (firstPunct == -1) firstPunct = cleaned.indexOf('？');
        if (firstPunct == -1) firstPunct = cleaned.indexOf('、');
        if (firstPunct > 3 && firstPunct <= 15) {
            return cleaned.substring(0, firstPunct);
        }
        return cleaned.substring(0, 15) + "...";
    }

    /** 紧急度判定：关键词命中即紧急（确定性规则，不依赖 LLM 输出） */
    private boolean isUrgent(String... texts) {
        String[] urgentKeywords = {"紧急", "急", "很急", "着急", "马上", "立刻", "立即",
                "严重", "漏水", "断电", "着火", "冒烟", "危险"};
        for (String text : texts) {
            if (text == null) continue;
            for (String kw : urgentKeywords) {
                if (text.contains(kw)) return true;
            }
        }
        return false;
    }
}