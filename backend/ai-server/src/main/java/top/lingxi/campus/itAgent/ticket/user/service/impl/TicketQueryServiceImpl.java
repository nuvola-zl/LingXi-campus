package top.lingxi.campus.itAgent.ticket.user.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import top.lingxi.campus.itAgent.Event.TicketClosedEvent;
import top.lingxi.campus.domain.biz.ticket.entity.BizTicket;
import top.lingxi.campus.domain.biz.ticket.entity.BizTicketCategory;
import top.lingxi.campus.domain.biz.ticket.entity.BizTicketComment;
import top.lingxi.campus.domain.biz.ticket.mapper.BizTicketCategoryMapper;
import top.lingxi.campus.domain.biz.ticket.mapper.BizTicketCommentMapper;
import top.lingxi.campus.domain.biz.ticket.mapper.BizTicketMapper;
import top.lingxi.campus.itAgent.ticket.user.service.ITicketQueryService;


import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketQueryServiceImpl implements ITicketQueryService {

    private final BizTicketMapper ticketMapper;

    private final BizTicketCommentMapper commentMapper;  // 新增

    private final BizTicketCategoryMapper categoryMapper;  // 新增

    private final ApplicationEventPublisher eventPublisher; // 新增

    @Override
    public String buildReply(Long userId) {
        // 1. 查数据库：该用户提交的所有报修单，最新在前
        List<BizTicket> tickets = ticketMapper.selectByRequesterId(userId);

        // 2. 空结果
        if (tickets == null || tickets.isEmpty()) {
            return "您目前没有报修单。";
        }

        // 3. 单条结果
        if (tickets.size() == 1) {
            BizTicket t = tickets.get(0);
            return String.format(
                "您有 1 个报修单：【%s】(编号：%s)，当前状态：%s。",
                t.getTitle(),
                t.getTicketNo(),
                mapStatus(t.getStatus())
            );
        }

        // 4. 多条结果
        StringBuilder sb = new StringBuilder();
        sb.append("您目前有 ").append(tickets.size()).append(" 个报修单：\n");
        for (int i = 0; i < tickets.size(); i++) {
            BizTicket t = tickets.get(i);
            sb.append(i + 1).append(". 【")
              .append(t.getTitle()).append("】(编号：")
              .append(t.getTicketNo()).append(") - ")
              .append(mapStatus(t.getStatus())).append("\n");
        }
        // 去掉最后一个换行
        return sb.toString().trim();
    }

    @Override
    public String buildReplyByTicketNo(Long userId, String ticketNo) {
        BizTicket ticket = ticketMapper.selectByTicketNo(ticketNo);

        if (ticket == null) {
            return "未找到编号为 " + ticketNo + " 的报修单。";
        }

        if (!ticket.getRequesterId().equals(userId)) {
            return "您无权查看报修单 " + ticketNo + "。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
                "报修单详情：\n【%s】\n编号：%s\n状态：%s\n创建时间：%s",
                ticket.getTitle(),
                ticket.getTicketNo(),
                mapStatus(ticket.getStatus()),
                ticket.getCreatedAt().toLocalDate()
        ));

        // ===== 新增：处理时间线 =====
        List<BizTicketComment> comments = commentMapper.selectByTicketId(ticket.getId());
        if (comments != null && !comments.isEmpty()) {
            sb.append("\n\n📋 处理记录：");
            for (BizTicketComment c : comments) {
                String time = c.getCreatedAt().format(
                        java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")
                );
                String actor = "SYSTEM".equals(c.getType()) ? "系统" : "工程师";
                sb.append(String.format("\n[%s] %s：%s", time, actor, c.getContent()));
            }
        } else {
            sb.append("\n\n暂无处理记录，工程师会尽快响应。");
        }
        // ============================

        return sb.toString();
    }

    @Override
    public String urgentLatestTicket(Long userId) {
        // 1. 查最新未关闭报修单
        BizTicket ticket = ticketMapper.selectLatestOpenTicket(userId);

        if (ticket == null) {
            return "您目前没有未关闭的报修单，如需新建请描述您遇到的问题。";
        }

        // 2. 已经是紧急
        if (ticket.getPriority() != null && ticket.getPriority() == 1) {
            return String.format(
                    "报修单【%s】(编号：%s) 已经是紧急状态，工程师会优先处理。",
                    ticket.getTitle(), ticket.getTicketNo()
            );
        }

        // 3. 改优先级
        ticket.setPriority(1);
        ticketMapper.updateById(ticket);

        // 4. 返回结果
        return String.format(
                "已为您加急处理报修单【%s】(编号：%s)\n优先级已调整为：紧急\n工程师会优先响应，预计 %s 内处理。",
                ticket.getTitle(),
                ticket.getTicketNo(),
                getSlaResponseTime(ticket.getCategoryId())
        );
    }

    @Override
    public String closeLatestTicket(Long userId) {
        BizTicket ticket = ticketMapper.selectLatestOpenTicket(userId);

        if (ticket == null) {
            return "您目前没有未关闭的报修单。";
        }

        // 已经是关闭状态
        if (ticket.getStatus() != null && ticket.getStatus() == 4) {
            return String.format(
                    "报修单【%s】(编号：%s) 已经是关闭状态。",
                    ticket.getTitle(), ticket.getTicketNo()
            );
        }

        // 关闭报修单
        ticket.setStatus(4);
        ticket.setClosedAt(java.time.LocalDateTime.now());
        ticketMapper.updateById(ticket);

        // ========== 新增：触发知识沉淀 ==========
        eventPublisher.publishEvent(new TicketClosedEvent(this, ticket.getId()));

        return String.format(
                "已为您关闭报修单【%s】(编号：%s)\n感谢您的反馈，如有其他问题随时联系！",
                ticket.getTitle(),
                ticket.getTicketNo()
        );
    }

    /**
     * 获取预计响应时间
     */
    private String getSlaResponseTime(Long categoryId) {
        BizTicketCategory category = categoryMapper.selectById(categoryId);
        if (category != null && category.getSlaResponseMinutes() != null) {
            return category.getSlaResponseMinutes() + " 分钟";
        }
        return "30 分钟";
    }

    @Override
    public List<BizTicket> getUserTicketList(Long userId) {
        return ticketMapper.selectByRequesterId(userId);
    }

    @Override
    public BizTicket getUserTicketDetail(Long userId, Long ticketId) {
        BizTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null || !ticket.getRequesterId().equals(userId)) {
            return null;
        }
        return ticket;
    }

    @Override
    public List<BizTicketComment> getTicketComments(Long ticketId) {
        return commentMapper.selectByTicketId(ticketId);
    }

    /**
     * 状态码转中文
     * 1待处理 2处理中 3待确认 4已关闭
     */
    private String mapStatus(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 1 -> "待处理";
            case 2 -> "处理中";
            case 3 -> "待确认";
            case 4 -> "已关闭";
            default -> "未知";
        };
    }
}