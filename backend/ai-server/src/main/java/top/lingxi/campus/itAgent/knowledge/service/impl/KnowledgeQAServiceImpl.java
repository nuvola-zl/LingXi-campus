package top.lingxi.campus.itAgent.knowledge.service.impl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import top.lingxi.campus.rag.service.IAstraSearchService;
import top.lingxi.campus.domain.ai.dto.AstraChatEvent;
import top.lingxi.campus.domain.ai.dto.AstraChatRequest;
import top.lingxi.campus.domain.ai.entity.KbLibrary;
import top.lingxi.campus.domain.ai.mapper.KbLibraryMapper;
import top.lingxi.campus.itAgent.knowledge.service.IKnowledgeQAService;

@Service
@Slf4j
@RequiredArgsConstructor
public class KnowledgeQAServiceImpl implements IKnowledgeQAService {

    private final IAstraSearchService astraSearchService;
    private final KbLibraryMapper libraryMapper;
    private final ChatClient astraClient;  // ← 加这个，直接调 AI

    @Override
    public Flux<String> answer(Long userId, String domain, String query) {

        if (query.contains("【用户上传文件") || query.contains("【用户尝试上传文件")) {
            return astraClient.prompt()
                    .user(query)
                    .stream()
                    .content()
                    .map(content -> "data:" + content + "\n\n");
        }


        // 知识库逻辑（IT 域固定指向校园办事指南）
        KbLibrary library = "IT".equalsIgnoreCase(domain)
                ? libraryMapper.selectByName("校园办事指南")
                : libraryMapper.selectByName(domain + "知识库");
        if (library == null) {
            return Flux.just("ERROR:该领域知识库不存在");
        }

        AstraChatRequest request = new AstraChatRequest();
        request.setLibraryId(library.getId());
        request.setPrompt(query);
        // 事件流还原为原有 SSE 字符串格式，保持本模块前端协议不变
        return astraSearchService.chat(userId, request)
                .map(this::toSseString);
    }

    /**
     * AstraChatEvent → "event: xxx\ndata: yyy\n\n"（与原 Flux<String> 行为一致）
     */
    private String toSseString(AstraChatEvent event) {
        return "event: " + event.getEvent() + "\ndata: " + event.getData() + "\n\n";
    }
}