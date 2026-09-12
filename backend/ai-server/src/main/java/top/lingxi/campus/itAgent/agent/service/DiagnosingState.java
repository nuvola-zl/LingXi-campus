package top.lingxi.campus.itAgent.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import top.lingxi.campus.itAgent.agent.core.AgentDecision;
import top.lingxi.campus.itAgent.agent.core.AgentEvent;
import top.lingxi.campus.itAgent.agent.core.AgentOrchestrator;
import top.lingxi.campus.itAgent.agent.core.DialogContext;
import top.lingxi.campus.itAgent.agent.core.DialogState;
import top.lingxi.campus.itAgent.agent.tool.ToolRegistry;


import java.util.Map;

@Slf4j
@Component
public class DiagnosingState implements DialogState {

    private final AgentOrchestrator orchestrator;


    private final ToolRegistry toolRegistry;

    public DiagnosingState(AgentOrchestrator orchestrator, ToolRegistry toolRegistry) {
        this.orchestrator = orchestrator;
        this.toolRegistry = toolRegistry;
    }

    @Override
    public String name() {
        return "DIAGNOSING";
    }

    /**
     * 改造后：只负责执行 Orchestrator 的决策，不做任何意图判断
     */
    @Override
    public Flux<AgentEvent> handle(DialogContext ctx, String userMessage) {
        // 1. 记录历史（只有真实用户消息才记录，工具回调轮次不重复记）
        if (ctx.getLastToolResult() == null) {
            ctx.getState().addHistory("user", userMessage);
        }

        // 2. 极简兜底：空输入直接反问（不经过 LLM，省 token）
        if (userMessage == null || userMessage.isBlank()) {
            return Flux.just(AgentEvent.respond("请描述一下您遇到的问题，我会尽力帮您解决。"));
        }

        // 3. 核心：调用 Orchestrator 做决策，代码只负责执行
        return orchestrator.decide(ctx, userMessage, "DIAGNOSING")
                .flatMapMany(decision -> {
                    log.info("[DiagnosingState] 执行决策: action={}, target={}, reasoning={}",
                            decision.action(), decision.targetState(), decision.reasoning());
                    return executeDecision(ctx, decision);
                })
                .onErrorResume(e -> {
                    log.error("[DiagnosingState] Orchestrator 异常，降级建单", e);
                    return fallbackToCollecting(ctx);
                });
    }

    // ==================== 决策执行器（纯执行，无判断逻辑）====================

    private Flux<AgentEvent> executeDecision(DialogContext ctx, AgentDecision d) {
        // RESPOND：直接回复，保持在 DIAGNOSING（等待用户下一轮反馈）
        if (d.isRespond()) {
            String content = d.content() != null ? d.content() : "请问还有其他问题吗？";
            ctx.getState().addHistory("ai", content);

            // 关键：如果本轮有 Observation（知识库结果），把建议持久化到 state
            // 这样用户下一条消息进来时，即使 lastToolResult 丢失，也能从 suggestionContent 看到之前的建议
            if (ctx.getLastToolResult() != null) {
                ctx.getState().setSuggestionContent(
                        content + "\n\n【知识库参考】\n" + ctx.getLastToolResult()
                );
            } else {
                ctx.getState().setSuggestionContent(content);
            }

            return Flux.just(AgentEvent.respond(content));
        }

        // TRANSITION：状态流转（如 DIAGNOSING → COLLECTING）
        if (d.isTransition()) {
            String content = d.content() != null ? d.content() : "好的，请继续。";
            ctx.getState().addHistory("ai", content);
            return Flux.just(
                    AgentEvent.respond(content),
                    AgentEvent.transition(d.targetState())
            );
        }

        // TOOL_CALL：调用工具，状态机自循环后会再次回调本状态
        if (d.isToolCall()) {
            Map<String, Object> input = d.toolInput() != null ? d.toolInput() : Map.of();

            // 兜底：如果 LLM 没给 query，用原始问题
            if (!input.containsKey("query") || input.get("query") == null) {
                input = new java.util.HashMap<>(input);
                input.put("query", ctx.getState().getOriginalMessage());
            }

            log.info("[DiagnosingState] 执行工具调用: tool={}, input={}", d.toolName(), input);
            return Flux.just(AgentEvent.toolCall(d.toolName(), input));
        }

        // COMPLETE：结束对话（用户解决或取消）
        if (d.isComplete()) {
            String content = d.content() != null ? d.content() : "好的，如有需要随时联系。";
            ctx.getState().addHistory("ai", content);
            return Flux.just(
                    AgentEvent.respond(content),
                    AgentEvent.complete()
            );
        }

        // 未知 action 降级
        log.warn("[DiagnosingState] 未知 action: {}, 降级建单", d.action());
        return fallbackToCollecting(ctx);
    }

    // ==================== 降级 ====================

    private Flux<AgentEvent> fallbackToCollecting(DialogContext ctx) {
        String reply = "我帮您创建报修单吧，请描述一下具体问题？";
        ctx.getState().addHistory("ai", reply);
        return Flux.just(
                AgentEvent.respond(reply),
                AgentEvent.transition("COLLECTING")
        );
    }
}