package top.lingxi.campus.chat.chat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import top.lingxi.campus.rag.file.service.impl.FileChatService;
import top.lingxi.campus.chat.chat.service.IChatService;

import java.io.IOException;

/**
 * 对话控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI对话", description = "AI对话相关接口")
public class ChatController {

    private final IChatService chatService;

    private final FileChatService fileChatService;

    /**
     * 文本对话接口（纯文本 SSE 流式返回）
     *
     * <p>SSE 数据格式：</p>
     * <pre>
     * data:SESSION_CREATED:&lt;id&gt;       // 新会话创建通知（仅首条消息）
     * data:&lt;think&gt;思考片段&lt;/think&gt;  // 思考过程分片
     * data:回答文本片段               // 回答内容分片
     * data:ERROR:错误信息             // 错误通知
     * </pre>
     */
    @PostMapping(value = "/text-chat")
    @Operation(summary = "智能对话", description = "支持文件上传和流式对话，SSE 纯文本格式")
    public SseEmitter textChat(
            @Parameter(description = "业务领域（IT/HR/ADMIN）", required = true)
            @RequestParam(required = false, defaultValue = "IT") String domain,

            @Parameter(description = "用户输入", required = true)
            @RequestParam String prompt,

            @Parameter(description = "上传文件（PDF/DOCX/MD/TXT，可选）")
            @RequestParam(required = false) MultipartFile file,

            @Parameter(description = "会话ID（首条消息传null）")
            @RequestParam(required = false) Long sessionId,

            @Parameter(description = "分组ID")
            @RequestParam(required = false) Long groupId,

            @Parameter(description = "是否启用思考过程")
            @RequestParam(required = false, defaultValue = "true") Boolean enableThinking,

            @Parameter(description = "思考token预算")
            @RequestParam(required = false) Integer thinkingBudget,

            @Parameter(description = "模型名称")
            @RequestParam(required = false) String model) throws IOException {

        log.info("收到聊天请求: domain={}, sessionId={}, hasFile={}, prompt={}",
                domain, sessionId, file != null, prompt);

        // ========== 功能一：处理上传文件 ==========
        String finalPrompt = prompt;

        if (file != null && !file.isEmpty()) {
            var result = fileChatService.process(file);
            log.info("文件处理结果: fileName={}, extractedText长度={}, isTooLong={}",
                    result.getFileName(),
                    result.getExtractedText() != null ? result.getExtractedText().length() : "null",
                    result.isTooLong());

            // ===== 修复后：三段式判断，覆盖所有场景 =====
            if (result.getExtractedText() == null || result.getExtractedText().isBlank()) {
                // 场景1：提取失败或文件无文本内容（扫描件/图片/加密文档）
                log.warn("文件内容提取为空: fileName={}", result.getFileName());
                finalPrompt = String.format(
                        "【用户上传文件：%s，但系统未能提取到文本内容（可能为扫描件/图片/加密文档）】\n\n【用户问题】\n%s",
                        result.getFileName(), prompt);

            } else if (result.isTooLong()) {
                // 场景2：文件太长，提示走知识库
                log.info("文件内容过长，提示用户走知识库: fileName={}, length={}",
                        result.getFileName(), result.getExtractedText().length());
                finalPrompt = String.format(
                        "【用户尝试上传文件：%s，但该文件内容过长（%d 字），" +
                                "系统暂不支持直接读取大文件。如需 AI 解读，请管理员将该文件上传至知识库。】\n\n【用户问题】\n%s",
                        result.getFileName(), result.getExtractedText().length(), prompt);

            } else {
                // 场景3：正常小文件，直接注入
                log.info("文件内容正常，已拼接进 prompt: fileName={}, length={}",
                        result.getFileName(), result.getExtractedText().length());
                finalPrompt = String.format(
                        "【用户上传文件：%s】\n文件内容如下：\n%s\n\n【用户问题】\n%s",
                        result.getFileName(), result.getExtractedText(), prompt);
            }
        }


        // ========== 原有 SSE 逻辑完全不变 ==========
        SseEmitter emitter = new SseEmitter(-1L);

        chatService.textChat(domain, groupId, sessionId, finalPrompt, enableThinking, thinkingBudget, model)
                .subscribe(
                        chunk -> {
                            try {
                                emitter.send(SseEmitter.event().data(chunk, MediaType.TEXT_PLAIN));
                            } catch (Exception e) {
                                log.warn("发送 SSE 数据失败: {}", e.getMessage());
                                emitter.completeWithError(e);
                            }
                        },
                        error -> {
                            log.warn("流式响应错误: {}", error.getMessage());
                            try {
                                emitter.send(SseEmitter.event().data("ERROR:服务暂时不可用", MediaType.TEXT_PLAIN));
                            } catch (Exception ignored) {
                            }
                            emitter.complete();
                        },
                        emitter::complete
                );

        return emitter;
    }
}
