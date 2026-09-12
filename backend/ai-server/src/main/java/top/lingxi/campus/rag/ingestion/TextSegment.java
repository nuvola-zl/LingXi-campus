package top.lingxi.campus.rag.ingestion;

import java.util.Map;

/**
 * 结构化文本片段
 *
 * 为什么不是简单的 String：PDF 等格式需要保留结构元数据（页码），
 * 扁平化成纯文本会丢失引用来源（"文件名-第N页"）信息。
 * 每个 TextSegment 携带该片段自身的元数据（如 page），
 * 由 IngestionPipeline 合并进 chunk 元数据。
 */
public class TextSegment {

    private final String text;
    private final Map<String, Object> metadata;

    public TextSegment(String text, Map<String, Object> metadata) {
        this.text = text;
        this.metadata = metadata;
    }

    public String getText() {
        return text;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}