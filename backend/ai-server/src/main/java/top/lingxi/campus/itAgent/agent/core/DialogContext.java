package top.lingxi.campus.itAgent.agent.core;

import lombok.Data;
import top.lingxi.campus.itAgent.state.TicketCreateState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 运行时的对话上下文
 * 比 ChatContext 更聚焦：只存 Agent 需要的运行时状态
 */
@Data
public class DialogContext {
    private Long userId;
    private Long sessionId;
    private String domain;

    // 运行时状态（可序列化到 Redis）
    private TicketCreateState state;

    private int llmCallCount = 0;

    // 工具调用结果缓存
    private String lastToolResult;

    // ReAct 循环保护
    private int toolCallCount = 0;
    private List<String> toolCallHistory = new ArrayList<>();

    // 是否结束
    private boolean finished = false;

    // 扩展属性
    private Map<String, Object> attributes = new HashMap<>();

    public int incrementAndGetToolCallCount() {
        return ++toolCallCount;
    }

    public void addToolResult(String toolName, Map<String, Object> args, String result) {
        StringBuilder record = new StringBuilder();
        record.append("第").append(toolCallCount).append("次调用 ").append(toolName);
        if (args != null && args.containsKey("query")) {
            record.append(" 查询词: \"").append(args.get("query")).append("\"");
        }
        record.append("\n结果: ").append(result);
        toolCallHistory.add(record.toString());
        this.lastToolResult = result;
    }

    public int incrementAndGetLlmCallCount() {
        return ++llmCallCount;
    }

    public String getToolCallHistoryText() {
        if (toolCallHistory.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\n【工具调用记录】（你已尝试过以下检索）\n");
        for (String record : toolCallHistory) {
            sb.append(record).append("\n---\n");
        }
        return sb.toString();
    }
}