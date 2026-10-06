package top.lingxi.campus.itAgent.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import top.lingxi.campus.itAgent.agent.core.AgentStateMachine;
import top.lingxi.campus.itAgent.agent.core.DialogContext;
import top.lingxi.campus.itAgent.agent.core.DialogState;
import top.lingxi.campus.itAgent.agent.tool.ToolRegistry;
import top.lingxi.campus.itAgent.state.TicketCreateState;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentDialogService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final DiagnosingState diagnosingState;
    private final CollectingState collectingState;
    private final ConfirmingState confirmingState;
    private final ToolRegistry toolRegistry;
    private final ConversationMemoryService memoryService;

    private static final String REDIS_KEY_PREFIX = "agent_dialog:";
    private static final long REDIS_TTL = 30;

    /**
     * 原子性 CAS 更新：
     * KEYS[1] = stateKey, KEYS[2] = verKey
     * ARGV[1] = expectedVersion, ARGV[2] = newJson, ARGV[3] = ttl(秒)
     *
     * 如果 Redis 中 verKey 的值等于 expectedVersion（或不存在视为0），
     * 则更新 stateKey 并递增 verKey，返回 1；否则返回 0。
     */
    private static final String CAS_SAVE_LUA =
            "local stateKey = KEYS[1] " +
                    "local verKey = KEYS[2] " +
                    "local expectedVer = tonumber(ARGV[1]) " +
                    "local newJson = ARGV[2] " +
                    "local ttl = tonumber(ARGV[3]) " +
                    "local currentVer = redis.call('get', verKey) " +
                    "if currentVer == false then currentVer = 0 else currentVer = tonumber(currentVer) end " +
                    "if currentVer == expectedVer then " +
                    "    redis.call('set', stateKey, newJson, 'EX', ttl) " +
                    "    redis.call('incr', verKey) " +
                    "    redis.call('expire', verKey, ttl) " +
                    "    return 1 " +
                    "else " +
                    "    return 0 " +
                    "end";

    private final DefaultRedisScript<Long> casSaveScript =
            new DefaultRedisScript<>(CAS_SAVE_LUA, Long.class);

    // ==================== 状态查询 ====================


    //
//    DIAGNOSING   诊断中（在查知识库、给建议）
//    COLLECTING   收集描述中（在追问用户）
//    CONFIRMING   等用户确认工单
//    COMPLETED    已完成/已取消（对话结束了）
    public boolean hasOngoingDialog(Long userId) {
        TicketCreateState state = getState(userId);
        if (state == null) return false;
        return !"COMPLETED".equals(state.getStatus());
    }

    /**
     * 读取 Agent 状态。
     *
     * 版本号约定（重构后）：
     * - verKey 是版本号的唯一权威来源；
     * - JSON 里也存 version，仅作 verKey 丢失时的兜底，正常路径会被 verKey 覆盖。
     *
     * 为什么读取时仍要覆盖一次？
     * 1. 兼容升级期间的老数据：旧代码保存的 JSON 中 version 可能滞后于 verKey；
     * 2. 防御未来有人绕过 saveStateCas 直接写 stateKey。
     * 覆盖操作成本极低（一次 GET），换来"任何情况下版本都对得上"。
     */
    public TicketCreateState getState(Long userId) {
        String key = REDIS_KEY_PREFIX + userId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) return null;
        try {
            TicketCreateState state = objectMapper.readValue(json, TicketCreateState.class);

            String verKey = REDIS_KEY_PREFIX + userId + ":ver";
            String verStr = redisTemplate.opsForValue().get(verKey);
            if (verStr != null) {
                // verKey 优先：它是 Lua incr 维护的权威版本
                state.setVersion(Integer.parseInt(verStr));
            } else if (state.getVersion() < 0) {
                // verKey 丢失的异常情况：至少保证非负，不阻断流程
                state.setVersion(0);
            }

            return state;
        } catch (Exception e) {
            log.error("Agent状态反序列化失败", e);
            return null;
        }
    }

    public void clearState(Long userId) {
        String stateKey = REDIS_KEY_PREFIX + userId;
        String verKey = REDIS_KEY_PREFIX + userId + ":ver";
        redisTemplate.delete(stateKey);
        redisTemplate.delete(verKey);
    }



    public Flux<String> startDialog(Long userId, String prompt, Long sessionId) {
        // 清除旧状态
        clearState(userId);

        DialogContext ctx = new DialogContext();
        ctx.setUserId(userId);
        ctx.setSessionId(sessionId);

        // 初始化状态，设置为诊断中
        TicketCreateState state = new TicketCreateState();
        state.setStatus("DIAGNOSING");
        state.setSessionId(sessionId);
        state.setOriginalMessage(prompt);
        state.setVersion(0); // 初始版本

        String memory = memoryService.getMemory(sessionId);
        if (memory != null && !memory.isEmpty()) {
            state.setSuggestionContent("【历史对话摘要】" + memory);
            log.info("[Memory] 已加载历史摘要: sessionId={}", sessionId);
        }

        ctx.setState(state);

        AgentStateMachine machine = createMachine("DIAGNOSING");

        // doFinally 捕获 onComplete / onError / cancel 三种信号
        // 让状态机去执行，不断的返回结果
        return machine.process(ctx, prompt)
                //拿到这个返回的Flux<String>说明书，
                .doFinally(signal -> {
                    log.info("[AgentDialogService] startDialog 流结束, signal={}, userId={}", signal, userId);
                    //贴上一个标签，根据不同的信号，执行不同的操作
                    afterProcess(userId, ctx);
                });
    }

    // ==================== 继续对话 ====================

    public Flux<String> continueDialog(Long userId, String prompt, Long sessionId) {
        TicketCreateState state = getState(userId);
        if (state == null) {
            return Flux.just("会话已过期，请重新描述您的问题。");
        }

        if (state.getSessionId() != null && !state.getSessionId().equals(sessionId)) {
            clearState(userId);
            return Flux.just("会话已过期，请重新描述您的问题。");
        }

        DialogContext ctx = new DialogContext();
        ctx.setUserId(userId);
        ctx.setSessionId(sessionId);
        ctx.setState(state);

        AgentStateMachine machine = createMachine(state.getStatus());

        // doFinally 确保取消/异常/完成都走 afterProcess
        //让状态机去执行，不断的返回结果
        return machine.process(ctx, prompt)
                //拿到这个返回的Flux<String>说明书，
                .doFinally(signal -> {
                    log.info("[AgentDialogService] continueDialog 流结束, signal={}, userId={}", signal, userId);
                    //贴上一个标签，根据不同的信号，执行不同的操作
                    afterProcess(userId, ctx);
                });
    }



    /**
     * 创建状态机，根据初始状态配置状态。
     */
    private AgentStateMachine createMachine(String initialState) {
        Map<String, DialogState> states = new HashMap<>();
        states.put("DIAGNOSING", diagnosingState);
        states.put("COLLECTING", collectingState);
        states.put("CONFIRMING", confirmingState);
        return new AgentStateMachine(states, initialState, toolRegistry);
    }

    /**
     * 流结束后统一处理：正常完成、异常、取消都会进这里
     */
    private void afterProcess(Long userId, DialogContext ctx) {
        if (ctx.isFinished()) {
            clearState(userId);
            log.info("Agent对话正常结束: userId={}", userId);
        } else {
            // 1. 更新记忆
            memoryService.updateMemory(ctx.getSessionId(), ctx.getState().getConversationHistory());

            // 2. CAS 保存状态（防并发覆盖）
            boolean saved = saveStateCas(userId, ctx.getState());
            if (!saved) {
                log.error("[AgentDialogService] 状态保存冲突，可能并发操作: userId={}", userId);
                // 冲突时状态不保存，下次请求会读到旧版本，用户需要重新描述
                // 也可以在这里选择重试 3 次，
            } else {
                log.info("Agent状态已保存: userId={}, status={}, version={}",
                        userId, ctx.getState().getStatus(), ctx.getState().getVersion());
            }
        }
    }

    /**
     * 原子性 CAS 保存（重构版）。
     *
     * 【重构说明】原实现先序列化 JSON 再执行 Lua，导致 JSON 里的 version
     * 永远比 verKey 少 1，读取方不得不做版本覆盖来打补丁。
     * 现在改为"先升版本、再序列化"，让 JSON 和 verKey 天然一致：
     *
     *   记录旧版本 expectedVer → 内存 version +1 → 序列化 JSON（含新版本）
     *   → Lua：verKey == expectedVer 才写入，并 incr verKey 到同一新版本
     *
     * CAS 失败/异常时把内存版本回滚到 expectedVer，维持"内存版本 == verKey"不变式。
     */
    private boolean saveStateCas(Long userId, TicketCreateState state) {
        String stateKey = REDIS_KEY_PREFIX + userId;
        String verKey = REDIS_KEY_PREFIX + userId + ":ver";
        int expectedVer = state.getVersion();      // ① 读取时的旧版本（CAS 比较基准）
        try {
            // ② 先升内存版本：JSON 和 Lua incr 后的 verKey 都将是这个新版本
            state.setVersion(expectedVer + 1);

            // ③ 序列化（此刻 JSON 里的 version 就是新版本）
            String json = objectMapper.writeValueAsString(state);

            // ④ Lua CAS：verKey 仍等于旧版本才允许写入，incr 后正好是新版本
            Long result = redisTemplate.execute(
                    casSaveScript,
                    Arrays.asList(stateKey, verKey),
                    String.valueOf(expectedVer),          // expectedVer = 旧版本
                    json,
                    String.valueOf(REDIS_TTL * 60)        // 30 分钟转秒
            );

            if (result != null && result == 1) {
                // 保存成功：内存 / JSON / verKey 三方同版本，一致
                return true;
            } else {
                // CAS 冲突：回滚内存版本，维持不变式
                state.setVersion(expectedVer);
                log.warn("[AgentDialogService] CAS 保存失败，version 冲突: userId={}, expectedVer={}",
                        userId, expectedVer);
                return false;
            }
        } catch (Exception e) {
            // 序列化或 Redis 异常：同样回滚内存版本
            state.setVersion(expectedVer);
            log.error("Agent状态保存失败", e);
            return false;
        }
    }
}