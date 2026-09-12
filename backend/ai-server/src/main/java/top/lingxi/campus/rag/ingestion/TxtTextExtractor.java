package top.lingxi.campus.rag.ingestion;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 纯文本提取器（.txt）
 */
@Component
public class TxtTextExtractor implements DocumentTextExtractor {

    private static final String FILE_TYPE = "TXT";

    @Override
    public String getFileType() {
        return FILE_TYPE;
    }

    @Override
    public List<TextSegment> extract(byte[] fileBytes) {
        String content = new String(fileBytes, StandardCharsets.UTF_8);
        if (content.isBlank()) {
            throw new RuntimeException("文本文件内容为空");
        }
        return List.of(new TextSegment(content, Map.of()));
    }
}