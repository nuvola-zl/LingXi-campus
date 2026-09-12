package top.lingxi.campus.itAgent.state;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
* 工单创建状态（Agent 多轮对话）
* 存储在 Redis 中，由 AgentDialogService 管理
* Key: agent_dialog:{userId}
* TTL: 30 分钟
*/
@Data
public class TicketCreateState {

   /**
    * 当前状态（Agent 流程）
    * DIAGNOSING: ReAct 诊断中（检索知识库 → 生成建议 / 转建单）
    * COLLECTING: 收集问题描述
    * CONFIRMING: 等待用户确认工单信息
    */
   private String status;

   /**
    * 工单标题，AI 自动生成
    * 示例：邮箱登录异常、VPN 连接失败
    */
   private String title;

   /**
    * 问题详细描述，用户对话中提取
    * 示例：提示密码错误，但我确定没输错
    */
   private String description;

   /**
    * 分类 ID，关联 biz_ticket_category
    * 1=IT运维 2=人力资源 3=行政服务 4=财务服务
    */
   private Long categoryId;

   /**
    * 紧急度：1紧急 2普通 3低
    * 用户说"急/马上/快"时标记为 1
    */
   private Integer priority;

   /**
    * 还缺的必填字段列表
    * 示例：["description"] 或 ["description", "categoryId"]
    * 为空时表示信息已收集完整
    */
   private List<String> missingSlots;


   // ===== 新增字段 =====
   private String suggestionContent;     // RAG 生成的推荐方案
   private List<String> suggestionSources; // 引用的知识库来源
   private String originalMessage;        // 用户原始问题（保留，建单时用）


   // ===== TODO: 新增 sessionId 用于隔离不同会话的状态 =====
   // 原逻辑只用 userId 做 Redis key，导致同一用户不同会话之间状态互相污染
   // 现在加上 sessionId 维度，每个会话独立维护建单状态
   private Long sessionId;

   // ===== 新增：对话历史 =====
   private List<ChatTurn> conversationHistory = new ArrayList<>();

   /**
    * 调用次数
    */
   @Setter
   @Getter
   private int version = 0;

    @Data
   public static class ChatTurn {
      private String role;      // "ai" 或 "user"
      private String content;   // 内容
      private Long timestamp;   // 时间戳
   }

   public void addHistory(String role, String content) {
      if (this.conversationHistory == null) {
         this.conversationHistory = new ArrayList<>();
      }
      ChatTurn turn = new ChatTurn();
      turn.setRole(role);
      turn.setContent(content);
      turn.setTimestamp(System.currentTimeMillis());
      this.conversationHistory.add(turn);

      if (this.conversationHistory.size() > 6) {
         this.conversationHistory = new ArrayList<>(
                 this.conversationHistory.subList(
                         this.conversationHistory.size() - 6,
                         this.conversationHistory.size()
                 )
         );
      }
   }
}
