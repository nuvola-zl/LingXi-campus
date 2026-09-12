package top.lingxi.campus.rag.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.lingxi.campus.domain.ai.dto.ChunkResponse;
import top.lingxi.campus.infra.config.AstraProperties;
import top.lingxi.campus.infra.config.ModelProperties;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DashScope Rerank 客户端
 *
 * Phase 3 抽取说明：原散落在 AstraSearchServiceImpl 中的约 200 行
 * （callDashscopeRerank / callDashscopeApi / parseRerankResult + 静态 HttpClient）
 * 收敛为本组件：
 * 1. 序列化统一使用 Spring 容器中的单例 ObjectMapper（原每次调用 new）
 * 2. HttpClient 静态复用（线程安全）
 * 3. 失败兜底语义不变：禁用/异常时退回原始顺序并截断 topK
 *
 * API: POST https://dashscope.aliyuncs.com/compatible-api/v1/reranks
 * 模型: qwen3-rerank（配置键 astra.rerank.model）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RerankClient {

    private static final String BASE_URL = "https://dashscope.aliyuncs.com";
    private static final String RERANK_ENDPOINT = "/compatible-api/v1/reranks";
    private static final String RERANK_INSTRUCT =
            "Given a web search query, retrieve relevant passages that answer the query.";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ModelProperties modelProperties;
    private final AstraProperties astraProperties;
    private final ObjectMapper objectMapper;

    /**
     * 对候选 chunk 重排序
     *
     * @param query  用户问题（用原始 query 而非改写后，保证精确名词不被改写破坏）
     * @param chunks 候选分片
     * @param topK   返回数量上限
     * @return 按 relevance_score 降序的结果；rerank 禁用或调用失败时退回原始顺序
     */
    public List<ChunkResponse> rerank(String query, List<ChunkResponse> chunks, int topK) {
        if (!astraProperties.getRerank().isEnabled()) {
            log.debug("ReRank已禁用，返回原始顺序");
            return truncate(chunks, topK);
        }
        if (chunks == null || chunks.isEmpty()) {
            return chunks;
        }

        log.debug("ReRank重排序: query={}, chunks={}, topK={}", query, chunks.size(), topK);
        try {
            String responseBody = callRerankApi(query, chunks);
            List<ChunkResponse> reranked = parseRerankResult(responseBody, chunks);
            return truncate(reranked, topK);
        } catch (Exception e) {
            log.error("ReRank重排序失败，退回原始顺序", e);
            return truncate(chunks, topK);
        }
    }

    private List<ChunkResponse> truncate(List<ChunkResponse> chunks, int topK) {
        if (chunks == null) {
            return null;
        }
        return chunks.subList(0, Math.min(chunks.size(), topK));
    }

    /**
     * 调用 qwen3-rerank API
     */
    private String callRerankApi(String query, List<ChunkResponse> chunks) throws Exception {
        List<String> documents = chunks.stream()
                .map(chunk -> chunk.getContent() != null ? chunk.getContent() : "")
                .collect(Collectors.toList());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", astraProperties.getRerank().getModel());
        requestBody.put("query", query);
        requestBody.put("documents", documents);
        requestBody.put("top_n", astraProperties.getRerank().getTopK());
        requestBody.put("instruct", RERANK_INSTRUCT);

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + RERANK_ENDPOINT))
                .header("Authorization", "Bearer " + modelProperties.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        long start = System.currentTimeMillis();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("【RERANK-HTTP】响应 | status={}, cost={}ms",
                response.statusCode(), System.currentTimeMillis() - start);

        if (response.statusCode() != 200) {
            log.error("【RERANK-HTTP】非200响应 | body={}", response.body());
            throw new IllegalStateException(
                    "DashScope rerank API 返回错误: " + response.statusCode() + " - " + response.body());
        }
        return response.body();
    }

    /**
     * 解析响应，兼容两种格式：
     * 格式A（旧版/某些模型）: {"output": {"results": [...]}}
     * 格式B（qwen3-rerank）: {"results": [...]}
     */
    @SuppressWarnings("unchecked")
    private List<ChunkResponse> parseRerankResult(String responseJson, List<ChunkResponse> chunks) throws Exception {
        Map<String, Object> response = objectMapper.readValue(responseJson,
                new TypeReference<Map<String, Object>>() {
                });

        List<Map<String, Object>> results;
        Map<String, Object> output = (Map<String, Object>) response.get("output");
        if (output != null) {
            results = (List<Map<String, Object>>) output.get("results");
        } else {
            results = (List<Map<String, Object>>) response.get("results");
        }

        if (results == null || results.isEmpty()) {
            log.warn("【RERANK-PARSE】无 results | 完整响应={}", responseJson);
            return chunks;
        }

        // index -> relevance_score
        Map<Integer, Float> scoreMap = new HashMap<>();
        for (Map<String, Object> result : results) {
            int index = ((Number) result.get("index")).intValue();
            double score = ((Number) result.get("relevance_score")).doubleValue();
            scoreMap.put(index, (float) score);
        }

        for (int i = 0; i < chunks.size(); i++) {
            Float score = scoreMap.get(i);
            if (score != null) {
                chunks.get(i).setScore(score);
            }
        }

        return chunks.stream()
                .sorted(Comparator.comparing(
                        c -> c.getScore() != null ? c.getScore() : 0f, Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }
}