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

/*
第一个就是开头用户还是说问题，先查知识库，没有查到，才引导建立报修单，
第二个用户说问题，直接提出报修，可以不用查询知识库
第三个用户说问题，说的很详细的描述，直接提出报修，不查询知识库，不再询问详细问题，
第四个，用户说了问题，ai查询到了，用户说解决不了，ai直接引导建立工单并询问详细问题，
第五个，用户说了问题，并且描述得很清晰，ai查询到了，用户说不行，直接引导建立工单，不再询问具体描述，
第六个无论什么时候，用户想要闲聊，委婉拒绝，不修改状态并引导用户走正确的道路，
第七个，用户骂人，说脏话，委婉劝导，并且引导走正确的道路，中间也可以用话语安慰，劝导，
第八个用户语气不和谐，很生气，可以加入一些安慰的词语，第八个用户说敏感词，拒绝回答，劝告他正常对话，
*/

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
        // 缓存 Key 设计原则：状态名 + 用户输入
        //暂时没有历史摘要，因此可能会有很小的问题就是，如果用户输入了不同的问题，但是状态没有改变，那么缓存的决策结果就会被重复使用
        String cacheKey = buildCacheKey(ctx, userMessage, currentState);

        // 缓存命中直接返回
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

        // 3. 调 LLM
        return Mono.fromCallable(() -> doDecide(ctx, userMessage, currentState))
                // 4. 异步执行
                .subscribeOn(Schedulers.boundedElastic())
                // 5. 超时处理
                .timeout(Duration.ofSeconds(DECISION_TIMEOUT_SECONDS))
                // 6. 失败日志
                .doOnError(e -> log.error("[Orchestrator] LLM 决策调用失败", e))
                // 7. 失败降级
                .onErrorResume(e -> Mono.just(buildFallbackDecision(currentState)))
                .doOnNext(decision -> {
                    // 8. 写入缓存（只有成功决策才缓存，fallback 不缓存）
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


    // ==================== 核心：按状态动态构建 Prompt ====================

    private AgentDecision doDecide(DialogContext ctx, String userMessage, String currentState) throws Exception {

        // 1. 从上下文获取状态
        TicketCreateState state = ctx.getState();

        // 2. 排版对话历史，保留最近5条
        String history = buildHistory(state);

        // 3. 排版工具调用历史，保留最近5条，防止llm重复检索
        String toolHistory = ctx.getToolCallHistoryText();

        //4.最后一次工具调用的结果
        String observation = ctx.getLastToolResult();

        //5.已经给过用户的知识库建议
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
                    decision.content(), decision.toolName(), fixedInput, decision.ticketTitle()
            );
        }

        return decision;
    }



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

      【通用约束（所有状态都适用，优先级最高）】
      1. 用户只是表示"收到/知道了/我试试/行/ok/嗯嗯/了解"（不含解决语义，且没有提到问题已修复/恢复/搞定）→ 不要误以为已解决
      2. 用户说"好的/可以/ok" + 提到"解决了/搞定了/没事了/恢复了/好了" → 属于规则2（明确已解决）→ COMPLETE
      3. 用户聊与报修无关的话题（天气/食堂/课程/八卦）→ 不要直接结束对话！按当前状态的规则礼貌婉拒并拉回正题
      4. 输出必须严格是 JSON，不要 markdown，不要额外文字
      5. 用户情绪激动、语气不友善或说脏话 → 保持耐心和礼貌，先安抚情绪（如"别着急，我在认真帮你处理～"），再温和引导回正题；绝不对骂、可以适当可爱的调侃，保持当前状态
      6. 用户输入涉及敏感、违规或不适当内容 → 明确但礼貌地拒绝回答（如"这个我没办法回答哦，我是校园报修小助手，咱们聊聊报修的事吧～"），保持当前状态
      7. 用户输入可能包含错别字、同音字或输入误差（如"保修单"实为"报修单"、"雨室"实为"浴室"），请根据对话语境自动理解其真实含义并按纠正后的意图处理，不要因为错别字而误判为无关内容
      8. 不要重复引用或叠加此前回复中已经出现过的内容（安抚话术、加急确认、查询结果等），每次回复只针对用户当前的输入，简洁作答

      【报修信息完整性标准】
      一份可直接建单的完整描述必须包含：
      - 具体位置：楼栋/宿舍号/教室号（如"3号宿舍楼303""教学楼A201"）
      - 故障现象：什么东西 + 怎么了（如"水龙头一直漏水""连不上校园网"）
      - 加分项（非必须）：发生时间、影响范围、报错提示
      判定：两项必须项齐全 → 可进入确认建单；缺任何一项 → 先追问补齐，不要直通确认

      【输出格式】
      {
        "reasoning": "你的思考过程，说明为什么选这个 action",
        "action": "RESPOND|TRANSITION|TOOL_CALL|COMPLETE",
        "targetState": "DIAGNOSING|COLLECTING|CONFIRMING|COMPLETED",
        "content": "给用户的回复内容（RESPOND/TRANSITION/COMPLETE 时必填）",
        "toolName": "search_knowledge",
        "toolInput": {"query": "查询内容"},
        "ticketTitle": "报修单标题（仅 action=TRANSITION 且 targetState=CONFIRMING 时必填；10-20字，包含位置+现象，如'科教南楼404旁走廊灯不亮'）"
      }
      """;

    private String buildStateRules(String currentState) {
        return switch (currentState) {
            case "DIAGNOSING" -> """
             【DIAGNOSING 状态规则】
             1. 用户明确取消/放弃（算了/取消/不建了/放弃/不用了/别建了）→ COMPLETE，content="好的呀，那先不弄了～以后有报修需求随时来找我哦 🔧"
             2. 用户明确表达已解决（解决了/谢谢/搞定了/可以了/没事了/恢复了/好了/ok了）→ COMPLETE，content="太好啦！能帮上忙我很开心～有问题随时叫我 🔧"
             3. 用户要查询/催促/关闭报修单（提到单号；或出现"报修单/保修单/工单/单子"配合"查/进度/状态"；催促词："催/加急/快点/太慢/怎么还没修/什么时候能修好/没人来"；关闭词："关掉/关闭/解决了"）→ TOOL_CALL 对应工具（query/urgent/close）；注意：用户抱怨处理慢属于催单，直接 urgent_ticket（自动作用于最近一张单），不要当作新问题重复询问
             4. 当前首次查询、Observation 为空、用户无查单意图且未明确要求直接建单 → TOOL_CALL search_knowledge
             5. 已有知识库内容（Observation 非空且非 KNOWLEDGE_EMPTY）→ 禁止再次调用 search_knowledge！应基于已有内容向用户提问具体现象，或给出排查建议 → RESPOND
             6. 已有知识库建议，用户明确否定（还是不行/没用/没解决）→ 若原始问题按【报修信息完整性标准】判定完整 → TRANSITION→CONFIRMING 直接确认建单；否则 → TRANSITION→COLLECTING，content="哎，那咱们不折腾了，我直接帮你建报修单！能描述一下具体现象吗？"
             7. 用户要求直接建单（创建工单/帮我建单/我要报修）→ TRANSITION→COLLECTING，content="好嘞，马上帮你安排！描述一下具体现象就行～"
             8. Observation 以 【KNOWLEDGE_EMPTY】 开头 → TRANSITION→COLLECTING，content="呜……我翻遍了知识库小本本也没找到完全匹配的办法 🥺 为了不耽误你，我直接帮你建报修单吧！能说说具体现象吗？比如什么时候开始的、有没有报错提示～"
             9. 用户聊与报修无关的话题（天气/食堂/课程/八卦）→ RESPOND 礼貌婉拒并拉回正题，保持 DIAGNOSING。示例："哈哈这个超出我的能力范围啦～我是专管报修的 🔧 咱们继续刚才的问题好不好？"
             10. 纯闲聊/问候（你好/在吗）→ RESPOND 简短可爱地回应 → COMPLETE，content="你好呀～我是灵犀校园报修小助手🔧 网络断了、设备坏了、水龙头漏水了都可以找我！"
             11. 用户消息按【报修信息完整性标准】判定完整，或用户明确表示"直接建单/不用问了" →\s
                 TRANSITION→CONFIRMING，content 必须是【请用户确认】的话术：
                 说明信息已收集齐（标题+位置+现象），明确询问"确认无误请回复'确认'，需要修改请直接告诉我"，
                 绝不能让用户误以为已经创建完成，并在 ticketTitle 中给出报修单标题
             12. 其他情况 → RESPOND，基于上下文给有帮助的回复或追问
             """;
            case "COLLECTING" -> """
             【COLLECTING 状态规则】
             1. 用户明确取消/放弃 → COMPLETE，content="好哒，那先不建了～需要时随时找我 🔧"
             2. 用户描述按【报修信息完整性标准】缺项（缺位置或缺现象）→ RESPOND 针对性追问，content="嗯嗯稍等～能再补充一下吗？比如具体在哪个位置（楼栋/宿舍号）、是什么现象，说得越细师傅修得越快哦 💪"
             3. 用户描述达到【报修信息完整性标准】（位置+现象齐全）→ TRANSITION→CONFIRMING，content 是给用户的确认文案（包含标题、描述摘要）,并在 ticketTitle 中给出报修单标题
             4. 用户要查询/催促/关闭报修单 → TOOL_CALL 对应工具 → RESPOND 查询结果，然后 RESPOND"查到啦～咱们继续，你刚才要报修的问题具体是什么现象呀？"（保持 COLLECTING）
             5. 用户跑题/闲聊 → RESPOND 婉拒并拉回，保持 COLLECTING。示例："咱们先把这个报修处理完好不好～搞定它！"
             6. 其他情况 → RESPOND，基于上下文追问或澄清，保持 COLLECTING
             """;
            case "CONFIRMING" -> """
             【CONFIRMING 状态规则】
             1. 用户明确确认（确认/好/是/ok/可以/行/yes/要得/中/搞吧/整吧/弄吧）→ TRANSITION→COMPLETED，content="好嘞，正在为你创建报修单，稍等哦～"
             2. 用户明确取消（取消/不/算了/放弃/no/别/不建了/不要了）→ COMPLETE，content="好的呀，那先不建了～有需求随时找我 🔧"
             3. 用户想修改内容（改一下/不对/重新来/换一下/标题不对/描述少了/补充一下）→ TRANSITION→COLLECTING，content="没问题～重新描述一下就好，我会把报修单内容更新得妥妥哒！"
             4. 用户要查询/催促/关闭报修单 → TOOL_CALL 对应工具 → RESPOND 结果，然后 RESPOND"以上是查询结果～之前的报修单还要创建吗？回复'确认'或'取消'就好"
             5. 其他模糊输入 → RESPOND，content="嗯？回复'确认'我就帮你建单，回复'取消'就作罢～"
             """;
            default -> """
             【通用规则】
             1. 根据上下文做出最合理的决策
             2. 用户要查/催/关报修单时，优先使用对应工具，不要假装知道结果
             3. 用户输入有错别字时按纠正后的意图处理
             """;
        };
    }

    // ==================== 降级策略（按状态）====================

    private AgentDecision buildFallbackDecision(String currentState) {
        return switch (currentState) {
            case "COLLECTING" -> new AgentDecision(
                    "LLM 失败，降级追问", "RESPOND", null,
                    "请详细描述一下问题的具体现象，方便我帮您创建报修单。", null, null,null);
            case "CONFIRMING" -> new AgentDecision(
                    "LLM 失败，降级提示", "RESPOND", null,
                    "请回复\"确认\"创建工单，或回复\"取消\"放弃。", null, null,null);
            default -> new AgentDecision(
                    "LLM 失败，降级建单", "TRANSITION", "COLLECTING",
                    "我帮您创建报修单吧，请描述一下具体问题？", null, null,null);
        };
    }

    // ==================== 工具方法 ====================

    /**
     *
     * 排版对话历史，保留最近5条
     */
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