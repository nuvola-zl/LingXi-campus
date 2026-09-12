package top.lingxi.campus.itAgent.agent.core;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import top.lingxi.campus.itAgent.agent.tool.ToolRegistry;

import java.util.Map;

@Slf4j
public class AgentStateMachine {

    private final Map<String, DialogState> states;
    private final String initialState;
    private final ToolRegistry toolRegistry;

    /** ReAct 循环最大迭代次数，防止 LLM 反复调用工具陷入死循环 */
    private static final int MAX_REACT_LOOPS = 3;

    public AgentStateMachine(Map<String, DialogState> states,
                             String initialState,
                             ToolRegistry toolRegistry) {
        this.states = states;
        this.initialState = initialState;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 入口：启动状态机
     */
    public Flux<String> process(DialogContext ctx, String userMessage) {
        return processState(ctx, userMessage, initialState);
    }

    /**
     * 递归处理状态事件
     * 关键能力：TOOL_CALL 执行后自循环回到当前状态，形成 ReAct 闭环
     */
    private Flux<String> processState(DialogContext ctx, String userMessage, String currentStateName) {
        DialogState state = states.get(currentStateName);
        if (state == null) {
            return Flux.error(new IllegalStateException("未知状态: " + currentStateName));
        }

        return state.handle(ctx, userMessage)
                .concatMap(event -> {
                    log.debug("[StateMachine] 状态={} | 事件={}", currentStateName, event.type());

                    switch (event.type()) {
                        case RESPOND:
                            return Flux.just((String) event.payload());

                        case STATE_TRANSITION:
                            // ===== 关键修正：只切换状态，不递归驱动 =====
                            // COLLECTING/CONFIRMING 需要等待用户下一条输入
                            String nextState = (String) event.payload();
                            ctx.getState().setStatus(nextState);
                            log.info("[StateMachine] 状态切换: {} -> {}", currentStateName, nextState);
                            return Flux.empty();

                        case COMPLETE:
                            ctx.setFinished(true);
                            ctx.getState().setStatus("COMPLETED");  // ← 新增：标记已完成，防止后续请求被误拦截
                            return Flux.empty();

                        case TOOL_CALL:
                            // ===== ReAct 核心：执行工具 → 写 Observation → 自循环 =====
                            return executeToolAndRecurse(ctx, userMessage, currentStateName, event);

                        default:
                            return Flux.error(new IllegalStateException("未知事件类型: " + event.type()));
                    }
                })
                .onErrorResume(e -> {
                    log.error("[StateMachine] 状态机异常", e);
                    return Flux.just("系统处理异常，请稍后重试。");
                });
    }

    /**
     * 执行工具（放在 boundedElastic 线程），然后自循环当前状态
     */
    private Flux<String> executeToolAndRecurse(DialogContext ctx, String userMessage,
                                               String currentStateName, AgentEvent event) {
        AgentEvent.ToolCall tc = (AgentEvent.ToolCall) event.payload();

        // ===== ReAct 循环上限保护 =====
        int callCount = ctx.incrementAndGetToolCallCount();
        if (callCount > MAX_REACT_LOOPS) {
            log.warn("[StateMachine] ReAct循环已达上限({}次), 强制终止, userId={}, toolName={}",
                    MAX_REACT_LOOPS, ctx.getUserId(), tc.name());
            String fallback = "我查询了多次知识库均未找到相关内容，已自动为您转建单流程。请描述一下具体问题，我会帮您创建工单。";
            ctx.getState().addHistory("ai", fallback);
            ctx.getState().setStatus("COLLECTING");
            ctx.setLastToolResult(null);
            return Flux.just(fallback);
        }
        // ===================================

        Map<String, Object> args = tc.input() instanceof Map
                ? (Map<String, Object>) tc.input()
                : Map.of();

        return Mono.fromCallable(() -> {
                    log.info("[StateMachine] 执行工具: {} (第{}/{}次), args={}",
                            tc.name(), callCount, MAX_REACT_LOOPS, args);
                    return toolRegistry.execute(tc.name(), args);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(result -> {
                    ctx.addToolResult(tc.name(), args, result);
                    log.info("[StateMachine] 工具结果(第{}次): {}",
                            callCount, result.length() > 120 ? result.substring(0, 120) + "..." : result);
                })
                .onErrorResume(e -> {
                    log.error("[StateMachine] 工具执行失败(第{}次)", callCount, e);
                    ctx.addToolResult(tc.name(), args, "工具执行失败: " + e.getMessage());
                    return Mono.just("error");
                })
                .flatMapMany(ignored ->
                        processState(ctx, userMessage, currentStateName)
                );
    }
}