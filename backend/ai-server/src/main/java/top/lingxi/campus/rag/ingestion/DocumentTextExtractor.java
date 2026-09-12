package top.lingxi.campus.rag.ingestion;

import java.util.List;

/**
 * 文档文本提取器（策略模式）
 *
 * Phase 2 重构说明：原 FileParser 将"提取文本、切分、embedding、入库"全部
 * 塞进每个文件类型的实现类（4 个类近 90% 代码重复）。本接口只负责
 * "字节数组 → 结构化文本片段"这一件事；切分/入库由 IngestionPipeline 统一编排。
 *
 * 与 FileParser 的关系：FileParser 保留给聊天直传等非入库场景，
 * 知识库入库链路统一走 DocumentTextExtractor + IngestionPipeline。
 */
public interface DocumentTextExtractor {

    /**
     * 支持的文件类型标识（与上传侧 getFileType 的产出对齐，如 PDF / DOCX / TXT / MD）
     */
    String getFileType();

    /**
     * 从文件字节中提取结构化文本
     *
     * @param fileBytes 文件内容
     * @return 文本片段列表（如 PDF 按页一个片段并携带 page 元数据；纯文本通常为单片段）
     */
    List<TextSegment> extract(byte[] fileBytes);

    default boolean supports(String fileType) {
        return fileType != null && getFileType().equalsIgnoreCase(fileType);
    }
}