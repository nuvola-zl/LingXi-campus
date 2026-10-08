package top.lingxi.campus.itAgent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import top.lingxi.campus.itAgent.agent.core.AgentDecision;
import top.lingxi.campus.itAgent.agent.core.AgentOrchestrator;
import top.lingxi.campus.itAgent.agent.core.DialogContext;
import top.lingxi.campus.itAgent.state.TicketCreateState;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent 决策准确率评测 v3.2（终版框架）
 *
 * v3.2 变更：
 * 1. clearDecisionCache 加可见性打印（防反射静默失败导致三轮缓存复读）；
 * 2. 评测集新增可选字段：
 *    - expectTool: action=TOOL_CALL 时校验工具名（防"该调 query_ticket 却乱调"）；
 *    - withSuggestion: true 时模拟"已给过知识库建议+用户否定"的上下文；
 * 3. 夹具保真：CONFIRMING 状态还原真实对话历史（模型只能从历史看到工单，
 *    buildPrompt 不注入 title/description——这是项目的已知优化点）。
 *
 * 验证方法：跑一轮 = 20/25 题 × 3 轮，事后到百炼控制台核对 qwen-flash
 * 调用次数应 ≈ 题数×3（排除缓存复读）。
 * 可选对照：agentDecisionModel 的 temperature 0.3 → 0 再跑，观察漂移题是否收敛。
 */
@SpringBootTest
class AgentDecisionTest {

    @Autowired
    private AgentOrchestrator orchestrator;

    @Autowired
    private ObjectMapper objectMapper;

    private static final long FAKE_USER_ID = 999999L;
    private static final long FAKE_SESSION_ID = 888888L;
    private static final int ROUNDS = 3;

    record EvalItem(String state, String message, String expectAction,
                    String expectTarget, String expectTool, String originalMessage,
                    Boolean withSuggestion, String note) {}

    @SuppressWarnings("unchecked")
    @BeforeEach
    void clearDecisionCache() {
        Object cache = ReflectionTestUtils.getField(orchestrator, "decisionCache");
        // 可见性打印：NULL 说明反射没拿到字段，三轮将退化为缓存复读
        System.out.println("[Test] decisionCache = " + (cache != null ? "OK" : "NULL!!!"));
        if (cache instanceof Cache<?, ?> c) {
            ((Cache<Object, Object>) c).invalidateAll();
        }
    }

    @Test
    void decisionAccuracy() throws Exception {
        List<EvalItem> evalSet = objectMapper.readValue(
                new ClassPathResource("agent-eval.json").getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, EvalItem.class));

        List<String> roundSummaries = new ArrayList<>();
        List<Double> scores = new ArrayList<>();

        for (int round = 1; round <= ROUNDS; round++) {
            int pass = 0;
            StringBuilder detail = new StringBuilder();

            for (EvalItem item : evalSet) {
                String original = item.originalMessage() != null ? item.originalMessage() : item.message();
                DialogContext ctx = buildContext(item.state(), original,
                        Boolean.TRUE.equals(item.withSuggestion()));

                AgentDecision d;
                try {
                    d = orchestrator.decide(ctx, item.message(), item.state())
                            .block(Duration.ofSeconds(20));
                } catch (Exception e) {
                    detail.append(String.format("✗ [%s] %s → 异常: %s%n", item.state(), item.message(), e.getMessage()));
                    continue;
                }
                if (d == null) {
                    detail.append(String.format("✗ [%s] %s → 返回null%n", item.state(), item.message()));
                    continue;
                }

                boolean actionOk = item.expectAction().equals(d.action());
                boolean targetOk = item.expectTarget() == null
                        || item.expectTarget().equals(d.targetState());
                // 工具名校验：期望了工具名就必须命中（该查单就调 query_ticket，不许乱调）
                boolean toolOk = item.expectTool() == null
                        || (d.isToolCall() && item.expectTool().equals(d.toolName()));

                boolean ok = actionOk && targetOk && toolOk;
                if (ok) {
                    pass++;
                } else {
                    detail.append(String.format(
                            "✗ [%s] %s | 期望 %s→%s tool=%s | 实际 %s→%s tool=%s | %.50s...%n",
                            item.state(), item.message(), item.expectAction(), item.expectTarget(),
                            item.expectTool(), d.action(), d.targetState(), d.toolName(),
                            d.reasoning() != null ? d.reasoning() : ""));
                }
            }

            int n = evalSet.size();
            double pct = 100.0 * pass / n;
            scores.add(pct);
            roundSummaries.add(String.format("第%d轮: %d/%d = %.1f%%%n%s", round, pass, n, pct, detail));
            clearDecisionCache();
        }

        double avg = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double min = scores.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = scores.stream().mapToDouble(Double::doubleValue).max().orElse(0);

        StringBuilder out = new StringBuilder(String.format(
                "%n===== Agent 决策评测 v3.2（n=%d × %d 轮）=====%n平均: %.1f%%（波动 %.1f%%~%.1f%%）%n%n",
                evalSet.size(), ROUNDS, avg, min, max));
        roundSummaries.forEach(s -> out.append(s).append('\n'));
        System.out.println(out);
    }

    private DialogContext buildContext(String state, String originalMessage, boolean withSuggestion) {
        TicketCreateState stateObj = new TicketCreateState();
        stateObj.setStatus(state);
        stateObj.setSessionId(FAKE_SESSION_ID);
        stateObj.setOriginalMessage(originalMessage);
        stateObj.setVersion(0);

        // CONFIRMING：还原真实对话历史（模型从历史看到工单草稿；prompt 不注入 title/desc）
        if ("CONFIRMING".equals(state)) {
            stateObj.setTitle("宿舍水龙头漏水");
            stateObj.setDescription("宿舍水龙头持续滴水，无法关闭");
            stateObj.addHistory("user", "宿舍水龙头漏水");
            stateObj.addHistory("ai", "请描述一下具体现象～");
            stateObj.addHistory("user", "一直滴水，怎么关都关不上");
            stateObj.addHistory("ai",
                    "好的，请确认报修信息：【标题】宿舍水龙头漏水【描述】宿舍水龙头持续滴水，"
                            + "无法关闭，位于3号宿舍楼303。确认无误请回复'确认'，需要修改请直接告诉我。");
        }

        DialogContext ctx = new DialogContext();
        ctx.setUserId(FAKE_USER_ID);
        ctx.setSessionId(FAKE_SESSION_ID);
        ctx.setState(stateObj);

        // 模拟"已检索过知识库且用户否定建议"的上下文
        if (withSuggestion) {
            stateObj.setSuggestionContent("已给出知识库建议：先尝试关闭角阀并清洗滤网");
            ctx.setLastToolResult("【知识库】先关闭角阀止水，再清洗水龙头滤网");
        }
        return ctx;
    }
}