package top.lingxi.campus.rag.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Word (.docx) 文本提取器
 *
 * 提取全部段落拼接为单一文本片段。
 * TODO(可选增强)：读取标题样式重建 section_path 存入元数据，
 *  支撑按章节路径的引用溯源（对应 RAG 规范中的结构化解析）
 */
@Slf4j
@Component
public class WordTextExtractor implements DocumentTextExtractor {

    private static final String FILE_TYPE = "DOCX";

    @Override
    public String getFileType() {
        return FILE_TYPE;
    }

    @Override
    public List<TextSegment> extract(byte[] fileBytes) {
        StringBuilder fullText = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(fileBytes))) {
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            for (XWPFParagraph paragraph : paragraphs) {
                String text = paragraph.getText();
                if (text != null && !text.isBlank()) {
                    fullText.append(text.trim()).append("\n");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Word 文本提取失败", e);
        }

        if (fullText.isEmpty()) {
            throw new RuntimeException("Word 文档未提取到任何文本");
        }
        return List.of(new TextSegment(fullText.toString(), Map.of()));
    }
}