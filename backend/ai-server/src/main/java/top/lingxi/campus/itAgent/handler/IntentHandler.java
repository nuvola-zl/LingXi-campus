package top.lingxi.campus.itAgent.handler;

import reactor.core.publisher.Flux;
import top.lingxi.campus.itAgent.context.ChatContext;


import java.util.List;

/**
 * 意图处理器接口
 * 每个领域（IT/HR/ADMIN）由一条 Handler 链组成，按优先级匹配，第一个命中的执行
 */
public interface IntentHandler {

    /**
     * 是否支持处理当前请求
     * 注意：这里只判断"业务条件"是否满足，domain 匹配由 HandlerRegistry 保证
     */
    boolean supports(ChatContext ctx);

    /**
     * 执行业务逻辑，返回 SSE 流
     */
    Flux<String> handle(ChatContext ctx);

    /**
     * 该 Handler 支持的业务领域
     * 例：["IT"] 或 ["IT","HR","ADMIN"]
     */
    List<String> getDomains();

    /**
     * 优先级，数字越小越优先匹配
     * 1=最高优先级（如状态机拦截），99=兜底
     */
    int getOrder();
}