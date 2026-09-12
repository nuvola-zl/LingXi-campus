package top.lingxi.campus.itAgent.intent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import top.lingxi.campus.itAgent.intent.strategy.CatalogIntentStrategy;
import top.lingxi.campus.itAgent.intent.strategy.KeywordIntentStrategy;
import top.lingxi.campus.itAgent.intent.strategy.LlmIntentStrategy;
import top.lingxi.campus.ai.service.IIntentDetectionService;
import top.lingxi.campus.result.IntentDetectionResult;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntentDetectionService implements IIntentDetectionService {

    private final LlmIntentStrategy llmStrategy;
    private final KeywordIntentStrategy keywordStrategy;
    private final CatalogIntentStrategy catalogStrategy;

    @Override
    public IntentDetectionResult analyzeIntent(String userMessage) {
        return analyzeIntent(userMessage, "IT");
    }

    @Override
    public IntentDetectionResult analyzeIntent(String userMessage, String domain) {
        return analyzeIntentReactive(userMessage, domain)
                .block(Duration.ofSeconds(5));
    }

    @Override
    public Mono<IntentDetectionResult> analyzeIntentReactive(String userMessage, String domain) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return Mono.just(IntentDetectionResult.text());
        }

        String finalDomain = domain != null ? domain.toUpperCase() : "IT";

        // 策略链：LLM(3秒超时) → 关键词 → 服务目录 → 兜底text
        return llmStrategy.detect(userMessage, finalDomain)
                .doOnNext(r -> log.info("[IntentChain] LLM命中: intent={}", r.getIntent()))

                .switchIfEmpty(Mono.defer(() -> keywordStrategy.detect(userMessage, finalDomain)))
                .doOnNext(r -> log.info("[IntentChain] 关键词命中: intent={}", r.getIntent()))

                .switchIfEmpty(Mono.defer(() -> catalogStrategy.detect(userMessage, finalDomain)))
                .doOnNext(r -> log.info("[IntentChain] 目录命中: intent={}, domain={}",
                        r.getIntent(), r.getDomain()))

                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("[IntentChain] 全部策略未命中，兜底 text");
                    return Mono.just(IntentDetectionResult.text());
                }))

                .map(result -> filterByDomain(result, finalDomain));
    }

    // ==================== 原 filterByDomain 保留 ====================

    private IntentDetectionResult filterByDomain(IntentDetectionResult result, String domain) {
        String intent = result.getIntent();
        if ("IT".equals(domain)) return result;

        if ("HR".equals(domain) || "ADMIN".equals(domain)) {
            if ("knowledge_qa".equals(intent)) {
                String kd = result.getDomain();
                if (kd != null && !kd.equalsIgnoreCase(domain) && !"行政".equals(kd)) {
                    return IntentDetectionResult.text();
                }
                if ("行政".equals(kd)) result.setDomain("ADMIN");
                return result;
            }
            if (intent.startsWith("ticket_") && !"ticket_abandon".equals(intent)) {
                return IntentDetectionResult.text();
            }
        }
        return result;
    }
}