package top.lingxi.campus.itAgent.intent.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import top.lingxi.campus.ai.service.impl.IntentKeywordService;
import top.lingxi.campus.result.IntentDetectionResult;


import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class KeywordIntentStrategy implements IntentStrategy {

    private final IntentKeywordService intentKeywordService;

    @Override
    public Mono<IntentDetectionResult> detect(String userMessage, String domain) {
        String msg = userMessage.toLowerCase();

        // 第1层：查 ai_intent_keyword 表
        String keywordIntent = intentKeywordService.matchKeyword(userMessage);
        if (keywordIntent != null) {
            log.info("[KeywordStrategy] 数据库关键词命中: intent={}", keywordIntent);
            IntentDetectionResult result = switch (keywordIntent) {
                case "ticket_abandon" -> IntentDetectionResult.abandonCreate();
                case "ticket_resolved" -> IntentDetectionResult.ticketClose();
                case "ticket_create" -> IntentDetectionResult.ticketCreate();
                case "ticket_query" -> IntentDetectionResult.ticketQuery(extractTicketNo(userMessage));
                case "ticket_urgent" -> IntentDetectionResult.urgent();
                case "ticket_close" -> IntentDetectionResult.ticketClose();
                case "continue" -> IntentDetectionResult.text();
                default -> null;
            };
            if (result != null) {
                return Mono.just(result);
            }
        }

        // 第2层：生图硬编码（保留原逻辑）
        if (msg.contains("生成图片") || msg.contains("画一张") || msg.contains("画个")
                || msg.contains("生成图像") || msg.contains("作图") || msg.contains("画图")) {
            log.info("[KeywordStrategy] 生图关键词命中");
            return Mono.just(IntentDetectionResult.imageGeneration(userMessage));
        }

        return Mono.empty();
    }

    private String extractTicketNo(String message) {
        Pattern pattern = Pattern.compile("[A-Z]{2}-\\d{8}-\\d{3}");
        Matcher matcher = pattern.matcher(message);
        return matcher.find() ? matcher.group() : null;
    }
}