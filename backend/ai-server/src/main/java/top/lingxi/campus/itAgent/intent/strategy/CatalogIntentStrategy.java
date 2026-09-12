package top.lingxi.campus.itAgent.intent.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import top.lingxi.campus.rag.cache.CatalogKeywordService;
import top.lingxi.campus.result.IntentDetectionResult;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogIntentStrategy implements IntentStrategy {

    private final CatalogKeywordService catalogKeywordService;

    @Override
    public Mono<IntentDetectionResult> detect(String userMessage, String domain) {
        String msg = userMessage.toLowerCase();
        CatalogKeywordService.MatchResult match = catalogKeywordService.match(userMessage);

        if (match != null) {
            log.info("[CatalogStrategy] 服务目录命中: domain={}, categoryId={}",
                    match.getDomain(), match.getCategoryId());

            IntentDetectionResult result = new IntentDetectionResult();

            // IT 域统一 ticket_create（保持与原逻辑一致）
            if ("IT".equalsIgnoreCase(domain)) {
                result.setIntent("ticket_create");
            } else {
                boolean hasProblem = msg.contains("申请") || msg.contains("无法")
                        || msg.contains("报错") || msg.contains("失败");
                result.setIntent(hasProblem ? "ticket_create" : "knowledge_qa");
            }

            result.setDomain(match.getDomain());
            result.setCategoryId(match.getCategoryId());
            result.setDefaultPriority(match.getPriority());
            return Mono.just(result);
        }

        return Mono.empty();
    }
}