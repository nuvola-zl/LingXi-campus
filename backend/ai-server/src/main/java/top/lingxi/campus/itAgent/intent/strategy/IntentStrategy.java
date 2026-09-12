package top.lingxi.campus.itAgent.intent.strategy;

import reactor.core.publisher.Mono;
import top.lingxi.campus.result.IntentDetectionResult;

public interface IntentStrategy {
    /**
     * 执行意图识别
     * @return Mono.empty() 表示未识别到，继续下一个策略
     */
    Mono<IntentDetectionResult> detect(String userMessage, String domain);
}