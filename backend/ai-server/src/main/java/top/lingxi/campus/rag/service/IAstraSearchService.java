package top.lingxi.campus.rag.service;

import reactor.core.publisher.Flux;
import top.lingxi.campus.domain.ai.dto.AstraChatEvent;
import top.lingxi.campus.domain.ai.dto.AstraChatRequest;
import top.lingxi.campus.domain.ai.entity.ChatSession;
import top.lingxi.campus.domain.ai.dto.ChunkResponse;

import java.util.List;

/**
 * Astra RAG 检索服务接口
 *
 * 重构说明：chat 返回值由 Flux<String>（预格式化 SSE 字符串）改为
 * Flux<AstraChatEvent>（结构化事件对象），SSE 序列化收敛到 Controller 单一出口。
 */
public interface IAstraSearchService {

    /**
     * 混合检索(BM25 + 向量)
     * @param libraryId 知识库ID
     * @param query 查询文本
     * @param topK 召回数量
     * @return 检索结果列表
     */
    List<ChunkResponse> hybridSearch(Long libraryId, String query, int topK);

    /**
     * ReRank 重排序
     * @param libraryId 知识库ID
     * @param query 查询文本
     * @param chunks 待重排序的分片
     * @param topK 返回数量
     * @return 重排序后的分片列表
     */
    List<ChunkResponse> rerank(Long libraryId, String query, List<ChunkResponse> chunks, int topK);

    /**
     * 构建 RAG 问答 Prompt
     * @param query 用户问题
     * @param chunks 检索到的分片
     * @return 构建好的 Prompt
     */
    String buildPrompt(String query, List<ChunkResponse> chunks);

    /**
     * 构建指定回答风格的 RAG 问答 Prompt
     * @param style "detail"=详细文档风格, "concise"=精简客服风格
     */
    String buildPrompt(String query, List<ChunkResponse> chunks, String style);

    /**
     * RAG 知识问答（流式）
     * @param userId 用户ID
     * @param request 问答请求
     * @return SSE 事件流（结构化事件，由 Controller 负责序列化发送）
     */
    Flux<AstraChatEvent> chat(Long userId, AstraChatRequest request);

    /**
     * 文件直传对话（流式）：不走检索，直接调用 LLM。
     * 由原 chat() 内的 if(isFileUpload) 分支拆出。
     * @param userId 用户ID
     * @param request 问答请求
     * @return SSE 事件流
     */
    Flux<AstraChatEvent> chatWithFile(Long userId, AstraChatRequest request);

    /**
     * 创建或获取 Astra 会话
     * @param userId 用户ID
     * @param sessionId 会话ID(null表示新建)
     * @return ChatSession
     */
    ChatSession getOrCreateSession(Long userId, Long libraryId, Long sessionId);
}