package top.lingxi.campus.result;

import lombok.Data;

@Data
public class IntentDetectionResult {

    /**
     * 意图类型
     * image_generation: 生图
     * text: 普通对话
     * ticket_query: 查询报修工单状态
     */
    private String intent;

    /**
     * 生图提示词（仅 image_generation 时有值）
     */
    private String imagePrompt;

    /**
     * 从用户输入中提取的工单编号（仅 ticket_query 时有值）
     */
    private String extractedTicketNo;

    private String domain;  // 新增：IT / HR / 行政 / null

    // ===== 新增：LLM 直接返回分类信息 =====
    private Long categoryId;
    private Integer defaultPriority;

    public static IntentDetectionResult imageGeneration(String prompt) {
        IntentDetectionResult result = new IntentDetectionResult();
        result.setIntent("image_generation");
        result.setImagePrompt(prompt);
        return result;
    }

    public static IntentDetectionResult text() {
        IntentDetectionResult result = new IntentDetectionResult();
        result.setIntent("text");
        return result;
    }

    /**
     * 新增：查询报修工单状态意图
     */
    public static IntentDetectionResult ticketQuery() {
        IntentDetectionResult result = new IntentDetectionResult();
        result.setIntent("ticket_query");
        return result;
    }
    public static IntentDetectionResult ticketQuery(String ticketNo) {
        IntentDetectionResult result = new IntentDetectionResult();
        result.setIntent("ticket_query");
        result.setExtractedTicketNo(ticketNo);  // 可能为 null
        return result;
    }

    public static IntentDetectionResult knowledgeQuery(String domain) {
        IntentDetectionResult result = new IntentDetectionResult();
        result.setIntent("knowledge_qa");
        result.setDomain(domain);
        return result;
    }

    public static IntentDetectionResult ticketCreate() {
        IntentDetectionResult result = new IntentDetectionResult();
        result.setIntent("ticket_create");
        return result;
    }

    public static IntentDetectionResult abandonCreate() {
        IntentDetectionResult result = new IntentDetectionResult();
        result.setIntent("ticket_abandon");
        return result;
    }

    public static IntentDetectionResult urgent() {
        IntentDetectionResult result = new IntentDetectionResult();
        result.setIntent("ticket_urgent");
        return result;
    }

    public static IntentDetectionResult ticketClose() {
        IntentDetectionResult result = new IntentDetectionResult();
        result.setIntent("ticket_close");
        return result;
    }
}