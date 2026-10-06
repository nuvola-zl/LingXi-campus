package top.lingxi.campus.chat.chat.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import reactor.core.publisher.Flux;
import top.lingxi.campus.common.constant.PromptConstant;
import top.lingxi.campus.itAgent.pipeline.ChatPipeline;
import top.lingxi.campus.itAgent.agent.service.AgentDialogService;
import top.lingxi.campus.chat.session.service.IChatSessionService;
import top.lingxi.campus.common.context.BaseContext;
import top.lingxi.campus.config.JdbcChatMemory;
import top.lingxi.campus.itAgent.context.ChatContext;

import top.lingxi.campus.chat.chat.service.IChatService;



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
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements IChatService {

    private final ChatPipeline chatPipeline;
    private final JdbcChatMemory jdbcChatMemory;
    private final AgentDialogService agentDialogService;
    private final RedissonClient redissonClient;

    // ========== 关键：注入 HR/行政的 ChatClient ==========
    private final ChatClient hrChatClient;
    private final ChatClient adminChatClient;
    private final IChatSessionService chatSessionService;

    @Override
    public Flux<String> textChat(String domain, Long groupId, Long sessionId, String prompt,
                                 Boolean enableThinking, Integer thinkingBudget, String model) {

        //大小写兼容，统一转换为大写，再次兜底为 IT 分支"
        String upperDomain = domain != null ? domain.toUpperCase() : "IT";

        //  HR 直接走 AI Tool 调用
        if ("HR".equals(upperDomain)) {
            return handleDomainChat("HR", groupId, sessionId, prompt, hrChatClient,
                    enableThinking, thinkingBudget, model);
        }

        //  行政直接走 AI Tool 调用
        if ("ADMIN".equals(upperDomain)) {
            return handleDomainChat("ADMIN", groupId, sessionId, prompt, adminChatClient,
                    enableThinking, thinkingBudget, model);
        }

        //  IT 分支（v3：统一进入 Agent 状态机）

        Long userId = BaseContext.getCurrentId();

        // [v3-fix] 每用户处理锁：上一条消息还没处理完时，新消息快速拒绝。
        // 防止"基于旧快照并发处理"导致的答非所问（CAS 只保护写，保护不了过期读）
        RLock processingLock = redissonClient.getLock("chat:processing:" + userId);
        boolean acquired;
        try {
            // waitTime=0：不等待，拿不到锁就直接拒绝；leaseTime=60：崩溃时自动过期兜底
            acquired = processingLock.tryLock(0, 60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Flux.just("系统繁忙，请稍后再试。");
        }
        if (!acquired) {
            // 被拒的消息直接返回提示：不经过 prepare → 不落库、不进状态机、不产生会话，无痕
            return Flux.just("上一条消息我还在处理哦，请稍等片刻再发～🔧");
        }

        Flux<String> result;
        try {
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

            // 2. 新会话首条事件
            Flux<String> initialFlux = ctx.isNewSession()
                    //新会话的首条响应会先推一个 SESSION_CREATED 标记，
                    // 把后端生成的 sessionId 同步给前端，前端后续请求都带上它来维持对话连续性
                    ? chatPipeline.emitSessionCreated(finalSessionId)
                    : Flux.empty();

            // 如果用户已经在 Agent 状态机中（DIAGNOSING/COLLECTING/CONFIRMING），
            // 直接继续对话，跳过意图识别，不受新消息意图干扰
            //if条件里面先获取一个当前用户的会话状态，判断是否有正在进行的对话流程，这个状态只有true和false
            if (agentDialogService.hasOngoingDialog(userId)) {

                // 从 Agent 状态机继续对话，返回一个 Flux<String>（还没执行）
                //相当于返回一个说明书和一个标签，说明了当前状态可以做什么，以及做什么后会进入哪个状态，让其他方法执行对应的操作
                Flux<String> agentFlux = agentDialogService.continueDialog(userId, prompt, finalSessionId);

                // 统一走 Pipeline 后置（保存 AI 消息、更新会话时间、生成标题）
                //再贴三层标签，根据不同的信号，执行不同的操作
                // 1. 系统标记过滤
                // 2. 全部吐完，把收集的内容存数据库
                // 3. 出错了，推错误文案兜底
                Flux<String> wrappedFlux = chatPipeline.wrap(ctx, agentFlux);

                // 合并首条事件和 Agent 流
                result = initialFlux.concatWith(wrappedFlux);
            } else {
                // 3. v3：IT 域所有请求统一进入 Agent 状态机
                //    诊断/查单/催单/关单/建单/闲聊 全部由 Agent 的 LLM 决策 + 工具调用处理
                log.info("========== 启动Agent对话: userId={}, sessionId={} ==========", userId, finalSessionId);
                result = initialFlux.concatWith(
                        chatPipeline.wrap(ctx, agentDialogService.startDialog(userId, prompt, finalSessionId)));
            }
        } catch (RuntimeException e) {
            // prepare 等同步环节抛异常时手动释放，别让锁空等到 60 秒租约到期
            processingLock.forceUnlock();
            throw e;
        }

        // 4. 流结束（完成/出错/取消）时释放锁，之后用户才能发下一条
        // 用 forceUnlock：加锁在 Tomcat 线程、释放可能在响应式线程，普通 unlock 会报线程不匹配
        RLock lockToRelease = processingLock;
        return result.doFinally(signal -> {
            try {
                if (lockToRelease.isLocked()) {
                    lockToRelease.forceUnlock();
                }
            } catch (Exception ignored) {
            }
        });


/*
        // 3. 意图识别（只有不在 Agent 流程中的请求才走）
        return intentDetectionService.analyzeIntentReactive(prompt, ctx.getDomain())
                .flatMapMany(intent -> {
                    ctx.setIntent(intent);
//                    log.info("意图识别完成: intent={}, domain={}, categoryId={}",
//                            intent.getIntent(), intent.getDomain(), intent.getCategoryId());

//                    // ===== 跨域转接：IT 入口识别到 HR/Admin 需求时，切换通道 =====
//                    if (intent.getCategoryId() != null
//                            && (intent.getCategoryId() == 2 || intent.getCategoryId() == 3)) {
//
//                        return handleCrossDomainTransfer(
//                                initialFlux, ctx, intent.getCategoryId(),
//                                prompt, userId);
//                    }

                    if ("ticket_create".equals(intent.getIntent())) {

                        Flux<String> agentFlux = agentDialogService.startDialog(userId, prompt, finalSessionId);

                        Flux<String> wrappedFlux = chatPipeline.wrap(ctx, agentFlux);

                        return initialFlux.concatWith(wrappedFlux);
                    }


                    // 4. 其他意图（ticket_query / knowledge_qa / image_generation / text 等）
                    // 继续走旧的 Handler 链
                    IntentHandler handler = handlerRegistry.findFirstMatch(ctx)
                            .orElseThrow(() -> new IllegalStateException("兜底 Handler 未注册"));


                    // 5. 执行业务逻辑
                    Flux<String> responseFlux = handler.handle(ctx);

                    Flux<String> wrappedFlux = chatPipeline.wrap(ctx, responseFlux);

                    return initialFlux.concatWith(wrappedFlux);
                });
*/
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

        if (!history.isEmpty()) {
            Message last = history.get(history.size() - 1);
            if (last instanceof UserMessage && prompt.equals(last.getText())) {
                history.remove(history.size() - 1);
            }
        }

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

        // 选域 prompt 并填充占位符：7 个 %s + 1 个 %d，顺序必须和 TIME_BLOCK 对应
        String systemText = String.format(
                "HR".equals(domain) ? PromptConstant.HR_SYSTEM_PROMPT : PromptConstant.ADMIN_SYSTEM_PROMPT,
                todayStr, weekDay, timeStr,
                todayStr, today.plusDays(1), today.plusDays(2), nextMonday,
                userId);


        var promptBuilder = client.prompt()
                .options(runtimeOptions)
                .toolContext(Map.of("userId", userId))
                .messages(new SystemMessage(systemText));

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

                        // 6. 提取思考内容（如果有）
                        String reasoningContent = extractReasoningFromThinkTags(rawContent);
                        // 7. 移除思考标签，保留回答内容
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
            ChatContext ctx,          // 【修复A-2】新增：IT 分支上下文，default 降级分支的 wrap 要用
            Long categoryId, String prompt, Long userId) {

        // 从 ctx 取原参数（保持原方法体逻辑不变）
        Long groupId = ctx.getGroupId();
        Long sessionId = ctx.getFinalSessionId();
        Boolean enableThinking = ctx.getEnableThinking();
        Integer thinkingBudget = ctx.getThinkingBudget();
        String model = ctx.getModel();

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
                // 【修复A-2】降级分支补上 chatPipeline.wrap：
                // 原来直接返回裸 agentFlux，AI 消息不落库、会话时间不更新，
                // 与 ticket_create 分支行为不一致。
                Flux<String> agentFlux = agentDialogService.startDialog(userId, prompt, sessionId);
                Flux<String> wrappedFlux = chatPipeline.wrap(ctx, agentFlux);
                return initialFlux.concatWith(wrappedFlux);
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