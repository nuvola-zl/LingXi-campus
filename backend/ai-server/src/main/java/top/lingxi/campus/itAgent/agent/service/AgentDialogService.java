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

    // ==================== 新增：Lua CAS 脚本 ====================
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

    public boolean hasOngoingDialog(Long userId) {
        TicketCreateState state = getState(userId);
        if (state == null) return false;
        return !"COMPLETED".equals(state.getStatus());  // ← 新增：已完成的不算进行中
    }

    public TicketCreateState getState(Long userId) {
        String key = REDIS_KEY_PREFIX + userId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) return null;
        try {
            TicketCreateState state = objectMapper.readValue(json, TicketCreateState.class);

            // 【关键修复】从 verKey 读取真实版本号，覆盖 JSON 里的旧 version
            String verKey = REDIS_KEY_PREFIX + userId + ":ver";
            String verStr = redisTemplate.opsForValue().get(verKey);
            if (verStr != null) {
                state.setVersion(Integer.parseInt(verStr));
            }
            // 如果 verKey 不存在（异常情况），至少保证 version 非负
            else if (state.getVersion() < 0) {
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

    // ==================== 启动新对话 ====================

    public Flux<String> startDialog(Long userId, String prompt, Long sessionId) {
        clearState(userId);

        DialogContext ctx = new DialogContext();
        ctx.setUserId(userId);
        ctx.setSessionId(sessionId);

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

        // 关键改造：doFinally 捕获 onComplete / onError / cancel 三种信号
        return machine.process(ctx, prompt)
                .doFinally(signal -> {
                    log.info("[AgentDialogService] startDialog 流结束, signal={}, userId={}", signal, userId);
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

        // 关键改造：doFinally 确保取消/异常/完成都走 afterProcess
        return machine.process(ctx, prompt)
                .doFinally(signal -> {
                    log.info("[AgentDialogService] continueDialog 流结束, signal={}, userId={}", signal, userId);
                    afterProcess(userId, ctx);
                });
    }

    // ==================== 内部方法 ====================

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
                // 也可以在这里选择重试 3 次，但简历项目保持简洁
            } else {
                log.info("Agent状态已保存: userId={}, status={}, version={}",
                        userId, ctx.getState().getStatus(), ctx.getState().getVersion());
            }
        }
    }

    /**
     * 原子性 CAS 保存：用 Redis Lua 脚本保证 get-check-set 原子
     */
    private boolean saveStateCas(Long userId, TicketCreateState state) {
        String stateKey = REDIS_KEY_PREFIX + userId;
        String verKey = REDIS_KEY_PREFIX + userId + ":ver";
        try {
            String json = objectMapper.writeValueAsString(state);
            Long result = redisTemplate.execute(
                    casSaveScript,
                    Arrays.asList(stateKey, verKey),
                    String.valueOf(state.getVersion()),
                    json,
                    String.valueOf(REDIS_TTL * 60) // 30 分钟转秒
            );

            if (result != null && result == 1) {
                // 保存成功，内存中的 version 也要 +1，和 Redis 保持一致
                state.setVersion(state.getVersion() + 1);
                return true;
            } else {
                log.warn("[AgentDialogService] CAS 保存失败，version 冲突: userId={}, expectedVer={}",
                        userId, state.getVersion());
                return false;
            }
        } catch (Exception e) {
            log.error("Agent状态保存失败", e);
            return false;
        }
    }
}