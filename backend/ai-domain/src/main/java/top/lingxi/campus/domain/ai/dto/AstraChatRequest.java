package top.lingxi.campus.domain.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Astra 知识问答请求
 *
 * 重构说明：新增 type 字段显式声明对话类型（替代原 prompt 嗅探方式）。
 * 不传时默认 RAG，旧前端无需改动。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "知识问答请求")
public class AstraChatRequest {

    @Schema(description = "知识库ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "知识库ID不能为空")
    private Long libraryId;

    @Schema(description = "会话ID，null表示新建会话")
    private Long sessionId;

    @Schema(description = "用户问题", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "问题不能为空")
    private String prompt;

    /**
     * 回答风格：detail（详细，默认）/ concise（精简）
     */
    private String style = "detail";

    /**
     * 对话类型：RAG=知识库问答（默认）/ FILE_UPLOAD=文件直传对话（不走检索）
     */
    @Schema(description = "对话类型：RAG=知识库问答(默认)，FILE_UPLOAD=文件直传对话")
    @Builder.Default
    private AstraChatType type = AstraChatType.RAG;
}