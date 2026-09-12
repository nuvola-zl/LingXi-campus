package top.lingxi.campus.rag.Feedback;

import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.lingxi.campus.itAgent.Event.TicketClosedEvent;
import top.lingxi.campus.domain.ai.entity.KbChunk;
import top.lingxi.campus.domain.ai.entity.KbLibrary;
import top.lingxi.campus.domain.ai.mapper.KbChunkMapper;
import top.lingxi.campus.domain.ai.mapper.KbLibraryMapper;
import top.lingxi.campus.domain.biz.ticket.entity.BizTicket;
import top.lingxi.campus.domain.biz.ticket.entity.BizTicketComment;
import top.lingxi.campus.domain.biz.ticket.mapper.BizTicketCommentMapper;
import top.lingxi.campus.domain.biz.ticket.mapper.BizTicketMapper;
import top.lingxi.campus.infra.config.FeedbackProperties;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工单知识沉淀服务
 *
 * Phase 3 变更：feedbackEnabled 由 @Value 注入改为 FeedbackProperties
 * （与检索侧开关同源，配置键不变：feedback.enabled）。
 * 默认 false：工单关闭事件触发后直接跳过，对业务零影响。
 *
 * TODO(业务下线后清理): 确认废弃后可整体删除本类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketKnowledgeFeedbackService {

    private final BizTicketMapper ticketMapper;
    private final BizTicketCommentMapper commentMapper;
    private final ChatModel textChatModel;
    private final KbLibraryMapper libraryMapper;
    private final KbChunkMapper chunkMapper;
    private final DashScopeEmbeddingModel embeddingModel;
    private final FeedbackProperties feedbackProperties;

    @Async
    @EventListener
    // 注意：外层不加 @Transactional，把事务下沉到写库方法
    public void onTicketClosed(TicketClosedEvent event) {
        if (!feedbackProperties.isEnabled()) {
            log.debug("正反馈已关闭，跳过");
            return;
        }

        Long ticketId = event.getTicketId();
        log.info("开始工单知识沉淀: ticketId={}", ticketId);

        try {
            BizTicket ticket = ticketMapper.selectById(ticketId);
            if (ticket == null || ticket.getStatus() != 4) {
                log.warn("工单不存在或未关闭: ticketId={}", ticketId);
                return;
            }

            List<BizTicketComment> comments = commentMapper.selectByTicketId(ticketId);
            List<String> processRecords = comments.stream()
                    .filter(c -> "COMMENT".equals(c.getType()))
                    .map(BizTicketComment::getContent)
                    .collect(Collectors.toList());

            if (processRecords.isEmpty()) {
                log.info("工单无有效处理记录，跳过沉淀: ticketId={}", ticketId);
                return;
            }

            String rawRecord = buildRawRecord(ticket, processRecords);
            String summary = generateSummary(rawRecord);

            if (!isQualified(summary, processRecords)) {
                log.info("工单总结质量不达标，跳过沉淀: ticketId={}", ticketId);
                return;
            }

            // ========== 关键：进反馈库，绝不污染官方库 ==========
            saveToFeedbackLibrary(ticket, summary);
            log.info("工单知识沉淀完成: ticketId={}", ticketId);

        } catch (Exception e) {
            log.error("工单知识沉淀失败: ticketId={}", ticketId, e);
        }
    }

    // ========== 核心改造：反馈库隔离 ==========
    private void saveToFeedbackLibrary(BizTicket ticket, String summary) {
        String domain = mapCategoryToDomain(ticket.getCategoryId());
        String feedbackLibName = domain + "_feedback";

        KbLibrary feedbackLib = libraryMapper.selectByName(feedbackLibName);
        if (feedbackLib == null) {
            log.warn("反馈知识库不存在，跳过: {}", feedbackLibName);
            return;
        }

        String title = extractTitle(summary);
        String content = summary;

        // 只在 feedback 库内查重
        DuplicateCheckResult duplicate = checkDuplicateByVector(feedbackLib.getId(), content);
        double similarity = duplicate.getSimilarity();

        if (similarity > 0.97) {
            log.info("反馈文档几乎相同，跳过: title={}, sim={}", title, String.format("%.2f", similarity));
            return;
        }

        if (similarity > 0.70 && duplicate.getExistingChunkId() != null) {
            KbChunk existing = chunkMapper.selectById(duplicate.getExistingChunkId());
            String decision = compareSolutions(existing.getContent(), content);

            switch (decision) {
                case "DIFFERENT_SOLUTION" -> {
                    insertFeedbackChunk(feedbackLib.getId(), title, content, ticket);
                    log.info("反馈库新增不同方案: title={}, sim={}", title, String.format("%.2f", similarity));
                }
                case "BETTER_VERSION" -> {
                    updateChunk(existing, content);
                    log.info("反馈库更新更优方案: title={}, sim={}", title, String.format("%.2f", similarity));
                }
                default -> {
                    log.info("反馈文档本质相同，跳过: title={}, sim={}", title, String.format("%.2f", similarity));
                }
            }
            return;
        }

        insertFeedbackChunk(feedbackLib.getId(), title, content, ticket);
        log.info("反馈库新文档入库: title={}, sim={}", title, String.format("%.2f", similarity));
    }

    // ========== 关键：反馈 chunk 的标记 ==========
    @Transactional  // 事务只包写库
    public void insertFeedbackChunk(Long libraryId, String title, String content, BizTicket ticket) {
        float[] embedding = embeddingModel.embed(List.of(content)).get(0);

        KbChunk chunk = new KbChunk();
        chunk.setLibraryId(libraryId);
        chunk.setMediaId(0L);  // ← 0 表示"AI生成，无源文件"
        chunk.setContent(content);
        chunk.setEmbedding(embedding);
        chunk.setChunkIndex(0);
        chunk.setMetadata(Map.of(
                "title", title,
                "source", "ticket_feedback",
                "knowledgeType", "FEEDBACK",      // ← 关键标记
                "trustScore", "30",                // ← 初始信任分
                "ticketId", String.valueOf(ticket.getId()),
                "category", mapCategoryToDomain(ticket.getCategoryId()),
                "createdAt", LocalDateTime.now().toString()
        ));
        chunkMapper.insert(chunk);
    }

    // ==================== 以下业务逻辑保持不变 ====================

    private String buildRawRecord(BizTicket ticket, List<String> records) {
        StringBuilder sb = new StringBuilder();
        sb.append("工单标题：").append(ticket.getTitle()).append("\n");
        sb.append("问题描述：").append(ticket.getDescription()).append("\n");
        sb.append("处理记录：\n");
        for (int i = 0; i < records.size(); i++) {
            sb.append(i + 1).append(". ").append(records.get(i)).append("\n");
        }
        return sb.toString();
    }

    private String generateSummary(String rawRecord) {
        String systemPrompt = """
        你是一个企业 IT 运维知识库整理专家。请根据以下工单处理记录，提炼成一篇标准解决方案文档。

        【硬性规则 - 必须遵守】
        1. 如果处理记录只有"重启"、"好了"、"解决了"等临时恢复操作，没有具体排查或修复步骤，必须只返回：UNQUALIFIED
        2. 如果完全没有步骤（只有问题描述），必须只返回：UNQUALIFIED

        【输出要求】
        1. 解决步骤必须 >= 2 条
        2. 每条步骤 15~40 字，包含：操作对象 + 具体操作 + 预期结果
        3. 总字数 80~250 字（太短=不详细，太长=啰嗦）
        4. 问题现象 20~50 字

        【输出格式】
        标题：《问题关键词》解决方案
        问题现象：...
        解决步骤：
        1. ...
        2. ...
        3. ...

        请直接输出文档内容，不要解释。
        """;

        String fullPrompt = systemPrompt + "\n\n" + rawRecord;

        try {
            var response = textChatModel.call(new Prompt(fullPrompt));
            return response.getResult().getOutput().getText().trim();
        } catch (Exception e) {
            log.error("LLM 总结失败", e);
            return "UNQUALIFIED";
        }
    }

    private boolean isQualified(String summary, List<String> rawComments) {
        if (summary == null || summary.isBlank()) return false;
        if (summary.contains("UNQUALIFIED")) return false;

        int stepCount = countSteps(summary);
        int length = summary.length();

        if (stepCount >= 3) return true;
        if (stepCount == 2 && length >= 60) return true;
        return false;
    }

    private int countSteps(String summary) {
        int count = 0;
        for (String line : summary.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.matches("^\\d+[.、)）\\s]+.+")) {
                count++;
            }
        }
        return count;
    }

    private DuplicateCheckResult checkDuplicateByVector(Long libraryId, String newContent) {
        try {
            float[] newEmbedding = embeddingModel.embed(List.of(newContent)).get(0);
            List<KbChunk> existingChunks = chunkMapper.selectByLibraryIdAndSource(libraryId, "ticket_feedback");

            if (existingChunks.isEmpty()) {
                return new DuplicateCheckResult(false, null, 0.0);
            }

            KbChunk mostSimilar = null;
            double maxSimilarity = 0.0;

            for (KbChunk chunk : existingChunks) {
                if (chunk.getEmbedding() == null) continue;
                double similarity = cosineSimilarity(newEmbedding, chunk.getEmbedding());
                if (similarity > maxSimilarity) {
                    maxSimilarity = similarity;
                    mostSimilar = chunk;
                }
            }

            return new DuplicateCheckResult(maxSimilarity > 0.70,
                    mostSimilar != null ? mostSimilar.getId() : null, maxSimilarity);

        } catch (Exception e) {
            log.error("向量查重失败", e);
            return new DuplicateCheckResult(false, null, 0.0);
        }
    }

    private String compareSolutions(String existingContent, String newContent) {
        String prompt = """
        你是一位知识库管理员。请比较以下两篇技术文档，判断它们的关系。

        【已有文档】
        %s

        【新文档】
        %s

        请严格按以下规则判断，只返回三个关键词之一：
        1. DIFFERENT_SOLUTION：同一问题的不同解决方式
        2. BETTER_VERSION：新文档是已有文档的更详细/更完整版本
        3. SAME：两篇文档本质相同

        只返回 DIFFERENT_SOLUTION / BETTER_VERSION / SAME，不要解释。
        """.formatted(
                existingContent.substring(0, Math.min(500, existingContent.length())),
                newContent.substring(0, Math.min(500, newContent.length()))
        );

        try {
            var response = textChatModel.call(new Prompt(prompt));
            String result = response.getResult().getOutput().getText().trim().toUpperCase();
            if (result.contains("DIFFERENT")) return "DIFFERENT_SOLUTION";
            if (result.contains("BETTER")) return "BETTER_VERSION";
            return "SAME";
        } catch (Exception e) {
            log.error("LLM 比较失败，默认保守处理: {}", e.getMessage());
            return "SAME";
        }
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) throw new IllegalArgumentException("向量维度不一致");
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    @Transactional
    protected void updateChunk(KbChunk existing, String newContent) {
        float[] newEmbedding = embeddingModel.embed(List.of(newContent)).get(0);
        existing.setContent(newContent);
        existing.setEmbedding(newEmbedding);
        Map<String, Object> metadata = existing.getMetadata();
        if (metadata != null) {
            metadata.put("updatedAt", LocalDateTime.now().toString());
        }
        chunkMapper.updateById(existing);
    }

    private String mapCategoryToDomain(Long categoryId) {
        if (categoryId == null) return "IT";
        return switch (categoryId.intValue()) {
            case 2 -> "HR";
            case 3 -> "行政";
            default -> "IT";
        };
    }

    private String extractTitle(String summary) {
        String[] lines = summary.split("\n");
        for (String line : lines) {
            String t = line.trim();
            if (t.startsWith("《") && t.contains("》")) {
                return t.substring(t.indexOf("《") + 1, t.indexOf("》"));
            }
        }
        return summary.length() > 20 ? summary.substring(0, 20) + "..." : summary;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class DuplicateCheckResult {
        private final boolean duplicate;
        private final Long existingChunkId;
        private final double similarity;
    }
}