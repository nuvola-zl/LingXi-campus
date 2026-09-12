package top.lingxi.campus.itAgent.agent.search;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import top.lingxi.campus.rag.service.IAstraSearchService;
import top.lingxi.campus.domain.ai.dto.ChunkResponse;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HydeSearchService {

    private final DashScopeChatModel chatModel;
    private final IAstraSearchService astraSearchService;

    private static final String HYDE_PROMPT = """
        用户问题：%s
        
        请根据这个问题，生成一段可能的知识库文档内容（100字左右），包含：
        - 问题现象描述
        - 可能的原因
        - 排查/解决步骤
        
        要求：直接输出文档内容，不要解释，不要markdown，不要"以下是"等开场白。
        """;

    /**
     * HyDE 混合检索
     * 1. LLM 生成假设文档
     * 2. 对假设文档走标准混合检索（复用现有 RAG 链路）
     * 
     * 注意：这里会走一次 QueryRewrite，但假设文档语义丰富，改写影响很小。
     * 后续如性能吃紧，可在 AstraSearchServiceImpl 里加一个跳过 QueryRewrite 的 direct 方法。
     */
    public List<ChunkResponse> hydeSearch(Long libraryId, String query, int topK) {
        String hypotheticalDoc = generateHypotheticalDocument(query);
        log.info("[HyDE] 假设文档: {}", hypotheticalDoc);

        List<ChunkResponse> chunks = astraSearchService.hybridSearch(libraryId, hypotheticalDoc, topK);
        
        log.info("[HyDE] 检索结果: {} 条", chunks != null ? chunks.size() : 0);
        return chunks;
    }

    private String generateHypotheticalDocument(String query) {
        try {
            String promptText = String.format(HYDE_PROMPT, query);
            Prompt prompt = new Prompt(promptText);
            ChatResponse response = chatModel.call(prompt);
            return response.getResult().getOutput().getText().trim();
        } catch (Exception e) {
            log.error("[HyDE] 生成假设文档失败，降级为原始查询", e);
            return query;
        }
    }
}