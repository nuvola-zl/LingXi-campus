package top.lingxi.campus.rag.parse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.lingxi.campus.rag.ingestion.IngestionPipeline;
import top.lingxi.campus.rag.ingestion.TextSegment;

import top.lingxi.campus.rag.ingestion.TxtTextExtractor;
import top.lingxi.campus.domain.ai.dto.ChunkResponse;
import top.lingxi.campus.domain.ai.dto.ParseMessage;

import java.util.List;
import java.util.stream.Collectors;

/**
 * TXT 解析器（兼容适配层）
 *
 * @deprecated 知识库入库已迁移至 {@link IngestionPipeline}（Phase 2）。
 * 本类仅保留给聊天直传等遗留调用点使用。
 */
@Deprecated
@Slf4j
@Component
public class TxtFileParser implements FileParser {

    private static final String FILE_TYPE = "TXT";

    private final TxtTextExtractor textExtractor;
    private final IngestionPipeline ingestionPipeline;

    public TxtFileParser(TxtTextExtractor textExtractor, IngestionPipeline ingestionPipeline) {
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