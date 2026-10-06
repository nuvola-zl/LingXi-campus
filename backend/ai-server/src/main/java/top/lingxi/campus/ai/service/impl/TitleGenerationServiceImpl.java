package top.lingxi.campus.ai.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import top.lingxi.campus.common.constant.PromptConstant;
import top.lingxi.campus.common.constant.RoleConstant;
import top.lingxi.campus.domain.ai.entity.ChatMessage;
import top.lingxi.campus.chat.history.service.IChatMessageService;
import top.lingxi.campus.chat.session.service.IChatSessionService;
import top.lingxi.campus.ai.service.ITitleGenerationService;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TitleGenerationServiceImpl implements ITitleGenerationService {

    private final IChatSessionService chatSessionService;
    private final DashScopeChatModel chatModel;

    private static final int MAX_TITLE_LENGTH = 30;

    @Override
    public String generateTitle(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            log.warn("消息列表为空，返回默认标题");
            return "新对话";
        }
        try {
            StringBuilder conversationText = new StringBuilder();
            for (ChatMessage msg : messages) {
                String role = RoleConstant.USER.equals(msg.getRole()) ? "用户" : "AI";
                conversationText.append(role).append("：").append(msg.getContent()).append("\n");
            }
            return callLlmForTitle(conversationText.toString());
        } catch (Exception e) {
            log.error("标题生成异常", e);
            return "新对话";
        }
    }

    @Async
    @Override
    public void generateAndUpdateTitle(Long sessionId, String userContent, String aiContent) {
        try {
            // [fix] 内容直接传参，避免异步线程在外层事务提交前读库导致首轮消息不全
            String conversationText = "用户：" + userContent + "\nAI：" + (aiContent == null ? "" : aiContent);
            String title = callLlmForTitle(conversationText);
            chatSessionService.updateSession(sessionId, title, null);
            log.info("标题生成并更新成功: sessionId={}, title={}", sessionId, title);
        } catch (Exception e) {
            log.error("异步标题生成失败: sessionId={}", sessionId, e);
        }
    }

    /** LLM 生成标题的核心逻辑（两种数据源共用） */
    private String callLlmForTitle(String conversationText) {
        String promptText = String.format(
                PromptConstant.SESSION_TITLE_GENERATE_PROMPT_V2,
                MAX_TITLE_LENGTH,
                conversationText
        );

        Prompt prompt = new Prompt(promptText);
        ChatResponse response = chatModel.call(prompt);

        if (response != null && response.getResult() != null
                && response.getResult().getOutput() != null) {

            String title = response.getResult().getOutput().getText().trim();
            title = title.replaceAll("^[\"']|[\"']$", "");   // 去引号

            if (title.length() > MAX_TITLE_LENGTH) {
                title = title.substring(0, MAX_TITLE_LENGTH) + "...";
            }
            log.info("标题生成成功: {}", title);
            return title;
        }

        log.warn("标题生成失败，返回默认标题");
        return "新对话";
    }
}