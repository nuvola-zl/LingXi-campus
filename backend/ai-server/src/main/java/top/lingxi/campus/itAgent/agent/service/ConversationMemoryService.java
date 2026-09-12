package top.lingxi.campus.itAgent.agent.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import top.lingxi.campus.itAgent.state.TicketCreateState;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationMemoryService {

    private final StringRedisTemplate redisTemplate;
    private final DashScopeChatModel chatModel;

    private static final String MEMORY_KEY_PREFIX = "agent_memory:";
    private static final int SUMMARIZE_THRESHOLD = 6; // 6轮后触发摘要

    /**
     * 如果对话轮数超过阈值，生成摘要并存入 Redis（2小时有效）
     */
    public void updateMemory(Long sessionId, List<TicketCreateState.ChatTurn> history) {
        if (history == null || history.size() < SUMMARIZE_THRESHOLD) {
            return;
        }

        String summary = generateSummary(history);
        String key = MEMORY_KEY_PREFIX + sessionId;
        redisTemplate.opsForValue().set(key, summary, Duration.ofHours(2));
        log.info("[Memory] 对话摘要已生成: sessionId={}, length={}", sessionId, summary.length());
    }

    /**
     * 获取历史摘要
     */
    public String getMemory(Long sessionId) {
        return redisTemplate.opsForValue().get(MEMORY_KEY_PREFIX + sessionId);
    }

    private String generateSummary(List<TicketCreateState.ChatTurn> history) {
        String historyText = history.stream()
                .map(h -> (h.getRole().equals("ai") ? "AI: " : "用户: ") + h.getContent())
                .collect(Collectors.joining("\n"));

        String promptText = """
            请把以下对话总结为100字以内的结构化摘要，保留关键信息：
            - 用户遇到的核心问题
            - 已经尝试过的解决方案及结果
            - 当前进展（是否已解决/是否在建单/等待什么）

            对话记录：
            """ + historyText;

        try {
            Prompt prompt = new Prompt(promptText);
            ChatResponse response = chatModel.call(prompt);
            return response.getResult().getOutput().getText().trim();
        } catch (Exception e) {
            log.error("[Memory] 摘要生成失败", e);
            return "对话摘要生成失败";
        }
    }
}