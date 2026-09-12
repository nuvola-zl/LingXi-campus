package top.lingxi.campus.itAgent.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import top.lingxi.campus.itAgent.agent.core.AgentDecision;
import top.lingxi.campus.itAgent.agent.core.AgentEvent;
import top.lingxi.campus.itAgent.agent.core.AgentOrchestrator;
import top.lingxi.campus.itAgent.agent.core.DialogContext;
import top.lingxi.campus.itAgent.agent.core.DialogState;
import top.lingxi.campus.itAgent.agent.tool.CreateTicketTool;
import top.lingxi.campus.itAgent.state.TicketCreateState;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConfirmingState implements DialogState {

    private final CreateTicketTool createTicketTool;
    private final RedissonClient redissonClient;
    private final AgentOrchestrator orchestrator;

    @Override
    public String name() {
        return "CONFIRMING";
    }

    @Override
    public Flux<AgentEvent> handle(DialogContext ctx, String userMessage) {
        TicketCreateState state = ctx.getState();
        state.setStatus("CONFIRMING");
        state.addHistory("user", userMessage);

        // 核心：让 Orchestrator 判断用户是确认、取消还是修改
        return orchestrator.decide(ctx, userMessage, "CONFIRMING")
                .flatMapMany(decision -> {
                    log.info("[ConfirmingState] 决策: action={}, target={}, reasoning={}",
                            decision.action(), decision.targetState(), decision.reasoning());
                    return executeDecision(ctx, decision);
                })
                .onErrorResume(e -> {
                    log.error("[ConfirmingState] Orchestrator 异常", e);
                    return Flux.just(AgentEvent.respond("系统繁忙，请稍后重试。"));
                });
    }

    // ==================== 决策执行器 ====================

    private Flux<AgentEvent> executeDecision(DialogContext ctx, AgentDecision d) {
        // COMPLETE：取消，结束对话
        if (d.isComplete()) {
            String content = d.content() != null ? d.content() : "好的，已为您取消。如果还有其他问题，随时告诉我。";
            ctx.getState().addHistory("ai", content);
            return Flux.just(AgentEvent.respond(content), AgentEvent.complete());
        }

        // RESPOND：用户说的模糊，追问
        if (d.isRespond()) {
            String content = d.content() != null ? d.content()
                    : "请回复\"确认\"创建报修单，或回复\"取消\"放弃。";
            ctx.getState().addHistory("ai", content);
            return Flux.just(AgentEvent.respond(content));
        }

        // TRANSITION→COLLECTING：用户想修改，回到收集阶段
        if (d.isTransition() && "COLLECTING".equals(d.targetState())) {
            String content = d.content() != null ? d.content()
                    : "好的，请重新描述一下问题，我会更新报修单内容。";
            ctx.getState().addHistory("ai", content);
            return Flux.just(AgentEvent.respond(content), AgentEvent.transition("COLLECTING"));
        }

        // TRANSITION→COMPLETED：用户确认，执行建单（带锁防重）
        if (d.isTransition() && "COMPLETED".equals(d.targetState())) {
            return handleConfirm(ctx);
        }

        // 兜底
        return Flux.just(AgentEvent.respond("请回复\"确认\"创建报修单，或回复\"取消\"放弃。"));
    }

    // ==================== 建单逻辑（Reactor 友好）====================

    private Flux<AgentEvent> handleConfirm(DialogContext ctx) {
        return Mono.fromCallable(() -> {
                    String lockKey = "ticket:create:" + ctx.getUserId() + ":" + ctx.getSessionId();
                    RLock lock = redissonClient.getLock(lockKey);

                    boolean acquired = lock.tryLock(3, 10, TimeUnit.SECONDS);
                    if (!acquired) {
                        return "BUSY";
                    }

                    try {
                        TicketCreateState state = ctx.getState();
                        if (!"CONFIRMING".equals(state.getStatus())) {
                            return "DUPLICATE";
                        }

                        Map<String, Object> args = Map.of(
                                "userId", ctx.getUserId(),
                                "title", state.getTitle(),
                                "description", state.getDescription(),
                                "priority", state.getPriority() != null ? state.getPriority() : 2,
                                "sessionId", ctx.getSessionId()
                        );

                        String result = createTicketTool.execute(args);

                        if (result.startsWith("DUPLICATE:")) {
                            return result;
                        }

                        // 创建成功，立即改状态防重
                        state.setStatus("COMPLETED");
                        return result;

                    } finally {
                        if (lock.isHeldByCurrentThread()) {
                            lock.unlock();
                        }
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())  // 同步锁不卡事件循环
                .flatMapMany(result -> {
                    if ("BUSY".equals(result)) {
                        return Flux.just(AgentEvent.respond("正在处理中，请勿重复提交。"));
                    }
                    if ("DUPLICATE".equals(result)) {
                        return Flux.just(AgentEvent.respond("该报修单已处理，请勿重复提交。"));
                    }
                    if ("ERROR".equals(result)) {
                        return Flux.just(AgentEvent.respond("系统繁忙，请稍后重试。"));
                    }
                    if (result.startsWith("DUPLICATE:")) {
                        String msg = result.substring("DUPLICATE:".length());
                        ctx.getState().addHistory("ai", msg);
                        return Flux.just(AgentEvent.respond(msg), AgentEvent.complete());
                    }

                    ctx.getState().addHistory("ai", result);
                    return Flux.just(AgentEvent.respond(result), AgentEvent.complete());
                })
                .onErrorResume(e -> {
                    log.error("[ConfirmingState] 建单异常", e);
                    return Flux.just(AgentEvent.respond("系统繁忙，请稍后重试。"));
                });
    }
}