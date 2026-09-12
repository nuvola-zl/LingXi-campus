package top.lingxi.campus.rag.ingestion;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Markdown 文本提取器（.md）
 *
 * 说明：Markdown 按原始文本提取，标题层级信息保留在文本中，
 * 由 ChunkingService 按段落边界切分。若未来做结构化解析，
 * 可在此按标题重建 section_path 元数据。
 */
@Component
public class MarkdownTextExtractor implements DocumentTextExtractor {

    private static final String FILE_TYPE = "MD";

    @Override
    public String getFileType() {
        return FILE_TYPE;
    }

    @Override
    public List<TextSegment> extract(byte[] fileBytes) {
        String content = new String(fileBytes, StandardCharsets.UTF_8);
        if (content.isBlank()) {
            throw new RuntimeException("Markdown 文件内容为空");
        }
        return List.of(new TextSegment(content, Map.of()));
    }
}