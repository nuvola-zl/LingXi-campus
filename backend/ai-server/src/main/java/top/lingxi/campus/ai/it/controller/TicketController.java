package top.lingxi.campus.ai.it.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.lingxi.campus.common.context.BaseContext;
import top.lingxi.campus.common.result.Result;
import top.lingxi.campus.domain.biz.ticket.entity.BizTicket;
import top.lingxi.campus.domain.biz.ticket.entity.BizTicketComment;
import top.lingxi.campus.itAgent.ticket.user.service.ITicketQueryService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ticket")
@RequiredArgsConstructor
public class TicketController {

    private final ITicketQueryService ticketQueryService;

    private static final Long TEST_USER_ID = 1L;

    @GetMapping("/list")
    public String list() {
        return ticketQueryService.buildReply(TEST_USER_ID);
    }

    @GetMapping("/query")
    public String query(@RequestParam String ticketNo) {
        return ticketQueryService.buildReplyByTicketNo(TEST_USER_ID, ticketNo);
    }

    // ========== 用户端 JSON 接口 ==========

    @GetMapping("/user/list")
    public Result<List<BizTicket>> userTicketList() {
        Long userId = BaseContext.getCurrentId();
        return Result.success(ticketQueryService.getUserTicketList(userId));
    }

    @GetMapping("/user/{ticketId}")
    public Result<Map<String, Object>> userTicketDetail(@PathVariable Long ticketId) {
        Long userId = BaseContext.getCurrentId();
        BizTicket ticket = ticketQueryService.getUserTicketDetail(userId, ticketId);
        if (ticket == null) {
            return Result.error("工单不存在或无权查看");
        }
        List<BizTicketComment> comments = ticketQueryService.getTicketComments(ticketId);
        Map<String, Object> result = new HashMap<>();
        result.put("ticket", ticket);
        result.put("comments", comments);
        return Result.success(result);
    }
}