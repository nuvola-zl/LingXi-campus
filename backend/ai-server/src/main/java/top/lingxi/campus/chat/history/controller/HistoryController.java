package top.lingxi.campus.chat.history.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import top.lingxi.campus.common.context.BaseContext;
import top.lingxi.campus.common.result.Result;
import top.lingxi.campus.domain.ai.entity.ChatMessage;
import top.lingxi.campus.domain.ai.entity.ChatSession;
import top.lingxi.campus.chat.history.service.IHistoryService;

import java.util.List;

/**
 * 历史记录管理接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai/history")
@RequiredArgsConstructor
public class HistoryController {

    private final IHistoryService historyService;

    /**
     * 获取指定类型的会话历史列表（返回 sessionId 列表）
     * 侧栏不需要用这个，用 SessionController.list 更合适
     */
    @GetMapping("/type/{type}")
    public Result<List<Long>> getChatHistory(@PathVariable String type) {
        List<Long> sessionIds = historyService.getSessionIdsByTypeAndUserId(type, BaseContext.getCurrentId());
        return Result.success(sessionIds);
    }

    /**
     * 获取指定用户的最新会话列表（按创建时间和置顶降序）
     * 侧栏也可以用这个作为备选
     */
    @GetMapping("/chat")
    public Result<List<ChatSession>> getChatHistory(
            @RequestParam(required = false, defaultValue = "100") Integer limit) {
        List<ChatSession> sessionList = historyService.getLatestMessagesByUserId(limit);
        return Result.success(sessionList);
    }

    /**
     * 获取指定会话的消息历史
     * 【修改】去掉路径中的 type，因为 sessionId 已经唯一确定会话
     */
    @GetMapping("/session/{chatId}")
    public Result<List<ChatMessage>> getChatMessages(@PathVariable Long chatId) {
        try {
            Long userId = BaseContext.getCurrentId();
            log.info("获取会话消息历史，用户ID: {}, 会话ID: {}", userId, chatId);
            List<ChatMessage> messages = historyService.getMessagesBySessionIdAndUserId(chatId, userId);
            return Result.success(messages);
        } catch (Exception e) {
            log.error("获取会话消息历史失败", e);
            return Result.error("获取会话消息历史失败: " + e.getMessage());
        }
    }
}