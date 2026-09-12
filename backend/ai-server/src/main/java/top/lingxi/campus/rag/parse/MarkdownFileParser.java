package top.lingxi.campus.rag.parse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.lingxi.campus.rag.ingestion.IngestionPipeline;
import top.lingxi.campus.rag.ingestion.MarkdownTextExtractor;
import top.lingxi.campus.rag.ingestion.TextSegment;

import top.lingxi.campus.domain.ai.dto.ChunkResponse;
import top.lingxi.campus.domain.ai.dto.ParseMessage;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Markdown 解析器（兼容适配层）
 *
 * @deprecated 知识库入库已迁移至 {@link IngestionPipeline}（Phase 2）。
 * 本类仅保留给聊天直传等遗留调用点使用。
 *
 * 注意：上传侧已修正 .md 后缀识别（原 text/plain 统一映射 TXT，
 * 本解析器在旧实现中永远匹配不到）。
 */
@Deprecated
@Slf4j
@Component
public class MarkdownFileParser implements FileParser {

    private static final String FILE_TYPE = "MD";

    private final MarkdownTextExtractor textExtractor;
    private final IngestionPipeline ingestionPipeline;

    public MarkdownFileParser(MarkdownTextExtractor textExtractor, IngestionPipeline ingestionPipeline) {
        this.textExtractor = textExtractor;
        this.ingestionPipeline = ingestionPipeline;
    }

    @Override
    public String getFileType() {
        return FILE_TYPE;
    }

    @Override
    public List<ChunkResponse> parse(ParseMessage message) {
        return ingestionPipeline.ingest(message);
    }

    @Override
    public String extractText(byte[] fileBytes) {
        List<TextSegment> segments = textExtractor.extract(fileBytes);
        return segments.stream()
                .map(TextSegment::getText)
                .collect(Collectors.joining("\n"));
    }
}