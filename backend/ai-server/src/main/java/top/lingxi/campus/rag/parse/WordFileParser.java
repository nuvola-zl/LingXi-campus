package top.lingxi.campus.rag.parse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.lingxi.campus.rag.ingestion.IngestionPipeline;
import top.lingxi.campus.rag.ingestion.TextSegment;

import top.lingxi.campus.rag.ingestion.WordTextExtractor;
import top.lingxi.campus.domain.ai.dto.ChunkResponse;
import top.lingxi.campus.domain.ai.dto.ParseMessage;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Word 解析器（兼容适配层）
 *
 * @deprecated 知识库入库已迁移至 {@link IngestionPipeline}（Phase 2）。
 * 本类仅保留给聊天直传等遗留调用点使用。
 *
 * 注意：FILE_TYPE 为 "DOCX"，上传侧（AstraMediaServiceImpl.getFileType）
 * 已同步修正为产出 "DOCX"（原产出 "WORD" 导致永远匹配不到解析器，Word 上传
 * 在旧实现中实际进入重试→死信）。
 */
@Deprecated
@Slf4j
@Component
public class WordFileParser implements FileParser {

    private static final String FILE_TYPE = "DOCX";

    private final WordTextExtractor textExtractor;
    private final IngestionPipeline ingestionPipeline;

    public WordFileParser(WordTextExtractor textExtractor, IngestionPipeline ingestionPipeline) {
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