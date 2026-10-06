package top.lingxi.campus.itAgent.pipeline;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;
import top.lingxi.campus.common.context.BaseContext;
import top.lingxi.campus.itAgent.context.ChatContext;
import top.lingxi.campus.domain.ai.entity.ChatSession;
import top.lingxi.campus.chat.history.service.IChatMessageService;
import top.lingxi.campus.chat.session.service.IChatSessionService;
import top.lingxi.campus.ai.service.ITitleGenerationService;


import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 对话统一管道
 * 负责：前置（创建会话+保存用户消息）+ 后置（保存AI消息+更新会话+生成标题）
 * 所有 Handler 只关心业务逻辑，收尾逻辑统一由本类处理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatPipeline {

    private final IChatSessionService chatSessionService;
    private final IChatMessageService chatMessageService;
    private final ITitleGenerationService titleGenerationService;

    private final PlatformTransactionManager transactionManager;

    /**
     * 系统标记前缀集合（这些标记不会进入消息保存内容）
     */
    private static final Set<String> SYSTEM_MARKERS = Set.of(
            "SESSION_CREATED:",  "ERROR:"
    );

    // ==================== 前置处理 ====================

    /**
     * 前置处理：创建/复用会话 + 保存用户消息
     * 新会话时，chat_session.type 写入 domain（IT/HR/ADMIN），方便后续复用
     */
    public void prepare(ChatContext ctx) {

        // 事务处理：确保会话创建和消息保存原子性
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.execute(status -> {
            doPrepare(ctx);
            return null;
        });
    }

    private void doPrepare(ChatContext ctx) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }
        ctx.setUserId(userId);

        boolean isNewSession = (ctx.getSessionId() == null);
        ctx.setNewSession(isNewSession);

        Long finalSessionId;
        if (isNewSession) {
            ChatSession session = chatSessionService.createSession(userId, ctx.getDomain(), "新对话");
            finalSessionId = session.getId();

            if (ctx.getGroupId() != null) {
                chatSessionService.updateSession(finalSessionId, null, ctx.getGroupId());
            }
            log.info("创建新会话: domain={}, sessionId={}", ctx.getDomain(), finalSessionId);
        } else {
            finalSessionId = ctx.getSessionId();
        }

        ctx.setFinalSessionId(finalSessionId);
        //多轮会话必须先于llm调用，否则后续链路查询不到这个用户消息
        chatMessageService.saveUserMessage(finalSessionId, ctx.getPrompt());
    }


    /**
     * 新会话创建标记（SSE 首条事件）
     */
    public Flux<String> emitSessionCreated(Long sessionId) {
        return Flux.just("SESSION_CREATED:" + sessionId);
    }

    // ==================== 后置处理 ====================

    /**
     * 包装 Handler 返回的流，统一执行后置处理
     *
     * 收集策略：
     * 1. 流中的 <think> 标签内容会被提取到 reasoning_content
     * 2. 系统标记（SESSION_CREATED:/ERROR:）不会进入保存内容
     * 3. 如果 Handler 显式设置了 ctx.responseContent，则优先使用（覆盖自动收集）
     * 4. 如果 Handler 显式设置了 ctx.responseMetadata，会合并到最终元数据
     */
    public Flux<String> wrap(ChatContext ctx, Flux<String> flux) {
        StringBuilder contentBuilder = new StringBuilder();

        return flux
                // 便利贴1：每来一片，先收集（过滤系统标记）
                .doOnNext(chunk -> {
                    if (SYSTEM_MARKERS.stream().noneMatch(chunk::startsWith)) {
                        contentBuilder.append(chunk);
                    }
                })
                // 便利贴2：全部吐完，把收集的内容存数据库
                .doOnComplete(() -> {

                    String rawContent = ctx.getResponseContent();
                    if (rawContent == null || rawContent.isEmpty()) {
                        rawContent = contentBuilder.toString().trim();
                    }

                    String reasoningContent = extractReasoningFromThinkTags(rawContent);
                    String content = removeThinkTags(rawContent);

                    Map<String, Object> metadata = new HashMap<>(ctx.getResponseMetadata());
                    if (!reasoningContent.isEmpty()) {
                        metadata.put("reasoning_content", reasoningContent);
                    }

                    // 关键：在 doOnComplete 回调内部开启独立事务
                    finalize(ctx, content, metadata);

                    ctx.setResponseContent(null);
                    ctx.setResponseMetadata(new HashMap<>());
                })
                // 便利贴3：出错了，推错误文案兜底
                .onErrorResume(error -> {
                    log.error("流式处理异常: domain={}, sessionId={}", ctx.getDomain(), ctx.getFinalSessionId(), error);
                    return Flux.just("ERROR:服务暂时不可用");
                });
    }


    /**
     * 统一清理 think 标签（对完整文本执行，不存在跨 chunk 问题）
     * 支持多种常见变体：<think>、<thinking>、<thought>
     */
    private String removeThinkTags(String text) {
        if (text == null || text.isEmpty()) return text;
        text = text.replaceAll("(?s)<think>.*?</think>", "");
        text = text.replaceAll("(?s)<thinking>.*?</thinking>", "");
        text = text.replaceAll("(?s)<thought>.*?</thought>", "");
        return text.trim();
    }

    /**
     * 【修复A-1】从完整文本提取 think 标签内的推理内容
     * （替代原来从未被调用、且实现损坏的 extractThinkContent）
     */
    private String extractReasoningFromThinkTags(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder reasoning = new StringBuilder();
        Pattern pattern = Pattern.compile("<think>(.*?)</think>", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            reasoning.append(matcher.group(1));
        }
        return reasoning.toString();
    }

    /**
     * 统一后置处理（保存 AI 消息 + 更新会话时间 + 新会话生成标题）
     */
    public void finalize(ChatContext ctx, String content, Map<String, Object> metadata) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            doFinalize(ctx, content, metadata);
        });
    }

    private void doFinalize(ChatContext ctx, String content, Map<String, Object> metadata) {
        if (content != null && !content.isEmpty()) {
            chatMessageService.saveAiMessage(ctx.getFinalSessionId(), content, metadata);
        }
        chatSessionService.updateLastActiveTime(ctx.getFinalSessionId());

        if (ctx.isNewSession()) {
            titleGenerationService.generateAndUpdateTitle(ctx.getFinalSessionId(), ctx.getPrompt(), content);
        }
    }
}