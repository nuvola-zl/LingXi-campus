package top.lingxi.campus.itAgent.handler;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.lingxi.campus.itAgent.context.ChatContext;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handler 注册器
 * 收集所有 IntentHandler，按 domain 分组并按优先级排序
 */
@Slf4j
@Component
public class HandlerRegistry {

    private final List<IntentHandler> allHandlers;
    private final Map<String, List<IntentHandler>> domainChains = new HashMap<>();

    public HandlerRegistry(List<IntentHandler> allHandlers) {
        this.allHandlers = allHandlers;
    }

    @PostConstruct
    public void init() {
        for (IntentHandler handler : allHandlers) {
            for (String domain : handler.getDomains()) {
                String key = domain.toUpperCase();
                domainChains.computeIfAbsent(key, k -> new ArrayList<>()).add(handler);
            }
        }
        // 每个领域的链按优先级排序
        domainChains.values().forEach(list -> list.sort(Comparator.comparingInt(IntentHandler::getOrder)));
        
        // 打印日志，方便启动时确认
        domainChains.forEach((domain, chain) -> {
            String names = chain.stream()
                    .map(h -> h.getClass().getSimpleName() + "(order=" + h.getOrder() + ")")
                    .collect(Collectors.joining(" → "));
            log.info("[HandlerRegistry] {} 领域链路: {}", domain, names);
        });
    }

    /**
     * 获取指定领域的 Handler 链
     */
    public List<IntentHandler> getChain(String domain) {
        return domainChains.getOrDefault(domain.toUpperCase(), Collections.emptyList());
    }

    /**
     * 在指定领域链中找到第一个匹配的 Handler
     */
    public Optional<IntentHandler> findFirstMatch(ChatContext ctx) {
        List<IntentHandler> chain = getChain(ctx.getDomain());
        for (IntentHandler handler : chain) {
            if (handler.supports(ctx)) {
                log.debug("[HandlerRegistry] domain={} 命中: {}", ctx.getDomain(), handler.getClass().getSimpleName());
                return Optional.of(handler);
            }
        }
        log.warn("[HandlerRegistry] domain={} 未匹配任何 Handler", ctx.getDomain());
        return Optional.empty();
    }
}