package top.lingxi.campus.itAgent.agent.tool;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ToolInitializer {

    private final ToolRegistry toolRegistry;
    private final KnowledgeSearchTool knowledgeSearchTool;
    private final CreateTicketTool createTicketTool;

    @PostConstruct
    public void init() {
        toolRegistry.register(knowledgeSearchTool);
        toolRegistry.register(createTicketTool);
        log.info("[ToolInitializer] 所有 Agent 工具注册完成");
    }
}