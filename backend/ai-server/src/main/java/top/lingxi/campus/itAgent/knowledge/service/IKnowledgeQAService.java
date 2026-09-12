package top.lingxi.campus.itAgent.knowledge.service;

import reactor.core.publisher.Flux;

public interface IKnowledgeQAService {
    Flux<String> answer(Long userId, String domain, String query);
}