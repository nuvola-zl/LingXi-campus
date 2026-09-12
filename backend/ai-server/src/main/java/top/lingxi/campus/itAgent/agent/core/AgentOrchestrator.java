package top.lingxi.campus.itAgent.agent.core;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import top.lingxi.campus.itAgent.state.TicketCreateState;
import top.lingxi.campus.itAgent.agent.tool.ToolRegistry;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
public class AgentOrchestrator {

    private final DashScopeChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final ToolRegistry toolRegistry;

    private static final int MAX_CONTEXT_LENGTH = 4000;
    private static final int DECISION_TIMEOUT_SECONDS = 15;

    /** 单轮用户消息最多允许 LLM 决策次数（防 ReAct 死循环） */
    private static final int MAX_LLM_CALLS_PER_TURN = 5;


    /**
     * Key: 状态名 + 用户输入 + 历史摘要的 hash
     * Value: AgentDecision
     *
     * 为什么用本地缓存而不是 Redis：
     * 1. 决策结果只和当前上下文有关，不需要跨节点共享
     * 2. Caffeine 性能极高，无网络开销
     * 3. LLM 决策有随机性，缓存太久会导致"AI 变机械"，设 30 秒足够覆盖连点/重试
     */
    private final Cache<String, AgentDecision> decisionCache = Caffeine.newBuilder()
            .maximumSize(1000)              // 最多缓存 1000 条
            .expireAfterWrite(30, TimeUnit.SECONDS)  // 30 秒过期
            .recordStats()                   // 开启统计，方便监控
            .build();


    public AgentOrchestrator(
            @Qualifier("agentDecisionModel") DashScopeChatModel chatModel,
            ObjectMapper objectMapper,
            ToolRegistry toolRegistry) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.toolRegistry = toolRegistry;
    }

    public Mono<AgentDecision> decide(DialogContext ctx, String userMessage, String currentState) {
        // 1. 先查缓存
        String cacheKey = buildCacheKey(ctx, userMessage, currentState);
        AgentDecision cached = decisionCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.info("[Orchestrator] 缓存命中: key={}, action={}", cacheKey, cached.action());
            return Mono.just(cached);
        }

        // 2. 防死循环检查
        int count = ctx.incrementAndGetLlmCallCount();
        if (count > MAX_LLM_CALLS_PER_TURN) {
            log.warn("[Orchestrator] LLM 调用次数超限({}/{}), 强制降级", count, MAX_LLM_CALLS_PER_TURN);
            return Mono.just(buildFallbackDecision(currentState));
        }

        // 3. 调 LLM（原有逻辑）
        return Mono.fromCallable(() -> doDecide(ctx, userMessage, currentState))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(DECISION_TIMEOUT_SECONDS))
                .doOnError(e -> log.error("[Orchestrator] LLM 决策调用失败", e))
                .onErrorResume(e -> Mono.just(buildFallbackDecision(currentState)))
                .doOnNext(decision -> {
                    // 4. 写入缓存（只有成功决策才缓存，fallback 不缓存）
                    if (!"TRANSITION".equals(decision.action()) ||
                            !"COLLECTING".equals(decision.targetState()) ||
                            decision.content() == null ||
                            !decision.content().contains("请描述一下具体问题")) {
                        // 不缓存降级结果
                        decisionCache.put(cacheKey, decision);
                        log.debug("[Orchestrator] 缓存写入: key={}", cacheKey);
                    }
                });
    }

    // ==================== 缓存 Key 构建 ====================

    /**
     * 缓存 Key 设计原则：
     * - 必须包含：当前状态 + 用户输入 + 历史轮数 + 是否有 Observation + 是否有 suggestion
     * - 不包含：sessionId、userId（不同用户相同上下文可以复用决策，比如都说"确认"）
     * - 用简单拼接 + hash，避免超长 key
     */
    private String buildCacheKey(DialogContext ctx, String userMessage, String currentState) {
        TicketCreateState state = ctx.getState();
        int historyTurns = state.getConversationHistory() != null ? state.getConversationHistory().size() : 0;
        boolean hasObs = ctx.getLastToolResult() != null;
        boolean hasSug = state.getSuggestionContent() != null;

        String raw = String.format("%s|%s|turns=%d|obs=%s|sug=%s",
                currentState,
                userMessage.trim().toLowerCase(),
                historyTurns,
                hasObs,
                hasSug
        );
        // 用 hashCode 压缩，避免 Redis key 过长（本地缓存无所谓，但养成好习惯）
        return "agent_decision:" + Integer.toHexString(raw.hashCode());
    }

    // ==================== 新增：缓存统计接口（调试用）====================

    public Map<String, Object> getCacheStats() {
        return Map.of(
                "hitCount", decisionCache.stats().hitCount(),
                "missCount", decisionCache.stats().missCount(),
                "hitRate", String.format("%.2f%%", decisionCache.stats().hitRate() * 100),
                "size", decisionCache.estimatedSize()
        );
    }

    // ==================== 核心：按状态动态构建 Prompt ====================

    private AgentDecision doDecide(DialogContext ctx, String userMessage, String currentState) throws Exception {
        TicketCreateState state = ctx.getState();

        String history = buildHistory(state);
        String toolHistory = ctx.getToolCallHistoryText();
        String observation = ctx.getLastToolResult();
        String suggestion = state.getSuggestionContent();

        String promptText = buildPrompt(currentState, state, userMessage, history, suggestion, toolHistory, observation);

        // 长度保护
        if (promptText.length() > MAX_CONTEXT_LENGTH) {
            log.warn("[Orchestrator] Prompt 超长({})，截断历史", promptText.length());
            promptText = buildPrompt(currentState, state, userMessage,
                    "（历史过长已省略）",
                    suggestion != null ? truncate(suggestion, 400) : "无",
                    "（工具历史已省略）",
                    observation != null ? truncate(observation, 400) : "无");
        }

        Prompt prompt = new Prompt(promptText);
        ChatResponse response = chatModel.call(prompt);
        String json = cleanJsonMarkdown(response.getResult().getOutput().getText());

        log.debug("[Orchestrator] LLM 原始输出: {}", json);

        AgentDecision decision = objectMapper.readValue(json, AgentDecision.class);

        // 参数兜底
        if (decision.isToolCall() && (decision.toolInput() == null || !decision.toolInput().containsKey("query"))) {
            Map<String, Object> fixedInput = decision.toolInput() != null
                    ? new java.util.HashMap<>(decision.toolInput())
                    : new java.util.HashMap<>();
            fixedInput.put("query", state.getOriginalMessage());
            decision = new AgentDecision(
                    decision.reasoning(), decision.action(), decision.targetState(),
                    decision.content(), decision.toolName(), fixedInput
            );
        }

        return decision;
    }

    // ==================== Prompt 动态构建 ====================

    private String buildPrompt(String currentState, TicketCreateState state, String userMessage,
                               String history, String suggestion, String toolHistory, String observation) {
        return String.format(ORCHESTRATOR_PROMPT,
                currentState,
                truncate(state.getOriginalMessage(), 200),
                truncate(userMessage, 500),
                history,
                suggestion != null ? truncate(suggestion, 800) : "无",
                toolHistory != null && !toolHistory.isEmpty() ? toolHistory : "无",
                observation != null ? truncate(observation, 600) : "无",
                toolRegistry.buildToolDescriptions(),
                buildStateRules(currentState)
        );
    }

    private static final String ORCHESTRATOR_PROMPT = """
      你是灵犀校园后勤报修 Agent 调度器。请严格根据当前对话上下文，分析用户真实意图并输出结构化决策。
      
      【当前状态】%s
      【原始问题】%s
      【用户输入】%s
      【对话历史】%s
      【已给出的知识库建议】%s
      【工具调用历史】%s
      【最新工具执行结果（Observation）】%s
      【可用工具】%s
      
      %s
      
      【通用约束】
      1. 用户只是表示"收到/知道了/我试试/行/ok/嗯嗯/了解"（不含解决语义，且没有提到问题已修复/恢复/搞定）→ 不要误以为已解决
      2. 用户说"好的/可以/ok" + 提到"解决了/搞定了/没事了/恢复了/好了" → 属于规则2（明确已解决）→ COMPLETE
      3. 用户输入与 IT 问题完全无关（天气/食堂/个人信息/闲聊）→ COMPLETE，礼貌结束
      4. 输出必须严格是 JSON，不要 markdown，不要额外文字

      【输出格式】
      {
        "reasoning": "你的思考过程，说明为什么选这个 action",
        "action": "RESPOND|TRANSITION|TOOL_CALL|COMPLETE",
        "targetState": "DIAGNOSING|COLLECTING|CONFIRMING|COMPLETED",
        "content": "给用户的回复内容（RESPOND/TRANSITION/COMPLETE 时必填）",
        "toolName": "search_knowledge",
        "toolInput": {"query": "查询内容"}
      }
      """;

    private String buildStateRules(String currentState) {
        return switch (currentState) {
            case "DIAGNOSING" -> """
       【DIAGNOSING 状态规则】
       1. 用户明确取消/放弃（算了/取消/不建了/放弃/不用了/别建了）→ COMPLETE
       2. 用户明确表达已解决（解决了/谢谢/搞定了/可以了/没事了/恢复了/好了/ok了）→ COMPLETE
       3. 当前首次查询且 Observation 为空 → TOOL_CALL search_knowledge
       4. 已有知识库内容（Observation 非空且非 KNOWLEDGE_EMPTY）→ 禁止再次调用 search_knowledge！应基于已有内容向用户提问具体现象，或给出排查建议 → RESPOND
       5. 已有知识库建议，用户明确否定（还是不行/没用/没解决/无效/不好使/没搞定）→ TRANSITION→COLLECTING
       6. 用户要求直接建单（创建工单/帮我建单/建立工单/开个工单/我要建单）→ TRANSITION→COLLECTING
       7. Observation 以 【KNOWLEDGE_EMPTY】 开头 → TRANSITION→COLLECTING，content="我检索了知识库，暂未找到与您问题完全匹配的方案。为了避免耽误您的时间，我直接帮您创建报修单吧，请描述一下具体现象？"
       8. 用户明显切换意图（查工单号/要求生图/闲聊天气食堂/问与当前问题无关的事）→ COMPLETE，content="好的，我先结束当前处理。如有其他需要随时告诉我。"
       9. 其他情况 → RESPOND，给出有帮助的回复或追问
       """;
            case "COLLECTING" -> """
       【COLLECTING 状态规则】
       1. 用户明确取消/放弃 → COMPLETE
       2. 用户描述过短（如只有1-2个无意义字）或过于模糊（如只有"网络""电脑"等名词，无现象描述）→ RESPOND，追问具体现象
       3. 用户描述足够具体，可以建单 → TRANSITION→CONFIRMING，content 是给用户的确认文案（包含标题、描述摘要）
       4. 用户明显切换意图（查工单号/要求生图/闲聊天气食堂/问与当前工单无关的问题）→ COMPLETE，content="好的，我先结束当前工单创建。如有其他需要随时告诉我。"
       5. 用户跑题/闲聊 → COMPLETE
       """;
            case "CONFIRMING" -> """
       【CONFIRMING 状态规则】
       1. 用户明确确认（确认/好/是/ok/可以/行/yes/要得/中/搞吧/整吧/弄吧）→ TRANSITION→COMPLETED，content="正在为您创建工单..."
       2. 用户明确取消（取消/不/算了/放弃/no/别/不建了/不要了）→ COMPLETE
       3. 用户想修改内容（改一下/不对/重新来/换一下/标题不对/描述少了/补充一下）→ TRANSITION→COLLECTING，content="好的，请重新描述一下问题，我会更新工单内容。"
       4. 用户不回复确认/取消，而是问其他问题（查工单/生图/闲聊/问其他问题）→ COMPLETE，content="好的，我先结束当前工单创建。如有其他需要随时告诉我。"
       5. 其他模糊输入 → RESPOND，请用户明确回复确认或取消
       """;
            default -> """
       【通用规则】
       根据上下文做出最合理的决策。
       """;
        };
    }

    // ==================== 降级策略（按状态）====================

    private AgentDecision buildFallbackDecision(String currentState) {
        return switch (currentState) {
            case "COLLECTING" -> new AgentDecision(
                    "LLM 失败，降级追问", "RESPOND", null,
                    "请详细描述一下问题的具体现象，方便我帮您创建报修单。", null, null);
            case "CONFIRMING" -> new AgentDecision(
                    "LLM 失败，降级提示", "RESPOND", null,
                    "请回复\"确认\"创建工单，或回复\"取消\"放弃。", null, null);
            default -> new AgentDecision(
                    "LLM 失败，降级建单", "TRANSITION", "COLLECTING",
                    "我帮您创建报修单吧，请描述一下具体问题？", null, null);
        };
    }

    // ==================== 工具方法 ====================

    private String buildHistory(TicketCreateState state) {
        if (state.getConversationHistory() == null || state.getConversationHistory().isEmpty()) {
            return "无";
        }
        int total = state.getConversationHistory().size();
        return state.getConversationHistory().stream()
                .skip(Math.max(0, total - 5))
                .map(h -> ("ai".equals(h.getRole()) ? "AI" : "用户") + ": " + h.getContent())
                .collect(Collectors.joining("\n"));
    }

    private String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...(已截断)";
    }

    private String cleanJsonMarkdown(String json) {
        json = json.trim();
        if (json.startsWith("```json")) json = json.substring(7);
        else if (json.startsWith("```")) json = json.substring(3);
        if (json.endsWith("```")) json = json.substring(0, json.length() - 3);
        return json.trim();
    }
}