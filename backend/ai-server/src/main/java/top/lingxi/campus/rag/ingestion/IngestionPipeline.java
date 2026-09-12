package top.lingxi.campus.rag.ingestion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.lingxi.campus.domain.ai.dto.ChunkResponse;
import top.lingxi.campus.domain.ai.dto.ParseMessage;
import top.lingxi.campus.domain.ai.entity.KbChunk;
import top.lingxi.campus.domain.ai.entity.KbMedia;
import top.lingxi.campus.domain.ai.mapper.KbMediaMapper;
import top.lingxi.campus.infra.oss.AliOssUtil;
import top.lingxi.campus.rag.parse.ChunkingService;
import top.lingxi.campus.rag.vector.HybridVectorStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文档入库流水线（离线链路唯一编排者）
 *
 * Phase 2 重构说明：原 4 个 Parser 实现类（PDF/TXT/Word/MD）各自重复
 * "下载 → 提取 → 切分 → 拼元数据 → embedding → 入库" 全流程，近 90% 代码相同。
 * 本类将流程收敛为单一编排，文件类型差异只保留在 DocumentTextExtractor
 * 的"字节 → 文本片段"一步（策略模式）。
 *
 * 流程：
 *   下载 → 结构化提取（保留页码等）→ 切分 → 元数据 + content_hash →
 *   批量 embedding + 入库（失败抛 IngestionException，由 MQ 重试/DLQ 接住）
 *
 * 修复的存量问题：
 * 1. embedding 失败静默（原 VectorStore 返回空列表，media 假 PARSED）→ 抛异常走重试
 * 2. 进度只有 0%/100% → 按入库批次真实上报
 * 3. content_hash 落库：增量更新（文档修改只重建变更分片）的地基
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionPipeline {

    private final KbMediaMapper mediaMapper;
    private final AliOssUtil aliOssUtil;
    private final TextExtractorFactory extractorFactory;
    private final ChunkingService chunkingService;
    private final HybridVectorStore vectorStore;

    /**
     * 执行入库
     *
     * @param message 解析任务消息
     * @return 入库的分片响应列表（消费侧用于统计 totalChunks）
     * @throws IngestionException 任一步失败都抛出，交由 MQ 重试/DLQ 机制
     */
    public List<ChunkResponse> ingest(ParseMessage message) {
        return ingest(message, IngestionProgressCallback.NOOP);
    }

    /**
     * 执行入库（带进度回调）
     */
    public List<ChunkResponse> ingest(ParseMessage message, IngestionProgressCallback progressCallback) {
        long t0 = System.currentTimeMillis();
        log.info("【INGEST】开始 | mediaId={}, libraryId={}, fileType={}",
                message.getMediaId(), message.getLibraryId(), message.getFileType());

        // 1. 媒体记录
        KbMedia media = mediaMapper.selectById(message.getMediaId());
        if (media == null) {
            throw new IngestionException("媒体文件不存在: " + message.getMediaId());
        }

        // 2. 从 OSS 下载文件
        byte[] fileBytes;
        try {
            fileBytes = aliOssUtil.download(message.getOssKey());
        } catch (IOException e) {
            throw new IngestionException("文件下载失败: " + message.getOssKey(), e);
        }

        // 3. 结构化文本提取（PDF 按页携带 page 元数据）
        DocumentTextExtractor extractor = extractorFactory.getExtractor(message.getFileType());
        if (extractor == null) {
            throw new IngestionException("不支持的文件类型: " + message.getFileType());
        }
        List<TextSegment> segments = extractor.extract(fileBytes);
        if (segments.isEmpty()) {
            throw new IngestionException("文件未提取到任何文本: " + media.getFileName());
        }

        // 4. 切分 + 组装元数据 + content_hash
        List<KbChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;
        for (TextSegment segment : segments) {
            List<String> texts = chunkingService.chunk(segment.getText());
            for (String chunkText : texts) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("fileName", media.getFileName());
                metadata.put("libraryId", message.getLibraryId());
                metadata.put("mediaId", message.getMediaId());
                metadata.put("chunkIndex", chunkIndex);
                if (segment.getMetadata() != null) {
                    metadata.putAll(segment.getMetadata());
                }

                chunks.add(KbChunk.builder()
                        .libraryId(message.getLibraryId())
                        .mediaId(message.getMediaId())
                        .content(chunkText)
                        .chunkIndex(chunkIndex++)
                        .metadata(metadata)
                        .contentHash(sha256Hex(chunkText))
                        .build());
            }
        }

        if (chunks.isEmpty()) {
            throw new IngestionException("切分后无任何有效分片: " + media.getFileName());
        }

        // 5. 批量 embedding + 入库（失败抛异常，消费侧重试/DLQ）
        vectorStore.addChunks(chunks, progressCallback);

        log.info("【INGEST】完成 | mediaId={}, chunks={}, cost={}ms",
                message.getMediaId(), chunks.size(), System.currentTimeMillis() - t0);

        // 6. 组装响应
        return chunks.stream()
                .map(c -> ChunkResponse.builder()
                        .libraryId(c.getLibraryId())
                        .mediaId(c.getMediaId())
                        .content(c.getContent())
                        .chunkIndex(c.getChunkIndex())
                        .metadata(c.getMetadata())
                        .source(buildSource(media.getFileName(), c.getMetadata()))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 引用来源：文件名-第N页 / 文件名
     */
    private String buildSource(String fileName, Map<String, Object> metadata) {
        Object page = metadata.get("page");
        if (page != null) {
            return fileName + "-第" + page + "页";
        }
        return fileName;
    }

    /**
     * 分片内容 SHA-256（增量更新比对用）
     */
    private String sha256Hex(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}