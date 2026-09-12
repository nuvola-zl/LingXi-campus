package top.lingxi.campus.rag.parse;

import top.lingxi.campus.domain.ai.dto.ParseMessage;
import top.lingxi.campus.domain.ai.dto.ChunkResponse;

import java.util.List;

/**
 * 文件解析器接口
 */
public interface FileParser {

    /**
     * 获取支持的文件类型
     */
    String getFileType();

    /**
     * 解析文件并生成 Chunk
     * @param message 解析消息
     * @return 分片列表
     */
    List<ChunkResponse> parse(ParseMessage message);

    /**
     * 是否支持该文件类型
     */
    default boolean supports(String fileType) {
        return getFileType().equalsIgnoreCase(fileType);
    }

    /**
     * 直接从字节数组提取纯文本（聊天上传用，不走知识库入库流程）
     */
    default String extractText(byte[] fileBytes) {
        throw new UnsupportedOperationException("该解析器不支持直接提取文本");
    }
}
