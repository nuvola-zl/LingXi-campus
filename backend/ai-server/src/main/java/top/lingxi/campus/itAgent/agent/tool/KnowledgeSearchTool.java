package top.lingxi.campus.itAgent.agent.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.lingxi.campus.itAgent.agent.search.HydeSearchService;
import top.lingxi.campus.rag.service.IAstraSearchService;
import top.lingxi.campus.domain.ai.dto.ChunkResponse;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeSearchTool implements AgentTool {

    private final IAstraSearchService astraSearchService;
    private final HydeSearchService hydeSearchService;

    /* 相关性分数阈值，低于此值的检索结果视为不相关 */
    //private static final float RELEVANCE_THRESHOLD = 0.3f;


    // ==================== 修复：语义相似度阈值 ====================
    /** 向量语义相似度阈值，低于此值视为不相关 */
    private static final float VECTOR_SIM_THRESHOLD = 0.70f;
    /** BM25 文本匹配阈值（可选，作为兜底） */
    private static final float BM25_SCORE_THRESHOLD = 1.0f;
    // ===========================================================

    @Override
    public String name() {
        return "search_knowledge";
    }

    @Override
    public String description() {
        return "查询IT知识库，获取故障排查方案。参数: query(查询内容), libraryId(知识库ID,默认1), topK(返回条数,默认3)";
    }

    @Override
    public String execute(Map<String, Object> args) {
        String query = (String) args.get("query");
        Long libraryId = args.get("libraryId") != null
                ? Long.valueOf(args.get("libraryId").toString()) : 1L;
        int topK = args.get("topK") != null
                ? Integer.parseInt(args.get("topK").toString()) : 3;

        try {
            List<ChunkResponse> chunks;

            if (query != null && query.length() < 10) {
                log.info("[KnowledgeTool] 短query({}字)，启用HyDE检索: {}", query.length(), query);
                chunks = hydeSearchService.hydeSearch(libraryId, query, topK);
            } else {
                log.info("[KnowledgeTool] 长query({}字)，走标准混合检索",
                        query != null ? query.length() : 0);
                chunks = astraSearchService.hybridSearch(libraryId, query, topK);
            }

            // ==================== 修改 1：空结果加标记 ====================
            if (chunks == null || chunks.isEmpty()) {
                return "【KNOWLEDGE_EMPTY】未找到相关知识库内容，建议创建工单由工程师处理。";
            }

            boolean hasRelevant = chunks.stream()
                    .anyMatch(c ->
                            (c.getVectorScore() != null && c.getVectorScore() >= VECTOR_SIM_THRESHOLD) ||
                                    (c.getBm25Score() != null && c.getBm25Score() >= BM25_SCORE_THRESHOLD)
                    );

            if (!hasRelevant) {
                float maxVector = chunks.stream()
                        .map(c -> c.getVectorScore() != null ? c.getVectorScore() : 0f)
                        .max(Float::compare).orElse(0f);
                float maxBm25 = chunks.stream()
                        .map(c -> c.getBm25Score() != null ? c.getBm25Score() : 0f)
                        .max(Float::compare).orElse(0f);

                log.info("[KnowledgeTool] 所有结果相关度低于阈值, 最高vectorScore={}, 最高bm25Score={}, query={}",
                        maxVector, maxBm25, query);
                return String.format(
                        "【KNOWLEDGE_EMPTY】未找到相关知识库内容（最高语义相似度 %.0f%%，低于 %.0f%% 阈值），建议创建工单由工程师处理。",
                        maxVector * 100, VECTOR_SIM_THRESHOLD * 100
                );
            }

            return chunks.stream()
                    .map(c -> {
                        float displayScore = c.getVectorScore() != null ? c.getVectorScore() : 0f;
                        return "【来源】" + (c.getSource() != null ? c.getSource() : "未知")
                                + "（语义相关度 " + String.format("%.0f%%", displayScore * 100) + "）\n"
                                + c.getContent();
                    })
                    .collect(Collectors.joining("\n\n---\n\n"));
        } catch (Exception e) {
            log.error("知识库检索工具执行失败", e);
            return "知识库检索失败: " + e.getMessage();
        }
    }
}