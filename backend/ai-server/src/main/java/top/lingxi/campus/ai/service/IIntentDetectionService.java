package top.lingxi.campus.ai.service;

import reactor.core.publisher.Mono;
import top.lingxi.campus.result.IntentDetectionResult;

public interface IIntentDetectionService {

    /**
     * 意图识别（兼容旧接口，默认 IT 领域）
     */
    IntentDetectionResult analyzeIntent(String userMessage);

    /**
     * 带领域过滤的意图识别
     *
     * @param userMessage 用户消息
     * @param domain      业务领域（IT/HR/ADMIN）
     */
    IntentDetectionResult analyzeIntent(String userMessage, String domain);

    /** 异步版本（WebFlux 推荐） */
    Mono<IntentDetectionResult> analyzeIntentReactive(String userMessage, String domain);
}