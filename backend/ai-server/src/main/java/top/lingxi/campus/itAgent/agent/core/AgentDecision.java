package top.lingxi.campus.itAgent.agent.core;

import java.util.Map;

/**
 * LLM 调度器输出的结构化决策
 * 替代原来散落在各 State 里的硬编码 if-else
 */
public record AgentDecision(
    String reasoning,           // LLM 思考过程（留痕，方便调试）
    String action,              // RESPOND / TRANSITION / TOOL_CALL / COMPLETE
    String targetState,         // TRANSITION 时有效：COLLECTING / CONFIRMING / DIAGNOSING
    String content,             // RESPOND / COMPLETE 时：给用户的回复内容
    String toolName,            // TOOL_CALL 时：工具名
    Map<String, Object> toolInput   // TOOL_CALL 时：工具入参
) {
    public boolean isRespond()    { return "RESPOND".equals(action); }
    public boolean isTransition() { return "TRANSITION".equals(action); }
    public boolean isToolCall()   { return "TOOL_CALL".equals(action); }
    public boolean isComplete()   { return "COMPLETE".equals(action); }
}