package top.lingxi.campus.itAgent.context;

import lombok.Builder;
import lombok.Data;
import top.lingxi.campus.result.IntentDetectionResult;

import java.util.HashMap;
import java.util.Map;


/**
 * 一次对话请求的完整上下文
 * 贯穿整个处理链路，避免方法签名参数爆炸
 */
@Data
@Builder
public class ChatContext {

    /** 领域：IT / HR / ADMIN */
    private String domain;

    /** 当前用户ID */
    private Long userId;

    /** 会话分组ID（AI会话分组，非业务领域） */
    private Long groupId;

    /** 前端传入的原始 sessionId（可能为null） */
    private Long sessionId;

    /** 创建或复用后的真实 sessionId */
    private Long finalSessionId;

    /** 用户输入 */
    private String prompt;

    /** 是否启用思考过程 */
    private Boolean enableThinking;

    /** 思考token预算 */
    private Integer thinkingBudget;

    /** 模型名称 */
    private String model;

    /** 是否是本次请求新创建的会话 */
    private boolean newSession;

    /** 意图识别结果（由 Handler 链前置步骤填充） */
    private IntentDetectionResult intent;

    /**
     * Handler 显式设置的要保存的完整回复内容
     * 用于非流式场景（如图片、报修单）覆盖自动收集的流内容
     */
    @Builder.Default
    private String responseContent = null;

    /**
     * Handler 显式设置的元数据
     * 最终会合并到保存消息的 metadata 中
     */
    @Builder.Default
    private Map<String, Object> responseMetadata = new HashMap<>();
}