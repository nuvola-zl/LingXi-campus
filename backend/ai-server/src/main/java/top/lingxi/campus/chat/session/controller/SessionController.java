package top.lingxi.campus.chat.session.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import top.lingxi.campus.common.context.BaseContext;
import top.lingxi.campus.common.result.Result;
import top.lingxi.campus.domain.ai.dto.SessionListDTO;
import top.lingxi.campus.domain.ai.entity.ChatSession;
import top.lingxi.campus.chat.session.service.IChatSessionService;

import java.util.List;

/**
 * @description: 会话管理接口
 * @author: Hazenix
 * @version: 1.0.0
 * @date: 2026/1/27
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai/session")
@RequiredArgsConstructor
@Tag(name = "会话管理", description = "会话管理相关接口")
public class SessionController {
    
    private final IChatSessionService chatSessionService;
    
    /**
     * 创建新会话（通常不需要手动调用，由聊天接口自动创建）
     * @param type 业务领域（IT/HR/ADMIN）或会话类型
     * @param title 会话标题（可选）
     * @return 包含会话ID的响应
     */
    @PostMapping("/create")
    @Operation(summary = "创建新会话", description = "手动创建会话（通常不需要，由聊天接口自动创建）")
    public Result<Long> createSession(
            @Parameter(description = "会话类型", required = true) @RequestParam String type,
            @Parameter(description = "会话标题") @RequestParam(required = false) String title) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            return Result.error("用户未登录");
        }
        ChatSession session = chatSessionService.createSession(userId, type, title);
        return Result.success(session.getId());
    }

    /**
     * 修改会话信息
     * @param sessionId 会话ID
     * @param title 会话标题（可选）
     * @param groupId 分组ID（可选）
     */
    @PutMapping
    @Operation(summary = "更新会话", description = "更新会话标题或分组")
    public Result<Void> updateSession(
            @Parameter(description = "会话ID", required = true) @RequestParam Long sessionId,
            @Parameter(description = "新标题") @RequestParam(required = false) String title,
            @Parameter(description = "新分组ID") @RequestParam(required = false) Long groupId) {
        chatSessionService.updateSession(sessionId, title, groupId);
        return Result.success();
    }

    /**
     * 删除会话
     * @param sessionId 会话ID
     */
    @DeleteMapping
    @Operation(summary = "删除会话", description = "软删除会话")
    public Result<Void> deleteSession(
            @Parameter(description = "会话ID", required = true) @RequestParam Long sessionId) {
        chatSessionService.deleteSession(sessionId);
        return Result.success();
    }
    
    /**
     * 获取会话列表
     * @param type 会话类型（可选）
     * @param groupId 分组ID（可选）
     * @param page 页码
     * @param pageSize 每页数量
     * @return 会话列表
     */
    @GetMapping("/list")
    @Operation(summary = "获取会话列表",
            description = "获取用户的会话列表，支持分页和筛选。type 传 IT/HR/ADMIN 对应三个业务入口")
    public Result<List<SessionListDTO>> getSessionList(
            @Parameter(description = "会话类型（IT/HR/ADMIN）") @RequestParam(required = false) String type,
            @Parameter(description = "分组ID") @RequestParam(required = false) Long groupId,
            @Parameter(description = "页码") @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页数量") @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            return Result.error("用户未登录");
        }
        List<SessionListDTO> sessions = chatSessionService.getSessionList(userId, type, groupId, page, pageSize);
        return Result.success(sessions);
    }
    
    /**
     * 置顶/取消置顶会话
     * @param sessionId 会话ID
     * @param isTop 是否置顶
     */
    @PutMapping("/toggle-top")
    @Operation(summary = "置顶会话", description = "置顶或取消置顶会话")
    public Result<Void> toggleTop(
            @Parameter(description = "会话ID", required = true) @RequestParam Long sessionId,
            @Parameter(description = "是否置顶", required = true) @RequestParam Boolean isTop) {
        chatSessionService.toggleTop(sessionId, isTop);
        return Result.success();
    }
}
