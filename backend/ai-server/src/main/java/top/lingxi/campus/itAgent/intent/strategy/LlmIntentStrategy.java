package top.lingxi.campus.itAgent.intent.strategy;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import top.lingxi.campus.common.json.JacksonObjectMapper;
import top.lingxi.campus.result.IntentDetectionResult;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmIntentStrategy implements IntentStrategy {

    private final DashScopeChatModel chatModel;
    private final JacksonObjectMapper objectMapper;

    @Value("${ai.intent-detection.model:qwen-turbo}")
    private String intentModel;

    @Override
    public Mono<IntentDetectionResult> detect(String userMessage, String domain) {
        return Mono.fromCallable(() -> doLlmAnalyze(userMessage, domain))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(5))   // 3 改成 5
                .retryWhen(reactor.util.retry.Retry
                        .fixedDelay(1, java.time.Duration.ofMillis(500)) // 超时/网络错误时快速重试1次
                        .filter(throwable -> throwable instanceof java.util.concurrent.TimeoutException
                                || throwable instanceof java.io.IOException))
                .doOnNext(result -> log.info("[LlmStrategy] 识别成功: intent={}", result.getIntent()))
                .doOnError(e -> log.warn("[LlmStrategy] LLM识别失败或超时: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    private IntentDetectionResult doLlmAnalyze(String userMessage, String domain) throws JsonProcessingException {
        String promptText = buildIntentPrompt(userMessage, domain);

        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .model(intentModel)
                .build();

        ChatResponse response = chatModel.call(new Prompt(promptText, options));
        String json = response.getResult().getOutput().getText().trim();
        json = cleanJsonMarkdown(json);

        JsonNode node = objectMapper.readTree(json);

        IntentDetectionResult result = new IntentDetectionResult();
        result.setIntent(node.path("intent").asText("text"));
        result.setDomain(node.path("domain").asText(domain));
        if (node.path("category_id").asLong(0) > 0) {
            result.setCategoryId(node.path("category_id").asLong());
        }
        result.setDefaultPriority(node.path("priority").asInt(2));

        if (!node.path("extracted_ticket_no").isNull()) {
            result.setExtractedTicketNo(node.path("extracted_ticket_no").asText());
        }
        if (!node.path("image_prompt").isNull()) {
            result.setImagePrompt(node.path("image_prompt").asText());
        }

        // confidence 只用于本地阈值判断，不存入 result（因为 result 没有该字段）
        double confidence = node.path("confidence").asDouble(1.0);
        if (confidence < 0.6 && !"image_generation".equals(result.getIntent())) {
            log.debug("LLM 置信度 {} 过低，降级为 text", confidence);
            result.setIntent("text");
        }

        if (result.getExtractedTicketNo() == null && result.getIntent().startsWith("ticket_")) {
            result.setExtractedTicketNo(extractTicketNo(userMessage));
        }

        return result;
    }

    private String buildIntentPrompt(String userMessage, String domain) {
        return String.format("""
        你是一个智能客服意图识别助手。
        
        【示例】
        用户输入: 电脑蓝屏了怎么办
        当前领域: IT
        {"intent":"ticket_create","domain":"IT","category_id":1,"priority":2,"confidence":0.95,"extracted_ticket_no":null,"image_prompt":null}
        
        用户输入: 查一下我的工单 IT-20260718-001
        当前领域: IT
        {"intent":"ticket_query","domain":"IT","category_id":0,"priority":2,"confidence":0.98,"extracted_ticket_no":"IT-20260718-001","image_prompt":null}
        
        用户输入: 帮我画一张猫咪的图片
        当前领域: IT
        {"intent":"image_generation","domain":"IT","category_id":0,"priority":2,"confidence":0.99,"extracted_ticket_no":null,"image_prompt":"一只可爱的猫咪"}
        
        用户输入: 怎么请假啊
        当前领域: HR
        {"intent":"knowledge_qa","domain":"HR","category_id":2,"priority":2,"confidence":0.92,"extracted_ticket_no":null,"image_prompt":null}
        
        用户输入: 你好
        当前领域: IT
        {"intent":"text","domain":"IT","category_id":0,"priority":2,"confidence":0.3,"extracted_ticket_no":null,"image_prompt":null}
        
        用户输入: 算了不用了
        当前领域: IT
        {"intent":"ticket_abandon","domain":"IT","category_id":0,"priority":2,"confidence":0.95,"extracted_ticket_no":null,"image_prompt":null}
        
        用户输入: 我的工单很急，什么时候能好
        当前领域: IT
        {"intent":"ticket_urgent","domain":"IT","category_id":0,"priority":2,"confidence":0.9,"extracted_ticket_no":null,"image_prompt":null}
        
        【可选意图】
        - ticket_create: 用户遇到具体问题，需要创建工单
        - ticket_query: 用户查询工单状态
        - ticket_urgent: 用户催单或要求加急
        - ticket_close: 用户想关闭工单
        - ticket_abandon: 用户想放弃当前操作
        - knowledge_qa: 用户咨询流程、政策
        - image_generation: 用户要求生成图片
        - text: 普通闲聊、问候、意图不明确
        
        【规则】
        1. 工单号格式：XX-YYYYMMDD-NNN，如 IT-20260718-001
        2. confidence<0.6 或意图不明 → text
        3. 严格返回 JSON，不要 markdown，不要额外文字
        
        【当前任务】
        用户输入：%s
        当前领域：%s
        """, userMessage, domain);
    }

    private String cleanJsonMarkdown(String json) {
        json = json.trim();
        if (json.startsWith("```json")) json = json.substring(7);
        else if (json.startsWith("```")) json = json.substring(3);
        if (json.endsWith("```")) json = json.substring(0, json.length() - 3);
        return json.trim();
    }

    private String extractTicketNo(String message) {
        Pattern pattern = Pattern.compile("[A-Z]{2}-\\d{8}-\\d{3}");
        Matcher matcher = pattern.matcher(message);
        return matcher.find() ? matcher.group() : null;
    }
}