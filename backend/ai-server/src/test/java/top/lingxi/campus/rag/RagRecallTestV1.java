package top.lingxi.campus.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import top.lingxi.campus.domain.ai.dto.ChunkResponse;
import top.lingxi.campus.rag.file.service.impl.HydeSearchService;
import top.lingxi.campus.rag.service.IAstraSearchService;

import java.util.List;

/**
 * RAG 召回命中率评测（Recall@K）
 *
 * 用法：
 *  1. 在 src/test/resources/rag-eval.json 填好评测集（30 题，goldFile 必填，goldPage 可选）
 *  2. 把 LIBRARY_ID 改成你的知识库 ID（上传过文档、已有分片的那个）
 *  3. 运行：mvn test -Dtest=RagRecallTest
 *  4. 看控制台输出的汇总表，把结果发给你自己存档
 *
 * 判定规则：TopK 中任一结果的 source 以 goldFile 开头，且（若指定 goldPage）包含"第N页"，记为命中。
 */
@SpringBootTest
class RagRecallTest {

    @Autowired
    private IAstraSearchService searchService;

    @Autowired
    private HydeSearchService hydeSearchService;

    @Autowired
    private ObjectMapper objectMapper;
    
    private static final long LIBRARY_ID = 1L;

    private static final int CANDIDATE_K = 30;   // RRF 融合后的候选数（对齐 rrf-output-top-k=30）
    private static final int FINAL_K = 10;       // rerank 后的最终条数（对齐 rerank.top-k=10）

    record EvalItem(String question, String goldFile, Integer goldPage) {}

    @Test
    void recallEvaluation() throws Exception {
        List<EvalItem> evalSet = objectMapper.readValue(
                new ClassPathResource("rag-eval.json").getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, EvalItem.class));

        int hitHybrid10 = 0, hitHybrid30 = 0, hitRerank10 = 0, hitHyde10 = 0;
        StringBuilder detail = new StringBuilder();

        for (EvalItem item : evalSet) {
            // 1) 混合检索（RRF，未 rerank）
            List<ChunkResponse> hybrid = searchService.hybridSearch(LIBRARY_ID, item.question(), CANDIDATE_K);

            boolean h10 = hit(topN(hybrid, 10), item);
            boolean h30 = hit(hybrid, item);

            // 2) 混合检索 + rerank（真实链路）
            List<ChunkResponse> reranked = searchService.rerank(LIBRARY_ID, item.question(), hybrid, FINAL_K);
            boolean r10 = hit(reranked, item);

            // 3) 短问题走 HyDE 对照（问题<=10字才有意义，长问题这里仅作参考）
            List<ChunkResponse> hyde = hydeSearchService.hydeSearch(LIBRARY_ID, item.question(), FINAL_K);
            boolean y10 = hit(hyde, item);

            if (h10) hitHybrid10++;
            if (h30) hitHybrid30++;
            if (r10) hitRerank10++;
            if (y10) hitHyde10++;

            detail.append(String.format(
                    "Q: %s | hybrid@10:%s hybrid@30:%s rerank@10:%s hyde@10:%s%n",
                    item.question(), mark(h10), mark(h30), mark(r10), mark(y10)));
        }

        int n = evalSet.size();
        String summary = String.format(
                "%n===== RAG 召回评测（n=%d, 库ID=%d）=====%n" +
                "Recall@10  混合检索:  %d/%d = %.1f%%%n" +
                "Recall@30  混合检索:  %d/%d = %.1f%%%n" +
                "Recall@10  混合+rerank: %d/%d = %.1f%%%n" +
                "Recall@10  HyDE:      %d/%d = %.1f%%%n%n明细:%n%s",
                n, LIBRARY_ID,
                hitHybrid10, n, 100.0 * hitHybrid10 / n,
                hitHybrid30, n, 100.0 * hitHybrid30 / n,
                hitRerank10, n, 100.0 * hitRerank10 / n,
                hitHyde10, n, 100.0 * hitHyde10 / n,
                detail);

        System.out.println(summary);
        // 故意不 assert：这是评测工具，不是回归门槛。结果自己存档，对比调参前后。
    }

    private List<ChunkResponse> topN(List<ChunkResponse> list, int n) {
        return list.size() <= n ? list : list.subList(0, n);
    }

    private boolean hit(List<ChunkResponse> results, EvalItem item) {
        for (ChunkResponse c : results) {
            String src = c.getSource();
            if (src == null || !src.startsWith(item.goldFile())) continue;
            if (item.goldPage() != null && !src.contains("第" + item.goldPage() + "页")) continue;
            return true;
        }
        return false;
    }

    private String mark(boolean hit) {
        return hit ? "✓" : "✗";
    }
}