package top.lingxi.campus.itAgent.agent.service;

import org.springframework.stereotype.Component;
import top.lingxi.campus.itAgent.state.TicketCreateState;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

/**
 * 进 CONFIRMING 前的数据准备（v3修复：任何路径进入都必须有完整数据）
 * 原逻辑在 CollectingState 私有方法里，规则11开了 DIAGNOSING→CONFIRMING 直通后，
 * 该路径拿不到数据导致建单NPE，故抽取为公共组件
 */
@Component
public class ConfirmDataPreparer {

   /**
     * 若数据缺失则补齐（已存在则不覆盖，防止 COLLECTING 正常路径的丰富描述被简化版覆盖）
     */
    /** titleFromLlm：LLM 在决策里给的标题，优先采用；没有则代码截取兜底 */
    public void prepareIfAbsent(TicketCreateState state, String userMessage, String titleFromLlm) {
        if (state.getDescription() == null || state.getDescription().isBlank()) {
            state.setDescription(buildEnrichedDescription(state, userMessage));
        }
        if (state.getTitle() == null || state.getTitle().isBlank()) {
            // [v3] 优先用 LLM 生成的标题（它有完整上下文，概括质量高于机械截取）
            if (titleFromLlm != null && !titleFromLlm.isBlank()) {
                state.setTitle(titleFromLlm.trim().length() > 30
                        ? titleFromLlm.trim().substring(0, 30)
                        : titleFromLlm.trim());
            } else {
                String titleSource = pickProblemLikeText(userMessage, state.getOriginalMessage());
                state.setTitle(extractTitle(titleSource));
            }
        }
        if (state.getPriority() == null) {
            state.setPriority(isUrgent(userMessage, state.getOriginalMessage()) ? 1 : 2);
        }
    }

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

    /**
     * 紧急度判定：关键词命中即紧急（确定性规则，不依赖 LLM 输出）
     */
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

    /**
     * 从候选文本里挑最像"问题描述"的一句（含地点/现象，且不是查询/确认类话语）
     */
    private static final String[] FAULT_KEYWORDS =
            {"坏", "漏", "断", "不亮", "打不开", "连不上", "无法", "异常", "故障",
                    "闪", "停", "滴水", "不制冷", "不出水", "不响", "急"};

    private String pickProblemLikeText(String... candidates) {
        String best = null;
        int bestScore = 0;
        for (String c : candidates) {
            if (c == null || c.isBlank()) continue;
            String t = c.trim();
            if (t.length() < 4) continue;
            if (t.matches(".*(查询|查一下|进度|状态|保修单|报修单|工单|确认|取消|你是谁|谢谢|描述过).*$")) continue;
            int score = 0;
            for (String kw : FAULT_KEYWORDS) if (t.contains(kw)) score++;
            if (score > bestScore) { best = t; bestScore = score; }   // 故障词多者胜
        }
        return best != null ? best : "报修";
    }
}