package top.lingxi.campus.test;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.lingxi.campus.domain.biz.ticket.entity.BizTicket;
import top.lingxi.campus.domain.biz.ticket.entity.BizTicketComment;
import top.lingxi.campus.domain.biz.ticket.mapper.BizTicketCommentMapper;
import top.lingxi.campus.domain.biz.ticket.mapper.BizTicketMapper;

import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest
public class FeedbackPromptTest {

    @Autowired
    private BizTicketMapper ticketMapper;
    
    @Autowired
    private BizTicketCommentMapper commentMapper;

//    @Test
//    public void testPrintSummary() {
//        Long ticketId = 14L;  // ← 你刚才的工单ID
//
//        // 1. 读工单
//        BizTicket ticket = ticketMapper.selectById(ticketId);
//        System.out.println("===== 工单信息 =====");
//        System.out.println("标题：" + ticket.getTitle());
//        System.out.println("描述：" + ticket.getDescription());
//
//        // 2. 读处理记录
//        List<BizTicketComment> comments = commentMapper.selectByTicketId(ticketId);
//        System.out.println("\n===== 处理记录 =====");
//        for (BizTicketComment c : comments) {
//            System.out.println("[" + c.getType() + "] " + c.getContent());
//        }
//
//        // 3. 拼接成 Prompt 给 LLM（模拟正反馈的逻辑）
//        String rawRecord = buildRawRecord(ticket, comments);
//        System.out.println("\n===== 给 LLM 的 Prompt =====");
//        System.out.println(rawRecord);
//
//        // 4. TODO：这里后面会调真正的 LLM，现在先看原始数据长什么样
//        System.out.println("\n===== 数据准备完毕，工单ID：" + ticketId + " =====");
//    }
//
//    private String buildRawRecord(BizTicket ticket, List<BizTicketComment> comments) {
//        StringBuilder sb = new StringBuilder();
//        sb.append("工单标题：").append(ticket.getTitle()).append("\n");
//        sb.append("问题描述：").append(ticket.getDescription()).append("\n");
//        sb.append("处理记录：\n");
//
//        List<BizTicketComment> processComments = comments.stream()
//                .filter(c -> "COMMENT".equals(c.getType()))
//                .collect(Collectors.toList());
//
//        for (int i = 0; i < processComments.size(); i++) {
//            sb.append(i + 1).append(". ").append(processComments.get(i).getContent()).append("\n");
//        }
//
//        return sb.toString();
//    }

    @Autowired
    private DashScopeChatModel chatModel;  // ← 新增：调 LLM

    @Test
    public void testPrintSummary() {
        Long ticketId = 14L;

        BizTicket ticket = ticketMapper.selectById(ticketId);
        List<BizTicketComment> comments = commentMapper.selectByTicketId(ticketId);

        // 拼接 Prompt
        String rawRecord = buildRawRecord(ticket, comments);

        // 调 LLM 总结
        String systemPrompt = """
            你是一个企业 IT 运维知识库整理专家。请根据以下工单处理记录，提炼成一篇标准解决方案文档。
            
            要求：
            1. 标题格式：《问题关键词》解决方案
            2. 内容结构：
               - 问题现象（1句话）
               - 解决步骤（编号，具体可操作）
            3. 如果处理记录无法提炼有效步骤，只返回：UNQUALIFIED
            4. 去除人名、时间
            
            请直接输出文档内容，不要解释。
            """;

        String fullPrompt = systemPrompt + "\n\n" + rawRecord;

        // 调用 LLM
        org.springframework.ai.chat.prompt.Prompt prompt =
                new org.springframework.ai.chat.prompt.Prompt(fullPrompt);
        var response = chatModel.call(prompt);
        String summary = response.getResult().getOutput().getText().trim();

        System.out.println("===== AI 总结结果 =====");
        System.out.println(summary);
    }

    private String buildRawRecord(BizTicket ticket, List<BizTicketComment> comments) {
        StringBuilder sb = new StringBuilder();
        sb.append("工单标题：").append(ticket.getTitle()).append("\n");
        sb.append("问题描述：").append(ticket.getDescription()).append("\n");
        sb.append("处理记录：\n");

        List<BizTicketComment> processComments = comments.stream()
                .filter(c -> "COMMENT".equals(c.getType()))
                .collect(Collectors.toList());

        for (int i = 0; i < processComments.size(); i++) {
            sb.append(i + 1).append(". ").append(processComments.get(i).getContent()).append("\n");
        }

        return sb.toString();
    }


}