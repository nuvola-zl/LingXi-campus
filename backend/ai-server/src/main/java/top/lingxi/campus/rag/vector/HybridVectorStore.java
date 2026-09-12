package top.lingxi.campus.rag.vector;

import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import top.lingxi.campus.rag.ingestion.IngestionException;
import top.lingxi.campus.rag.ingestion.IngestionProgressCallback;
import top.lingxi.campus.domain.ai.entity.KbChunk;
import top.lingxi.campus.domain.ai.mapper.KbChunkMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 向量存储封装
 * <p>
 * 封装 KbChunkMapper，提供向量存储能力（不实现 VectorStore 接口以兼容 Spring AI 1.1.2）
 * </p>
 *
 * Phase 2 变更：
 * 1. 新增 addChunks(List&lt;KbChunk&gt;, IngestionProgressCallback)：
 *    入库链路直接传入组装好的 KbChunk（含 content_hash），不再绕 Document
 * 2. 修复静默失败：embedding 失败由"返回空列表"改为抛 IngestionException，
 *    让消费侧重试/DLQ 机制接住（原行为会把 media 标记为 PARSED 但库里无数据）
 * 3. 按批次上报入库进度（原前端进度条只有 0%/100% 两档）
 */
@Slf4j
@RequiredArgsConstructor
public class HybridVectorStore {

    private final KbChunkMapper chunkMapper;
    private final DashScopeEmbeddingModel embeddingModel;

    private static final int BATCH_SIZE = 100;

    /**
     * 入库链路主入口：批量 embedding + 分批落库
     *
     * @param chunks   已组装好元数据/content_hash 的分片
     * @param progress 进度回调（可为 IngestionProgressCallback.NOOP）
     * @return 写入成功的分片（embedding 字段已回填）
     * @throws IngestionException embedding 或写库失败
     */
    public List<KbChunk> addChunks(List<KbChunk> chunks, IngestionProgressCallback progress) {
        if (chunks == null || chunks.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. 批量生成 embedding（单批调用，失败抛异常交给重试/DLQ）
        List<String> contents = chunks.stream()
                .map(KbChunk::getContent)
                .collect(Collectors.toList());

        List<float[]> embeddings;
        try {
            embeddings = embeddingModel.embed(contents);
        } catch (Exception e) {
            // 修复点：原实现 catch 后返回空列表，导致"假成功"。此处必须抛出。
            log.error("生成 embedding 失败, chunks={}", chunks.size(), e);
            throw new IngestionException("生成 embedding 失败", e);
        }

        // 2. 回填向量
        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).setEmbedding(embeddings.get(i));
        }

        // 3. 分批写入 + 进度上报
        IngestionProgressCallback callback = progress != null ? progress : IngestionProgressCallback.NOOP;
        for (int i = 0; i < chunks.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, chunks.size());
            try {
                chunkMapper.batchInsert(chunks.subList(i, end));
            } catch (Exception e) {
                log.error("分片批量写入失败, range=[{}, {})", i, end, e);
                throw new IngestionException("分片批量写入失败", e);
            }
            callback.onProgress(end, chunks.size());
        }

        log.info("VectorStore 入库分片: {} 条", chunks.size());
        return chunks;
    }

    /**
     * 兼容旧入口：Document 列表入库（非入库链路场景使用）
     */
    public List<KbChunk> add(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return new ArrayList<>();
        }

        List<KbChunk> chunks = documents.stream()
                .map(this::toKbChunk)
                .collect(Collectors.toList());

        return addChunks(chunks, IngestionProgressCallback.NOOP);
    }

    /**
     * 删除分片
     */
    public void delete(List<String> ids) {
        for (String id : ids) {
            try {
                Long chunkId = extractChunkId(id);
                if (chunkId != null) {
                    chunkMapper.deleteById(chunkId);
                }
            } catch (Exception e) {
                log.warn("删除文档失败: id={}", id, e);
            }
        }
        log.info("VectorStore 删除文档: {} 条", ids.size());
    }

    /**
     * 获取原生客户端
     */
    public KbChunkMapper getNativeClient() {
        return chunkMapper;
    }

    /**
     * Document 转换为 KbChunk（仅旧 add 入口使用）
     */
    private KbChunk toKbChunk(Document doc) {
        java.util.Map<String, Object> metadata = new java.util.HashMap<>(doc.getMetadata());
        Long libraryId = extractLong(metadata.remove("libraryId"));
        Long mediaId = extractLong(metadata.remove("mediaId"));
        Integer chunkIndex = extractInt(metadata.remove("chunkIndex"));

        return KbChunk.builder()
                .libraryId(libraryId)
                .mediaId(mediaId)
                .content(doc.getText())
                .chunkIndex(chunkIndex)
                .metadata(metadata)
                .build();
    }

    /**
     * 从 ID 中提取 KbChunk ID
     */
    private Long extractChunkId(String id) {
        if (id == null) {
            return null;
        }
        if (id.startsWith("kbchunk_")) {
            return Long.parseLong(id.substring("kbchunk_".length()));
        }
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long extractLong(Object value) {
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer extractInt(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}