//package top.hazenix.hazeaihub.test;
//
//import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//
//
//import top.hazenix.hazeaihub.Feedback.TicketKnowledgeFeedbackService;
//import top.hazenix.hazeaihub.domain.biz.ticket.entity.BizTicket;
//import top.hazenix.hazeaihub.domain.biz.ticket.entity.BizTicketComment;
//import top.hazenix.hazeaihub.domain.biz.ticket.mapper.BizTicketCommentMapper;
//import top.hazenix.hazeaihub.domain.biz.ticket.mapper.BizTicketMapper;
//
//import java.time.LocalDateTime;
//import java.util.List;
//import java.util.stream.Collectors;
//
//@SpringBootTest
//public class MockDataTest {
//
//    @Autowired
//    private BizTicketMapper ticketMapper;
//
//    @Autowired
//    private BizTicketCommentMapper commentMapper;
//
//    @Autowired
//    private DashScopeChatModel chatModel;  // ← 新增：调 LLM
//
//    @Autowired
//    private TicketKnowledgeFeedbackService feedbackService;  // ← 新增
//
//
////    @Test
////    public void addTestData() {
////        // 造一条"打印机坏了"的工单
////        BizTicket ticket = new BizTicket();
////        ticket.setTicketNo("IT-20260717-001");
////        ticket.setTitle("打印机坏了");
////        ticket.setDescription("打印机卡纸，无法打印");
////        ticket.setCategoryId(1L);      // IT分类
////        ticket.setPriority(2);          // 普通
////        ticket.setStatus(4);            // 已关闭（直接关单，方便测试）
////        ticket.setSource("AI");
////        ticket.setRequesterId(4L);    // 小王
////        ticket.setAssigneeId(2L);       // 小李
////        ticket.setGroupId(1L);          // IT组
////        ticket.setCreatedAt(LocalDateTime.now());
////        ticket.setUpdatedAt(LocalDateTime.now());
////        ticket.setClosedAt(LocalDateTime.now());
////        ticketMapper.insert(ticket);
////
////        // 加处理记录：工程师写的步骤
////        addComment(ticket.getId(), 2L, "COMMENT", "打开打印机后盖，取出卡纸碎片");
////        addComment(ticket.getId(), 2L, "COMMENT", "清理进纸通道，重新装纸");
////        addComment(ticket.getId(), 2L, "COMMENT", "测试打印正常，已恢复使用");
////
////        // 系统关单记录
////        addComment(ticket.getId(), 2L, "SYSTEM", "用户确认问题解决，工单关闭");
////
////        System.out.println("✅ 测试数据已插入，工单ID：" + ticket.getId());
////    }
//
//
//
//    @Test
//    public void testBadData() {
//        // 造一条"很水"的工单
//        BizTicket ticket = new BizTicket();
//        ticket.setTicketNo("IT-" + System.currentTimeMillis());  // ← 动态，避免重复
//        ticket.setTitle("电脑蓝屏");
//        ticket.setDescription("电脑突然蓝屏");
//        ticket.setCategoryId(1L);
//        ticket.setPriority(2);
//        ticket.setStatus(4);
//        ticket.setSource("AI");
//        ticket.setRequesterId(4L);
//        ticket.setAssigneeId(2L);
//        ticket.setGroupId(1L);
//        ticket.setCreatedAt(LocalDateTime.now());
//        ticket.setUpdatedAt(LocalDateTime.now());
//        ticket.setClosedAt(LocalDateTime.now());
//        ticketMapper.insert(ticket);
//
//        // 只写一条很水的记录
//        addComment(ticket.getId(), 2L, "COMMENT", "重启后好了");
//        addComment(ticket.getId(), 2L, "SYSTEM", "用户确认问题解决，工单关闭");
//
//        System.out.println("垃圾数据工单ID：" + ticket.getId());
//
//        // 触发正反馈（关键！）
//        feedbackService.onTicketClosed(
//                new top.hazenix.hazeaihub.Event.TicketClosedEvent(ticket.getId())
//        );
//
//        // 等异步处理完成
//        try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
//
//        System.out.println("✅ 正反馈处理完成");
//    }
//    private void addComment(Long ticketId, Long userId, String type, String content) {
//        BizTicketComment c = new BizTicketComment();
//        c.setTicketId(ticketId);
//        c.setUserId(userId);
//        c.setContent(content);
//        c.setType(type);
//        c.setCreatedAt(LocalDateTime.now());
//        commentMapper.insert(c);
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
//
//}

package top.lingxi.campus.test;

import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import top.lingxi.campus.itAgent.Event.TicketClosedEvent;
import top.lingxi.campus.rag.Feedback.TicketKnowledgeFeedbackService;
import top.lingxi.campus.domain.biz.ticket.entity.BizTicket;
import top.lingxi.campus.domain.biz.ticket.entity.BizTicketComment;
import top.lingxi.campus.domain.biz.ticket.mapper.BizTicketCommentMapper;
import top.lingxi.campus.domain.biz.ticket.mapper.BizTicketMapper;

import java.time.LocalDateTime;
import java.util.List;

@SpringBootTest
public class MockDataTest {

    @Autowired
    private BizTicketMapper ticketMapper;

    @Autowired
    private BizTicketCommentMapper commentMapper;

    @Autowired
    private TicketKnowledgeFeedbackService feedbackService;

    /**
     * 测试去重逻辑：连续造5条工单，看正反馈怎么处理
     */
    @Test
    public void testDuplicate() {
        // 1. 打印机卡纸（第一次，应该入库）
        runOne("打印机卡纸", List.of(
                "打开打印机后盖，取出卡纸碎片",
                "清理进纸通道，重新装纸",
                "测试打印正常"
        ));

        // 2. 打印机又卡纸（几乎一样，应该跳过）
        runOne("打印机又卡纸了", List.of(
                "打开后盖取出卡纸",
                "清理通道后重新装纸",
                "打印测试通过"
        ));

        // 3. 更详细版本（应该替换旧文档）
        runOne("打印机卡纸详细处理", List.of(
                "关闭打印机电源，等待30秒散热",
                "打开后盖，小心取出卡纸碎片",
                "用软布清理进纸通道灰尘",
                "重新装纸，注意纸张对齐导轨",
                "开机测试打印，确认无异常声音"
        ));

        // 4. 蓝屏（不同问题，应该新增）
        runOne("电脑蓝屏", List.of(
                "检查错误代码 MEMORY_MANAGEMENT",
                "运行内存诊断，发现内存条故障",
                "更换内存条后测试正常"
        ));

        // 5. 蓝屏降温（同一问题不同方案，应该新增保留）
        runOne("电脑蓝屏机身发热", List.of(
                "检查机身温度异常高，CPU达90度",
                "清理散热口灰尘，更换散热硅脂",
                "测试运行正常，温度降至60度"
        ));

        System.out.println("✅ 全部跑完，看上面的日志输出！");
    }

    // 造一条工单 + 触发正反馈
    private void runOne(String title, List<String> comments) {
        System.out.println("\n========== 开始: " + title + " ==========");

        // 造工单
        BizTicket ticket = new BizTicket();
        ticket.setTicketNo("IT-" + System.currentTimeMillis());
        ticket.setTitle(title);
        ticket.setDescription(title);
        ticket.setCategoryId(1L);
        ticket.setPriority(2);
        ticket.setStatus(4);
        ticket.setSource("AI");
        ticket.setRequesterId(4L);
        ticket.setAssigneeId(2L);
        ticket.setGroupId(1L);
        ticket.setCreatedAt(LocalDateTime.now());
        ticket.setUpdatedAt(LocalDateTime.now());
        ticket.setClosedAt(LocalDateTime.now());
        ticketMapper.insert(ticket);

        // 加处理记录
        for (String content : comments) {
            addComment(ticket.getId(), 2L, "COMMENT", content);
        }
        addComment(ticket.getId(), 2L, "SYSTEM", "用户确认问题解决，工单关闭");

        System.out.println("工单ID: " + ticket.getId());

        // 触发正反馈
        feedbackService.onTicketClosed(
                new TicketClosedEvent(this, ticket.getId())
        );

        // 等5秒，让异步处理完
        try { Thread.sleep(20000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        System.out.println("========== 结束: " + title + " ==========\n");
    }

    private void addComment(Long ticketId, Long userId, String type, String content) {
        BizTicketComment c = new BizTicketComment();
        c.setTicketId(ticketId);
        c.setUserId(userId);
        c.setContent(content);
        c.setType(type);
        c.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(c);
    }
}