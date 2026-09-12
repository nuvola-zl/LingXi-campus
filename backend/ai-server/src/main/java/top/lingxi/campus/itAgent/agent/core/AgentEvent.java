package top.lingxi.campus.itAgent.agent.core;

public record AgentEvent(
        EventType type,
        Object payload
) {
    public enum EventType {
        THINK, TOOL_CALL, TOOL_RESULT, STATE_TRANSITION, RESPOND, COMPLETE
    }

    public static AgentEvent respond(String text) {
        return new AgentEvent(EventType.RESPOND, text);
    }

    public static AgentEvent transition(String stateName) {
        return new AgentEvent(EventType.STATE_TRANSITION, stateName);
    }

    public static AgentEvent complete() {
        return new AgentEvent(EventType.COMPLETE, "");
    }

    public static AgentEvent toolCall(String toolName, Object input) {
        return new AgentEvent(EventType.TOOL_CALL, new ToolCall(toolName, input));
    }

    public record ToolCall(String name, Object input) {}
}