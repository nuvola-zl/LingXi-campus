package top.lingxi.campus.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 工具调用统一网关（安全 + 治理收敛入口）
 *
 * 设计说明（对应生产实践的"工具调用拦截链路"轻量落地）：
 * 1. 统一审计：谁调用了什么工具、入参、耗时、结果长度，一条日志全记录
 * 2. 异常兜底：工具抛异常不再直接炸给框架，翻译为友好提示由 LLM 转述
 * 3. 超时保护：默认 10 秒，超时返回兜底文案（注：Java 线程不能真正中断，
 *    超时后原线程仍会在后台跑完，此处保护的是"用户侧不卡死"）
 * 4. 留扩展点：限流、熔断可在本类按工具名维度添加
 *
 * ⚠️ 调用约定：工具方法内若用到 ThreadLocal 上下文（如 BaseContext 的用户 ID），
 * 必须在提交 lambda 之前捕获，因为 lambda 在网关线程池执行。
 */
@Slf4j
@Component
public class ToolCallGateway {

    private static final long DEFAULT_TIMEOUT_SECONDS = 10;

    /** 工具执行专用线程池（工具多为 DB 阻塞操作，独立池隔离，不用 commonPool） */
    private final ExecutorService toolExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "tool-gateway");
        t.setDaemon(true);
        return t;
    });

    /**
     * 执行工具调用（带治理）
     *
     * @param toolName 工具名（审计用）
     * @param args     入参摘要（审计用，勿传敏感信息）
     * @param action   工具实际逻辑
     * @return 工具结果；异常/超时返回兜底文案
     */
    public String execute(String toolName, Map<String, Object> args, Supplier<String> action) {
        long start = System.currentTimeMillis();
        log.info("[ToolGateway] 调用开始: tool={}, args={}", toolName, args);

        try {
            String result = CompletableFuture.supplyAsync(action, toolExecutor)
                    .orTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .join();

            long cost = System.currentTimeMillis() - start;
            log.info("[ToolGateway] 调用成功: tool={}, cost={}ms, resultLength={}",
                    toolName, cost, result != null ? result.length() : 0);
            return result;

        } catch (CompletionException e) {
            // orTimeout 触发 TimeoutException 包装在 CompletionException 中
            log.error("[ToolGateway] 调用超时(>{}s): tool={}", DEFAULT_TIMEOUT_SECONDS, toolName);
            return "操作处理时间较长，请稍后重试或联系管理员。";
        } catch (Exception e) {
            log.error("[ToolGateway] 调用异常: tool={}", toolName, e);
            return "系统繁忙，请稍后重试";
        }
    }
}