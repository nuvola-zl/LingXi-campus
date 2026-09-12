package top.lingxi.campus.itAgent.ticket.engineer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import top.lingxi.campus.itAgent.ticket.engineer.service.ITicketEngineerService;
import top.lingxi.campus.common.annotation.RequireRole;
import top.lingxi.campus.common.context.BaseContext;
import top.lingxi.campus.common.result.Result;
import top.lingxi.campus.domain.engineer.dto.TicketProcessRequest;
import top.lingxi.campus.domain.engineer.vo.EngineerTicketVO;


import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/ticket/engineer")
@RequiredArgsConstructor
@Tag(name = "工程师工单处理", description = "工程师接单、处理、关闭工单")
public class TicketEngineerController {

    private final ITicketEngineerService ticketEngineerService;

    @GetMapping("/pending")
    @RequireRole("ENGINEER")
    @Operation(summary = "待处理工单列表", description = "包含组内未分配 + 已分配给自己的处理中工单")
    public Result<List<EngineerTicketVO>> getPending() {
        Long engineerId = BaseContext.getCurrentId();
        return Result.success(ticketEngineerService.getPendingList(engineerId));
    }

    @GetMapping("/history")
    @RequireRole("ENGINEER")
    @Operation(summary = "历史工单列表", description = "工程师已关闭的工单")
    public Result<List<EngineerTicketVO>> getHistory(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        Long engineerId = BaseContext.getCurrentId();
        return Result.success(ticketEngineerService.getHistoryList(engineerId, page, pageSize));
    }

    @GetMapping("/{ticketId}")
    @RequireRole("ENGINEER")
    @Operation(summary = "工单详情", description = "含完整处理时间线")
    public Result<EngineerTicketVO> getDetail(@PathVariable Long ticketId) {
        return Result.success(ticketEngineerService.getDetail(ticketId));
    }

    @PostMapping("/{ticketId}/claim")
    @RequireRole("ENGINEER")
    @Operation(summary = "接单")
    public Result<Void> claim(@PathVariable Long ticketId) {
        Long engineerId = BaseContext.getCurrentId();
        ticketEngineerService.claim(ticketId, engineerId);
        return Result.success();
    }

    @PostMapping("/{ticketId}/comment")
    @RequireRole("ENGINEER")
    @Operation(summary = "添加处理记录")
    public Result<Void> addComment(
            @PathVariable Long ticketId,
            @Valid @RequestBody TicketProcessRequest request) {
        Long engineerId = BaseContext.getCurrentId();
        ticketEngineerService.addComment(ticketId, engineerId, request.getContent());
        return Result.success();
    }

    @PostMapping("/{ticketId}/resolve")
    @RequireRole("ENGINEER")
    @Operation(summary = "标记解决", description = "工单变为待确认状态，等待用户确认关闭")
    public Result<Void> resolve(@PathVariable Long ticketId) {
        Long engineerId = BaseContext.getCurrentId();
        ticketEngineerService.resolve(ticketId, engineerId);
        return Result.success();
    }
}