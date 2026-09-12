package top.lingxi.campus.rag.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import top.lingxi.campus.common.annotation.RequireRole;
import top.lingxi.campus.common.context.BaseContext;
import top.lingxi.campus.common.result.Result;
import top.lingxi.campus.domain.ai.dto.AstraChatEvent;
import top.lingxi.campus.domain.ai.dto.AstraChatRequest;
import top.lingxi.campus.domain.ai.dto.AstraChatType;
import top.lingxi.campus.domain.ai.dto.LibraryCreateRequest;
import top.lingxi.campus.domain.ai.dto.LibraryResponse;
import top.lingxi.campus.domain.ai.dto.LibraryUpdateRequest;
import top.lingxi.campus.rag.service.IAstraLibraryService;
import top.lingxi.campus.rag.service.IAstraSearchService;

import java.util.List;

/**
 * Astra 知识库 Controller
 *
 * 重构说明：chat 端点的 SSE 事件处理由"解析字符串"简化为"直发结构化事件"，
 * 事件序列化唯一出口在 sendQuietly。文件直传对话由 type 字段路由到
 * chatWithFile，不再在 prompt 内容里嗅探。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/astra")
@RequiredArgsConstructor
@Tag(name = "Astra 知识库接口")
public class AstraController {

    private final IAstraLibraryService libraryService;
    private final IAstraSearchService searchService;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "知识库问答(SSE)", description = "type=RAG 走检索增强问答；type=FILE_UPLOAD 直传文件内容对话")
    public SseEmitter chat(@Valid @RequestBody AstraChatRequest request) {
        Long userId = BaseContext.getCurrentId();

        // 路由：显式 type 优先，兼容旧前端的 prompt 标记嗅探
        Flux<AstraChatEvent> events = isFileUploadChat(request)
                ? searchService.chatWithFile(userId, request)
                : searchService.chat(userId, request);

        SseEmitter emitter = new SseEmitter(60000L);
        events.subscribe(
                event -> sendQuietly(emitter, event),
                emitter::completeWithError,
                emitter::complete
        );
        return emitter;
    }

    /**
     * 判断是否文件直传对话。
     * 优先使用显式 type 字段；未传时回退到历史 prompt 标记嗅探（兼容旧前端）。
     * TODO(兼容期后删除)：旧前端全部升级后可移除 contains 回退逻辑。
     */
    private boolean isFileUploadChat(AstraChatRequest request) {
        if (request.getType() == AstraChatType.FILE_UPLOAD) {
            return true;
        }
        String prompt = request.getPrompt();
        return prompt != null && (
                prompt.contains("【用户上传文件") ||
                        prompt.contains("【用户尝试上传文件")
        );
    }

    /**
     * 发送单个 SSE 事件。序列化唯一出口：事件名与 data 由 AstraChatEvent 保证格式。
     */
    private void sendQuietly(SseEmitter emitter, AstraChatEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.getEvent())
                    .data(event.getData(), MediaType.TEXT_PLAIN));
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    // ==================== 知识库管理 ====================

    @PostMapping("/libraries")
    @RequireRole("ADMIN")   //权限，仅管理员
    @Operation(summary = "创建知识库")
    public Result<LibraryResponse> createLibrary(@Valid @RequestBody LibraryCreateRequest request) {
        Long userId = BaseContext.getCurrentId();
        log.info("创建知识库: userId={}, name={}", userId, request.getName());
        LibraryResponse response = libraryService.createLibrary(userId, request);
        return Result.success(response);
    }


    @GetMapping("/libraries")
    @Operation(summary = "获取知识库列表")
    public Result<List<LibraryResponse>> listLibraries(
            @Parameter(description = "搜索关键字") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") Integer page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") Integer size) {
        Long userId = BaseContext.getCurrentId();
        List<LibraryResponse> list = libraryService.listLibraries(userId, keyword, page, size);
        return Result.success(list);
    }


    @GetMapping("/libraries/{id}")
    @Operation(summary = "获取知识库详情")
    public Result<LibraryResponse> getLibrary(@PathVariable Long id) {
        Long userId = BaseContext.getCurrentId();
        LibraryResponse response = libraryService.getLibrary(id, userId);
        return Result.success(response);
    }


    @PutMapping("/libraries/{id}")
    @RequireRole("ADMIN")
    @Operation(summary = "更新知识库")
    public Result<LibraryResponse> updateLibrary(
            @PathVariable Long id,
            @Valid @RequestBody LibraryUpdateRequest request) {
        Long userId = BaseContext.getCurrentId();
        LibraryResponse response = libraryService.updateLibrary(id, userId, request);
        return Result.success(response);
    }

    @DeleteMapping("/libraries/{id}")
    @RequireRole("ADMIN")
    @Operation(summary = "删除知识库")
    public Result<Void> deleteLibrary(@PathVariable Long id) {
        Long userId = BaseContext.getCurrentId();
        libraryService.deleteLibrary(id, userId);
        return Result.success();
    }

    @PutMapping("/libraries/{id}/toggle-top")
    @RequireRole("ADMIN")
    @Operation(summary = "置顶/取消置顶知识库")
    public Result<LibraryResponse> toggleTop(@PathVariable Long id) {
        Long userId = BaseContext.getCurrentId();
        LibraryResponse response = libraryService.toggleTop(id, userId);
        return Result.success(response);
    }

}