package top.lingxi.campus.rag.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PDF 文本提取器
 *
 * 按页提取：每页一个 TextSegment 并携带 page 元数据，
 * 供入库后生成"文件名-第N页"引用来源。
 * 注意：扫描版 PDF（图片型）提取结果为空，需要 OCR——当前版本不做 OCR，
 * 空文本会在 Pipeline 层记录告警（解析质量是 RAG 的上限，此处不静默）。
 */
@Slf4j
@Component
public class PdfTextExtractor implements DocumentTextExtractor {

    private static final String FILE_TYPE = "PDF";

    @Override
    public String getFileType() {
        return FILE_TYPE;
    }

    @Override
    public List<TextSegment> extract(byte[] fileBytes) {
        List<TextSegment> segments = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(fileBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            int totalPages = document.getNumberOfPages();
            for (int page = 1; page <= totalPages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(document);

                if (pageText == null || pageText.isBlank()) {
                    log.warn("PDF 第 {} 页未提取到文本（可能为扫描件/图片页）", page);
                    continue;
                }
                segments.add(new TextSegment(pageText.trim(), Map.of("page", page)));
            }
        } catch (IOException e) {
            throw new RuntimeException("PDF 文本提取失败", e);
        }

        if (segments.isEmpty()) {
            log.error("PDF 全文未提取到任何文本（扫描版 PDF 需要 OCR 能力）");
        }
        return segments;
    }
}