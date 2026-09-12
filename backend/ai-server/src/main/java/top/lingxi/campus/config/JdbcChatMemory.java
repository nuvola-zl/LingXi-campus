package top.lingxi.campus.config;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import top.lingxi.campus.chat.history.service.IChatMessageService;
import top.lingxi.campus.domain.ai.entity.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据库版对话记忆
 * 不实现 Spring AI 的 ChatMemory 接口，直接查数据库拼历史
 */
@Component
@RequiredArgsConstructor
public class JdbcChatMemory {

    private final IChatMessageService chatMessageService;

    /**
     * 获取最近 N 条历史消息
     */
    public List<Message> getHistory(Long sessionId, int lastN) {
        List<ChatMessage> dbMessages = chatMessageService.getMessagesBySessionId(sessionId, lastN);
        List<Message> result = new ArrayList<>();

        for (ChatMessage msg : dbMessages) {
            if ("user".equals(msg.getRole())) {
                result.add(new UserMessage(msg.getContent()));
            } else if ("assistant".equals(msg.getRole())) {
                result.add(new AssistantMessage(msg.getContent()));
            }
        }
        return result;
    }
}