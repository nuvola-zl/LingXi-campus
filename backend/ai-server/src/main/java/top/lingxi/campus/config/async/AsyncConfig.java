package top.lingxi.campus.config.async;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 异步配置
 *
 * 重构变更（DAG 移除）：删除 dagExecutor 线程池 Bean（DAG 编排引擎的唯一消费方）。
 * 保留 @EnableAsync——项目其他 @Async 方法（如工单知识沉淀监听）依赖它。
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}