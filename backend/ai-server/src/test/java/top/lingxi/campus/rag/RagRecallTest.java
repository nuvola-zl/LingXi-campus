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
 * RAG 召回质量评测 v2
 *
 * v2 变更：
 * 1. 正样本看"排名质量"：MRR / Hit@1 / Hit@3（小语料下 Recall@10 必然虚高，排名才有区分度）
 * 2. 新增负样本（goldFile 为 null 的题）：不判对错，只打印 top1 的 source 和向量分数，
 *    供人工判断"系统会不会把不相干内容当答案"（你代码里的 KNOWLEDGE_EMPTY 阈值设计）
 *
 * 评测集：src/test/resources/rag-eval.json
 *   正样本: { "question": "...", "goldFile": "答案所在文件名.md" }
 *   负样本: { "question": "...", "goldFile": null }   ← 文档库中不存在答案的问题
 * 运行：mvn test -Dtest=RagRecallTest
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

    private static final int CANDIDATE_K = 30;
    private static final int FINAL_K = 10;

    record EvalItem(String question, String goldFile, Integer goldPage) {}

    @Test
    void recallEvaluation() throws Exception {
        List<EvalItem> evalSet = objectMapper.readValue(
                new ClassPathResource("rag-eval.json").getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, EvalItem.class));

        int n = 0;
        int hit1 = 0, hit3 = 0, hit10 = 0;
        double mrr = 0;
        StringBuilder detail = new StringBuilder();
        StringBuilder negative = new StringBuilder();

        for (EvalItem item : evalSet) {
            List<ChunkResponse> hybrid = searchService.hybridSearch(LIBRARY_ID, item.question(), CANDIDATE_K);
            List<ChunkResponse> reranked = searchService.rerank(LIBRARY_ID, item.question(), hybrid, FINAL_K);

            // ===== 负样本：只记录，不判分 =====
            if (item.goldFile() == null) {
                ChunkResponse top1 = hybrid.isEmpty() ? null : hybrid.get(0);
                negative.append(String.format(
                        "负样本 Q: %s | 库中无答案 | top1=%s (vectorScore=%.3f)%n",
                        item.question(),
                        top1 != null ? top1.getSource() : "空结果",
                        top1 != null && top1.getVectorScore() != null ? top1.getVectorScore() : -1));
                continue;
            }

            // ===== 正样本：看金标准分片的排名 =====
            n++;
            int rankInRerank = rankOf(reranked, item);
            if (rankInRerank == 1) hit1++;
            if (rankInRerank > 0 && rankInRerank <= 3) hit3++;
            if (rankInRerank > 0 && rankInRerank <= 10) hit10++;
            if (rankInRerank > 0) mrr += 1.0 / rankInRerank;

            detail.append(String.format(
                    "Q: %s | 金标准排名=%s%n",
                    item.question(), rankInRerank > 0 ? ("第" + rankInRerank + "名") : "未命中"));
        }

        String summary = String.format(
                "%n===== RAG 召回评测 v2（正样本 n=%d, 库ID=%d）=====%n" +
                "Hit@1  = %d/%d = %.1f%%%n" +
                "Hit@3  = %d/%d = %.1f%%%n" +
                "Hit@10 = %d/%d = %.1f%%%n" +
                "MRR    = %.3f%n%n排名明细:%n%s%n负样本观察（库中无答案，理想情况是低分或空）:%n%s",
                n, LIBRARY_ID,
                hit1, n, 100.0 * hit1 / n,
                hit3, n, 100.0 * hit3 / n,
                hit10, n, 100.0 * hit10 / n,
                n > 0 ? mrr / n : 0,
                detail, negative);

        System.out.println(summary);
    }

    /** 金标准分片在结果列表中的名次（1 起），未命中返回 -1 */
    private int rankOf(List<ChunkResponse> results, EvalItem item) {
        for (int i = 0; i < results.size(); i++) {
            String src = results.get(i).getSource();
            if (src == null || !src.startsWith(item.goldFile())) continue;
            if (item.goldPage() != null && !src.contains("第" + item.goldPage() + "页")) continue;
            return i + 1;
        }
        return -1;
    }
}