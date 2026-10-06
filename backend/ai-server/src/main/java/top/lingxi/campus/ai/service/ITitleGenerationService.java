package top.lingxi.campus.ai.service;

import top.lingxi.campus.domain.ai.entity.ChatMessage;

import java.util.List;


public interface ITitleGenerationService {
    
    /**
     * 根据对话内容生成标题
     * @return 生成的标题
     */
    String generateTitle(List<ChatMessage> messages);
    
    /**
     * 异步生成并更新会话标题
     * @param sessionId 会话ID
     */
    void generateAndUpdateTitle(Long sessionId, String userContent, String aiContent);
}
