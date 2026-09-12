package top.lingxi.campus.itAgent.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import top.lingxi.campus.itAgent.context.ChatContext;
import top.lingxi.campus.itAgent.knowledge.service.IKnowledgeQAService;


import java.util.List;
//todo 这里 domain 直接传 "IT" / "HR" / "ADMIN"，
// 对应知识库名需要是 "IT知识库" / "HR知识库" / "ADMIN知识库"。如果你之前用的是 "行政知识库"
// 而不是 "ADMIN知识库"，初始化知识库时请保持一致，或者在这里加一层 domain → 知识库名 的映射。
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeQAHandler implements IntentHandler {

    private final IKnowledgeQAService knowledgeQAService;

    @Override
    public List<String> getDomains() {
        return List.of("IT", "HR", "ADMIN");
    }

    @Override
    public int getOrder() {
        return 4;
    }

    @Override
    public boolean supports(ChatContext ctx) {
        String intent = ctx.getIntent() != null ? ctx.getIntent().getIntent() : null;
        return "knowledge_qa".equals(intent);
    }

    @Override
    public Flux<String> handle(ChatContext ctx) {
        Long userId = ctx.getUserId();
        String domain = ctx.getDomain(); // IT / HR / ADMIN
        String prompt = ctx.getPrompt();

        return knowledgeQAService.answer(userId, domain, prompt)
                .map(this::extractAnswer)
                .filter(s -> !s.isEmpty());
    }

    /**
     * 从 SSE 格式响应中提取纯文本答案。
     * knowledgeQAService.answer() 返回的是为 /astra/chat 端点设计的 SSE 格式：
     * "event: answer\ndata: <content>\n\n"
     * 但本 Handler 走的是 /ai/text-chat 端点，外层 ChatController 会统一做 SSE 包裹，
     * 所以这里剥离内层 SSE 格式，只返回纯文本，避免前端收到双重包裹的 "event: answer data: ..." 乱码。
     */
    private String extractAnswer(String chunk) {
        String[] events = chunk.split("\n\n");
        StringBuilder result = new StringBuilder();
        for (String event : events) {
            if (event.isEmpty()) continue;
            String eventName = null;
            String data = null;
            for (String line : event.split("\n")) {
                if (line.startsWith("event: ")) eventName = line.substring(7);
                else if (line.startsWith("data: ")) data = line.substring(6);
            }
            // 只提取 answer 事件（或无事件名的裸 data，如文件上传分支）
            if (data != null && (eventName == null || "answer".equals(eventName))) {
                result.append(data);
            }
        }
        return result.toString();
    }
}