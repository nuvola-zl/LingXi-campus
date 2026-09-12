package top.lingxi.campus.chat.chat.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.stereotype.Service;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import reactor.core.publisher.Flux;
import top.lingxi.campus.itAgent.pipeline.ChatPipeline;
import top.lingxi.campus.itAgent.agent.service.AgentDialogService;
import top.lingxi.campus.chat.session.service.IChatSessionService;
import top.lingxi.campus.common.context.BaseContext;
import top.lingxi.campus.config.JdbcChatMemory;
import top.lingxi.campus.itAgent.context.ChatContext;
import top.lingxi.campus.itAgent.handler.HandlerRegistry;
import top.lingxi.campus.itAgent.handler.IntentHandler;
import top.lingxi.campus.chat.chat.service.IChatService;
import top.lingxi.campus.ai.service.IIntentDetectionService;


import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements IChatService {

    private final ChatPipeline chatPipeline;
    private final HandlerRegistry handlerRegistry;
    private final IIntentDetectionService intentDetectionService;
    private final JdbcChatMemory jdbcChatMemory;
    private final AgentDialogService agentDialogService;

    // ========== 关键：注入 HR/行政的 ChatClient ==========
    private final ChatClient hrChatClient;
    private final ChatClient adminChatClient;
    private final IChatSessionService chatSessionService;

    @Override
    //@Transactional
    public Flux<String> textChat(String domain, Long groupId, Long sessionId, String prompt,
                                 Boolean enableThinking, Integer thinkingBudget, String model) {

        String upperDomain = domain != null ? domain.toUpperCase() : "IT";
        log.info("========== domain分流: rawDomain={}, upperDomain={} ==========", domain, upperDomain);

        // ========== HR 直接走 AI Tool 调用 ==========
        if ("HR".equals(upperDomain)) {
            log.info("========== 进入HR分支 ==========");
            return handleDomainChat("HR", groupId, sessionId, prompt, hrChatClient,
                    enableThinking, thinkingBudget, model);
        }

        // ========== 行政直接走 AI Tool 调用 ==========
        if ("ADMIN".equals(upperDomain)) {
            log.info("========== 进入ADMIN分支 ==========");
            return handleDomainChat("ADMIN", groupId, sessionId, prompt, adminChatClient,
                    enableThinking, thinkingBudget, model);
        }

        // ========== IT 分支（改造后）==========
        log.info("========== 进入IT分支 ==========");
        ChatContext ctx = ChatContext.builder()
                .domain(upperDomain)
                .groupId(groupId)
                .sessionId(sessionId)
                .prompt(prompt)
                .enableThinking(enableThinking)
                .thinkingBudget(thinkingBudget)
                .model(model)
                .build();

        // 1. 前置处理：创建/复用会话 + 保存用户消息（所有分支共用）
        chatPipeline.prepare(ctx);
        Long finalSessionId = ctx.getFinalSessionId();
        Long userId = BaseContext.getCurrentId();

        // 2. 新会话首条事件
        Flux<String> initialFlux = ctx.isNewSession()
                ? chatPipeline.emitSessionCreated(finalSessionId)
                : Flux.empty();

        // ===== 关键新增：Agent 多轮对话优先拦截 =====
        // 如果用户已经在 Agent 状态机中（DIAGNOSING/COLLECTING/CONFIRMING），
        // 直接继续对话，跳过意图识别，不受新消息意图干扰
        if (agentDialogService.hasOngoingDialog(userId)) {
            log.info("========== Agent对话继续: userId={}, sessionId={} ==========", userId, finalSessionId);
            Flux<String> agentFlux = agentDialogService.continueDialog(userId, prompt, finalSessionId);
            // 统一走 Pipeline 后置（保存 AI 消息、更新会话时间、生成标题）
            Flux<String> wrappedFlux = chatPipeline.wrap(ctx, agentFlux);
            return initialFlux.concatWith(wrappedFlux);
        }
        // =============================================

        // 3. 意图识别（只有不在 Agent 流程中的请求才走）
        return intentDetectionService.analyzeIntentReactive(prompt, ctx.getDomain())
                .flatMapMany(intent -> {
                    ctx.setIntent(intent);
                    log.info("意图识别完成: intent={}, domain={}, categoryId={}",
                            intent.getIntent(), intent.getDomain(), intent.getCategoryId());

                    // ===== 跨域转接：IT 入口识别到 HR/Admin 需求时，切换通道 =====
                    if (intent.getCategoryId() != null
                            && (intent.getCategoryId() == 2 || intent.getCategoryId() == 3)) {
                        log.info("========== 跨域转接: categoryId={} ==========", intent.getCategoryId());
                        return handleCrossDomainTransfer(
                                initialFlux, intent.getCategoryId(),
                                ctx.getGroupId(), ctx.getFinalSessionId(), prompt, userId,
                                ctx.getEnableThinking(), ctx.getThinkingBudget(), ctx.getModel());
                    }
                    // ========================================================

                    // ===== 关键新增：ticket_create 走 Agent 流程 =====
                    if ("ticket_create".equals(intent.getIntent())) {
                        log.info("========== 启动Agent对话: userId={}, sessionId={} ==========", userId, finalSessionId);
                        Flux<String> agentFlux = agentDialogService.startDialog(userId, prompt, finalSessionId);
                        Flux<String> wrappedFlux = chatPipeline.wrap(ctx, agentFlux);
                        return initialFlux.concatWith(wrappedFlux);
                    }
                    // =================================================

                    // 4. 其他意图（ticket_query / knowledge_qa / image_generation / text 等）
                    // 继续走旧的 Handler 链
                    IntentHandler handler = handlerRegistry.findFirstMatch(ctx)
                            .orElseThrow(() -> new IllegalStateException("兜底 Handler 未注册"));

                    Flux<String> responseFlux = handler.handle(ctx);
                    Flux<String> wrappedFlux = chatPipeline.wrap(ctx, responseFlux);

                    return initialFlux.concatWith(wrappedFlux);
                });
    }

    /**
     * 统一处理 HR/ADMIN
     */
    private Flux<String> handleDomainChat(String domain, Long groupId, Long sessionId,
                                          String prompt, ChatClient client,
                                          Boolean enableThinking, Integer thinkingBudget, String model) {

        log.info("========== handleDomainChat开始: domain={}, model={}, enableThinking={} ==========",
                domain, model, enableThinking);

        // 1. 创建/复用会话 + 保存用户消息
        ChatContext ctx = ChatContext.builder()
                .domain(domain)
                .groupId(groupId)
                .sessionId(sessionId)
                .prompt(prompt)
                .build();

        chatPipeline.prepare(ctx);
        Long finalSessionId = ctx.getFinalSessionId();
        log.info("========== 会话准备完成: finalSessionId={} ==========", finalSessionId);

        // 2. 新会话标记
        Flux<String> initialFlux = ctx.isNewSession()
                ? chatPipeline.emitSessionCreated(finalSessionId)
                : Flux.empty();

        // 3. 查历史消息
        Long userId = BaseContext.getCurrentId();
        List<Message> history = jdbcChatMemory.getHistory(finalSessionId, 10);
        log.info("========== 历史消息: count={} ==========", history.size());

        // 4. 构建运行时选项（默认 qwen-flash，不上思考）
        String actualModel = model != null ? model : "qwen-flash";
        boolean actualThinking = enableThinking != null && enableThinking;
        DashScopeChatOptions runtimeOptions = DashScopeChatOptions.builder()
                .model(actualModel)
                .enableThinking(actualThinking)
                .build();
        if (actualThinking && thinkingBudget != null) {
            runtimeOptions.setMaxTokens(thinkingBudget);
        }

        // 5. 构建 ChatClient 请求（带历史 + 时间上下文）
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();

        String todayStr = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String weekDay = now.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINA);
        String timeStr = now.format(DateTimeFormatter.ofPattern("HH:mm"));

        LocalDate nextMonday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        String timeContext = String.format(
                "【当前时间上下文】现在时间是 %s（%s）%s。" +
                "如果用户说'今天'，指 %s；'明天'指 %s；'后天'指 %s；'下周'指从 %s 开始的那一周。" +
                "所有日期必须使用 yyyy-MM-dd 格式输出，禁止输出过去年份。当前用户ID：%d。",
                todayStr, weekDay, timeStr,
                todayStr, today.plusDays(1), today.plusDays(2), nextMonday,
                userId
        );

        var promptBuilder = client.prompt()
                .options(runtimeOptions)
                .toolContext(Map.of("userId", userId))   // ← 关键：随请求传递，线程无关
                .messages(new SystemMessage(timeContext));

        for (Message msg : history) {
            promptBuilder.messages(msg);
        }

        Flux<String> aiFlux = promptBuilder
                .user(prompt)
                .stream()
                .content()
                .filter(chunk -> chunk != null && !chunk.isEmpty());  // 过滤空 chunk，保留换行符

        // 5. 收集回复 + 保存到数据库
        StringBuilder contentBuilder = new StringBuilder();

        Flux<String> wrappedFlux = aiFlux
                .doOnNext(contentBuilder::append)
                .doOnComplete(() -> {
                    String rawContent = contentBuilder.toString().trim();
                    if (!rawContent.isEmpty()) {
                        String reasoningContent = extractReasoningFromThinkTags(rawContent);
                        String cleanContent = removeThinkTags(rawContent);

                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("model", actualModel);
                        metadata.put("enable_thinking", actualThinking);
                        if (!reasoningContent.isEmpty()) {
                            metadata.put("reasoning_content", reasoningContent);
                        }

                        if (!cleanContent.isEmpty()) {
                            chatPipeline.finalize(ctx, cleanContent, metadata);
                        }
                    }
                })
                .onErrorResume(error -> {
                    log.error("AI 调用失败", error);
                    return Flux.just("ERROR:服务暂时不可用");
                });

        return initialFlux.concatWith(wrappedFlux);
    }

    /**
     * 跨域智能转接：IT 入口识别到 HR/行政 等问题时，提示用户并切换通道。
     * 不调用 handleDomainChat（避免重复 prepare 导致用户消息存两份），
     * 直接构建 ChatClient 流，利用 bean 已配置的 system prompt + tools。
     */
    private Flux<String> handleCrossDomainTransfer(
            Flux<String> initialFlux,
            Long categoryId, Long groupId, Long sessionId, String prompt, Long userId,
            Boolean enableThinking, Integer thinkingBudget, String model) {

        String categoryName;
        String targetDomain;
        ChatClient targetClient;

        switch (categoryId.intValue()) {
            case 2 -> {
                categoryName = "人力资源";
                targetDomain = "HR";
                targetClient = hrChatClient;
            }
            case 3 -> {
                categoryName = "行政服务";
                targetDomain = "ADMIN";
                targetClient = adminChatClient;
            }
            default -> {
                log.warn("跨域转接遇到未知分类: categoryId={}, 降级为IT处理", categoryId);
                Flux<String> agentFlux = agentDialogService.startDialog(userId, prompt, sessionId);
                return initialFlux.concatWith(agentFlux);
            }
        }

        log.info("跨域转接: IT入口 → {}(categoryId={})", targetDomain, categoryId);

        // 更新数据库会话类型为实际处理域
        chatSessionService.updateSessionType(sessionId, targetDomain);

        String transferMsg = String.format(
                "检测到您的需求属于【%s】，已为您切换至对应服务通道处理。\n",
                categoryName
        );

        // 构建运行时选项
        String actualModel = model != null ? model : "qwen-flash";
        boolean actualThinking = enableThinking != null && enableThinking;
        DashScopeChatOptions runtimeOptions = DashScopeChatOptions.builder()
                .model(actualModel)
                .enableThinking(actualThinking)
                .build();
        if (actualThinking && thinkingBudget != null) {
            runtimeOptions.setMaxTokens(thinkingBudget);
        }

        // 加载历史消息
        List<Message> history = jdbcChatMemory.getHistory(sessionId, 10);

        // 注入时间上下文（不覆盖 bean 的默认 system prompt + tools）
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        String todayStr = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String weekDay = now.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINA);
        String timeStr = now.format(DateTimeFormatter.ofPattern("HH:mm"));
        LocalDate nextMonday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        String timeContext = String.format(
                "【当前时间上下文】现在时间是 %s（%s）%s。" +
                "如果用户说'今天'，指 %s；'明天'指 %s；'后天'指 %s；'下周'指从 %s 开始的那一周。" +
                "所有日期必须使用 yyyy-MM-dd 格式输出，禁止输出过去年份。当前用户ID：%d。",
                todayStr, weekDay, timeStr,
                todayStr, today.plusDays(1), today.plusDays(2), nextMonday,
                userId
        );

        var promptBuilder = targetClient.prompt()
                .options(runtimeOptions)
                .toolContext(Map.of("userId", userId))
                .messages(new SystemMessage(timeContext));

        for (Message msg : history) {
            promptBuilder.messages(msg);
        }

        // 收集响应并保存（session 已存在，不重复生成标题）
        StringBuilder contentBuilder = new StringBuilder();
        ChatContext finalizeCtx = ChatContext.builder()
                .domain(targetDomain)
                .sessionId(sessionId)
                .build();
        finalizeCtx.setFinalSessionId(sessionId);
        finalizeCtx.setNewSession(false);

        Flux<String> aiFlux = promptBuilder
                .user(prompt)
                .stream()
                .content()
                .filter(chunk -> chunk != null && !chunk.isEmpty())
                .doOnNext(contentBuilder::append)
                .doOnComplete(() -> {
                    String rawContent = contentBuilder.toString().trim();
                    if (!rawContent.isEmpty()) {
                        String reasoningContent = extractReasoningFromThinkTags(rawContent);
                        String cleanContent = removeThinkTags(rawContent);

                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("model", actualModel);
                        metadata.put("enable_thinking", actualThinking);
                        if (!reasoningContent.isEmpty()) {
                            metadata.put("reasoning_content", reasoningContent);
                        }

                        if (!cleanContent.isEmpty()) {
                            chatPipeline.finalize(finalizeCtx, cleanContent, metadata);
                        }
                    }
                })
                .onErrorResume(error -> {
                    log.error("跨域 AI 调用失败", error);
                    return Flux.just("ERROR:服务暂时不可用");
                });

        return Flux.concat(initialFlux, Flux.just(transferMsg), aiFlux);
    }

    /**
     * 从文本中提取 &lt;think&gt; 标签内的推理内容
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
     * 移除文本中的 &lt;think&gt; / &lt;thinking&gt; / &lt;thought&gt; 标签
     */
    private String removeThinkTags(String text) {
        if (text == null || text.isEmpty()) return text;
        text = text.replaceAll("(?s)<think>.*?</think>", "");
        text = text.replaceAll("(?s)<thinking>.*?</thinking>", "");
        text = text.replaceAll("(?s)<thought>.*?</thought>", "");
        return text.trim();
    }
}