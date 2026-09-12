package top.lingxi.campus.itAgent.agent.tool;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import top.lingxi.campus.domain.biz.ticket.entity.BizTicket;
import top.lingxi.campus.domain.biz.ticket.entity.BizTicketCategory;
import top.lingxi.campus.domain.biz.ticket.mapper.BizTicketCategoryMapper;
import top.lingxi.campus.domain.biz.ticket.mapper.BizTicketMapper;
import top.lingxi.campus.tool.DuplicateChecker;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 报修工单创建工具（create_ticket）
 *
 * 主题变更（灵犀校园）：IT 工单 → 后勤报修工单，仅文案变更
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreateTicketTool implements AgentTool {

    private final DuplicateChecker duplicateChecker;
    private final RedissonClient redissonClient;
    private final BizTicketMapper ticketMapper;
    private final BizTicketCategoryMapper categoryMapper;

    @Override
    public String name() {
        return "create_ticket";
    }

    @Override
    public String description() {
        return "创建后勤报修工单。参数: userId(用户ID), title(报修标题), description(问题描述), priority(优先级1-3,默认2), sessionId(会话ID)";
    }

    @Override
    public String execute(Map<String, Object> args) {
        Long userId = Long.valueOf(args.get("userId").toString());
        String title = (String) args.get("title");
        String description = (String) args.get("description");
        Integer priority = args.get("priority") != null
                ? Integer.parseInt(args.get("priority").toString()) : 2;
        Long sessionId = args.get("sessionId") != null
                ? Long.valueOf(args.get("sessionId").toString()) : null;
        Long categoryId = 1L;

        // 1. 查重
        String dupMsg = duplicateChecker.checkDuplicateTicket(userId, title, description);
        if (dupMsg != null) {
            return "DUPLICATE:" + dupMsg;
        }

        // 2. 生成单号
        String ticketNo = generateTicketNo(categoryId);

        // 3. 写库
        BizTicket ticket = new BizTicket();
        ticket.setTicketNo(ticketNo);
        ticket.setTitle(title);
        ticket.setDescription(description);
        ticket.setCategoryId(categoryId);
        ticket.setPriority(priority);
        ticket.setStatus(1);
        ticket.setSource("AI_AGENT");
        ticket.setRequesterId(userId);
        ticket.setAiSessionId(sessionId != null ? sessionId.toString() : null);
        assignDefaultGroup(ticket);
        ticketMapper.insert(ticket);

        return String.format(
                "已为您创建报修工单【%s】\n编号：%s\n分类：%s\n优先级：%s\n当前状态：待处理\n预计响应时间：%s",
                ticket.getTitle(),
                ticket.getTicketNo(),
                getCategoryName(ticket.getCategoryId()),
                mapPriority(ticket.getPriority()),
                getSlaResponseTime(ticket.getCategoryId())
        );
    }

    private String generateTicketNo(Long categoryId) {
        BizTicketCategory category = categoryMapper.selectById(categoryId);
        String code = category != null ? category.getCode().toUpperCase() : "BX";
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String redisKey = "ticket:seq:" + code + ":" + dateStr;
        RAtomicLong atomicLong = redissonClient.getAtomicLong(redisKey);

        QueryWrapper<BizTicket> wrapper = new QueryWrapper<>();
        wrapper.likeRight("ticket_no", code + "-" + dateStr + "-");
        wrapper.orderByDesc("ticket_no");
        wrapper.last("LIMIT 1");

        BizTicket lastTicket = ticketMapper.selectOne(wrapper);
        int dbMaxSeq = 0;
        if (lastTicket != null && lastTicket.getTicketNo() != null) {
            String no = lastTicket.getTicketNo();
            String seqStr = no.substring(no.lastIndexOf('-') + 1);
            try {
                dbMaxSeq = Integer.parseInt(seqStr);
            } catch (NumberFormatException e) {
                dbMaxSeq = 0;
            }
        }

        if (!atomicLong.isExists() || atomicLong.get() < dbMaxSeq) {
            atomicLong.set(dbMaxSeq);
            atomicLong.expire(2, TimeUnit.DAYS);
        }

        long nextSeq = atomicLong.incrementAndGet();
        return String.format("%s-%s-%03d", code, dateStr, nextSeq);
    }

    private void assignDefaultGroup(BizTicket ticket) {
        BizTicketCategory category = categoryMapper.selectById(ticket.getCategoryId());
        if (category != null && category.getDefaultGroupId() != null) {
            ticket.setGroupId(category.getDefaultGroupId());
        }
    }

    private String getCategoryName(Long categoryId) {
        BizTicketCategory category = categoryMapper.selectById(categoryId);
        return category != null ? category.getName() : "未知分类";
    }

    private String getSlaResponseTime(Long categoryId) {
        BizTicketCategory category = categoryMapper.selectById(categoryId);
        if (category != null && category.getSlaResponseMinutes() != null) {
            return category.getSlaResponseMinutes() + " 分钟";
        }
        return "30 分钟";
    }

    private String mapPriority(Integer priority) {
        return switch (priority) {
            case 1 -> "紧急";
            case 2 -> "普通";
            case 3 -> "低";
            default -> "普通";
        };
    }
}