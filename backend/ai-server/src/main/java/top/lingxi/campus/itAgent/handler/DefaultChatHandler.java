package top.lingxi.campus.itAgent.handler;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import top.lingxi.campus.common.constant.RoleConstant;
import top.lingxi.campus.itAgent.context.ChatContext;
import top.lingxi.campus.domain.ai.entity.ChatMessage;
import top.lingxi.campus.chat.history.service.IChatMessageService;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultChatHandler implements IntentHandler {

    private final ChatModel textChatModel;
    private final IChatMessageService chatMessageService;

    @Override
    public List<String> getDomains() {
        return List.of("IT", "HR", "ADMIN");
    }

    @Override
    public int getOrder() {
        return 99; // 兜底，最低优先级
    }

    @Override
    public boolean supports(ChatContext ctx) {
        // 兜底 Handler，始终支持
        return true;
    }

    @Override
    public Flux<String> handle(ChatContext ctx) {
        Long sessionId = ctx.getFinalSessionId();

        // 1. 构建历史消息（最近10条）
        List<Message> historyMessages = new ArrayList<>();
        if (!ctx.isNewSession()) {
            List<ChatMessage> dbMessages = chatMessageService.getMessagesBySessionId(sessionId, 10);
            for (ChatMessage msg : dbMessages) {
                if (RoleConstant.USER.equals(msg.getRole())) {
                    historyMessages.add(new UserMessage(msg.getContent()));
                } else if (RoleConstant.ASSISTANT.equals(msg.getRole())) {
                    historyMessages.add(new AssistantMessage(msg.getContent()));
                }
            }
        }
        historyMessages.add(new UserMessage(ctx.getPrompt()));

        // 2. 构建运行时选项
        DashScopeChatOptions runtimeOptions = DashScopeChatOptions.builder()
                .model(ctx.getModel() != null ? ctx.getModel() : "qwen-flash")
                .enableThinking(ctx.getEnableThinking())
                .build();
        if (ctx.getThinkingBudget() != null) {
            runtimeOptions.setMaxTokens(ctx.getThinkingBudget());
        }

        // 3. 流式调用
        Prompt chatPrompt = new Prompt(historyMessages, runtimeOptions);
        Flux<ChatResponse> chatResponseFlux = textChatModel.stream(chatPrompt);

        // 4. 解析流式响应
        StringBuilder fullReasoningBuilder = new StringBuilder();

        return chatResponseFlux
                .concatMap(chatResponse -> {
                    if (chatResponse.getResults() == null || chatResponse.getResults().isEmpty()) {
                        return Flux.empty();
                    }
                    var output = chatResponse.getResults().get(0).getOutput();
                    var chunkMetadata = output.getMetadata();
                    String text = output.getText();

                    // 诊断日志
                    if (!chunkMetadata.isEmpty()) {
                        log.debug("[chunk-meta] keys={} | reasoningContent={}",
                                chunkMetadata.keySet(),
                                chunkMetadata.get("reasoningContent"));
                    }

                    // 提取思考内容（DashScope 使用 camelCase key: "reasoningContent"）
                    String thinkingChunk = null;
                    Object raw = chunkMetadata.get("reasoningContent");
                    if (raw instanceof String s && StringUtils.hasText(s)) {
                        thinkingChunk = s;
                    }

                    List<String> results = new ArrayList<>();
                    if (thinkingChunk != null) {
                        fullReasoningBuilder.append(thinkingChunk);
                        //results.add(thinkingChunk);//todo 后续再处理思考内容
                        // thinking 只收集到 metadata，不输出到流
                    }
//                    if (StringUtils.hasText(text)) {
//                        results.add(text);
//                    }
                    if (StringUtils.hasText(text)) {
                        // 关键修复：过滤 text 中可能包含的  t...d  标签内容
                        String cleanedText = removeThinkTags(text);
                        if (StringUtils.hasText(cleanedText)) {
                            results.add(cleanedText);
                        }
                    }
                    return Flux.fromIterable(results);
                })
                .doOnComplete(() -> {
                    // 将元数据放入上下文，供 Pipeline 统一保存
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("model", ctx.getModel() != null ? ctx.getModel() : "qwen-plus");
                    metadata.put("enable_thinking", ctx.getEnableThinking());
                    metadata.put("reasoning_content", fullReasoningBuilder.toString());
                    metadata.put("thinking_budget", ctx.getThinkingBudget());
                    ctx.setResponseMetadata(metadata);
                })
                .doOnError(error -> log.error("流式对话出错", error));
    }

    /**
     * 移除  t...d  标签及其包裹的思考内容（支持多行）
     */
    private String removeThinkTags(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        // (?s) 开启 DOTALL 模式，使 . 能匹配换行符
        // 匹配  t...d  开头到  d  结尾的所有内容
        return text.replaceAll("(?s)<think>.*?</think>", "").trim();
    }
}