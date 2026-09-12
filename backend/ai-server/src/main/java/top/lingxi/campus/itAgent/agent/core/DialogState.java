package top.lingxi.campus.itAgent.agent.core;

import reactor.core.publisher.Flux;

public interface DialogState {
    String name();
    
    /**
     * 处理用户输入，返回事件流
     * 事件流会被 AgentStateMachine 解析并执行
     */
    Flux<AgentEvent> handle(DialogContext ctx, String userMessage);
}