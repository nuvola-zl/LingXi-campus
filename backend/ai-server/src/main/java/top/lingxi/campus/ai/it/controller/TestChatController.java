package top.lingxi.campus.ai.it.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import top.lingxi.campus.chat.chat.service.IChatService;
import top.lingxi.campus.common.context.BaseContext;

@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestChatController {

    private final IChatService chatService;
    private static final Long TEST_USER_ID = 1L;
    private static final String TEST_ROLE_TYPE = "ADMIN";  // 测试用，硬编码 ADMIN

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatTest(
            @RequestParam String prompt,
            @RequestParam(required = false, defaultValue = "IT") String domain,
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false, defaultValue = "true") Boolean enableThinking,
            @RequestParam(required = false) Integer thinkingBudget,
            @RequestParam(required = false) String model,
            HttpServletRequest request) {

        log.info("【TestChat POST】请求来源: IP={}, prompt={}",
                request.getRemoteAddr(), prompt);

        // 改成新的 setCurrentUser
        BaseContext.setCurrentUser(TEST_USER_ID, TEST_ROLE_TYPE);
        try {
            SseEmitter emitter = new SseEmitter(-1L);

            Flux<String> flux = chatService.textChat(domain, groupId, sessionId, prompt,
                    enableThinking, thinkingBudget, model);

            flux.subscribe(
                    chunk -> {
                        try {
                            emitter.send(SseEmitter.event().data(chunk, MediaType.TEXT_PLAIN));
                        } catch (Exception e) {
                            log.warn("发送 SSE 失败: {}", e.getMessage());
                            emitter.completeWithError(e);
                        }
                    },
                    error -> {
                        log.warn("流式错误: {}", error.getMessage());
                        try {
                            emitter.send(SseEmitter.event().data("ERROR:服务暂时不可用", MediaType.TEXT_PLAIN));
                        } catch (Exception ignored) {}
                        emitter.complete();
                    },
                    emitter::complete
            );

            return emitter;
        } finally {
            // 改成新的 removeCurrentUser
            BaseContext.removeCurrentUser();
        }
    }

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatTestGet(
            @RequestParam String prompt,
            @RequestParam(required = false, defaultValue = "IT") String domain,
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false, defaultValue = "true") Boolean enableThinking,
            @RequestParam(required = false) Integer thinkingBudget,
            @RequestParam(required = false) String model,
            HttpServletRequest request) {

        log.info("【TestChat GET】请求来源: IP={}, prompt={}",
                request.getRemoteAddr(), prompt);

        return chatTest(prompt, domain, sessionId, groupId, enableThinking, thinkingBudget, model, request);
    }
}