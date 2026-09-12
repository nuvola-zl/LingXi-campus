package top.lingxi.campus.itAgent.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.lingxi.campus.tool.ToolCallGateway;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 工具注册表
 *
 * 重构变更：execute 统一经过 ToolCallGateway（审计日志 + 计时 + 异常兜底 + 超时），
 * 与 HR/行政工具走同一治理入口。注册机制不变（各工具构造时自注册）。
 */
@Slf4j
@Component
public class ToolRegistry {

    private final ToolCallGateway toolGateway;
    private final Map<String, AgentTool> tools = new ConcurrentHashMap<>();

    public ToolRegistry(ToolCallGateway toolGateway) {
        this.toolGateway = toolGateway;
    }

    public void register(AgentTool tool) {
        tools.put(tool.name(), tool);
        log.info("[ToolRegistry] 工具已注册: {}", tool.name());
    }

    /**
     * 执行工具（经 ToolCallGateway 治理）
     */
    public String execute(String name, Map<String, Object> args) {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("未知工具: " + name);
        }
        return toolGateway.execute(name, args, () -> tool.execute(args));
    }

    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    public String buildToolDescriptions() {
        StringBuilder sb = new StringBuilder();
        for (AgentTool tool : tools.values()) {
            sb.append("- ").append(tool.name()).append(": ").append(tool.description()).append("\n");
        }
        return sb.toString();
    }
}