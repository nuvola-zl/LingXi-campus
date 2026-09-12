package top.lingxi.campus.rag.service.impl;

import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import top.lingxi.campus.domain.ai.dto.AstraChatEvent;
import top.lingxi.campus.domain.ai.dto.AstraChatRequest;
import top.lingxi.campus.domain.ai.entity.ChatSession;
import top.lingxi.campus.domain.ai.entity.KbChunk;
import top.lingxi.campus.domain.ai.entity.KbLibrary;
import top.lingxi.campus.common.exception.ErrorCode;
import top.lingxi.campus.common.exception.BusinessException;
import top.lingxi.campus.domain.ai.mapper.KbChunkMapper;
import top.lingxi.campus.domain.ai.mapper.KbLibraryMapper;
import top.lingxi.campus.infra.config.AstraProperties;
import top.lingxi.campus.infra.config.FeedbackProperties;
import top.lingxi.campus.rag.service.IAstraSearchService;
import top.lingxi.campus.chat.session.service.IChatSessionService;
import top.lingxi.campus.rag.service.IQueryRewriteService;
import top.lingxi.campus.domain.ai.dto.ChunkResponse;
import top.lingxi.campus.result.KbChunkSearchResult;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Astra RAG 检索服务实现
 *
 * Phase 1：SSE 事件结构化、chat/chatWithFile 拆分、权限校验、反馈库 IN 查询融合、
 *          expandWithContext 去 O(n²)、内容去重、citations 溯源
 * Phase 3：rerank 实现抽取至 RerankClient；RERANK_TOP_K 常量与 feedback 开关
 *          收敛进 AstraProperties / FeedbackProperties；移除 HttpClient/ObjectMapper 相关代码
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AstraSearchServiceImpl implements IAstraSearchService {

    private final KbLibraryMapper libraryMapper;
    private final KbChunkMapper chunkMapper;
    private final IChatSessionService chatSessionService;
    private final DashScopeEmbeddingModel embeddingModel;
    private final ChatClient astraClient;
    private final IQueryRewriteService queryRewriteService;
    private final AstraProperties astraProperties;
    private final FeedbackProperties feedbackProperties;
    private final RerankClient rerankClient;

    // ==================== RAG 知识问答主链路 ====================

    @Override
    public Flux<AstraChatEvent> chat(Long userId, AstraChatRequest request) {
        long t0 = System.currentTimeMillis();
        log.info("【CHAT-SVC】开始 | userId={}, libraryId={}, prompt={}",
                userId, request.getLibraryId(), request.getPrompt());

        int rerankCandidateK = astraProperties.getSearch().getRerankCandidateK();

        // 1. 校验知识库存在
        // 注意：问答为开放访问模型——管理员负责维护（上传/删除有权限控制），
        // 所有登录用户均可查询，此处不做所有者校验
        KbLibrary library = libraryMapper.selectById(request.getLibraryId());
        if (library == null) {
            throw new BusinessException(ErrorCode.ASTRA_LIBRARY_NOT_FOUND);
        }

        log.info("【CHAT-SVC】库校验完成 | cost={}ms", System.currentTimeMillis() - t0);

        // 2. 获取或创建会话
        ChatSession session = getOrCreateSession(userId, request.getLibraryId(), request.getSessionId());
        AstraChatEvent sessionCreatedEvent = AstraChatEvent.sessionCreated(session.getId());
        log.info("【CHAT-SVC】会话创建完成 | sessionId={}, cost={}ms", session.getId(), System.currentTimeMillis() - t0);

        // 3. 混合检索（含反馈库时复用同一次重写/Embedding）
        long t1 = System.currentTimeMillis();
        List<ChunkResponse> searchResults;
        try {
            searchResults = feedbackProperties.getSearch().isEnabled()
                    ? hybridSearchWithFeedback(request.getLibraryId(), request.getPrompt(), rerankCandidateK)
                    : hybridSearch(request.getLibraryId(), request.getPrompt(), rerankCandidateK);
            log.info("【CHAT-SVC】混合检索完成 | results={}, cost={}ms",
                    searchResults.size(), System.currentTimeMillis() - t1);
        } catch (BusinessException e) {
            log.warn("【CHAT-SVC】混合检索异常 | code={}", e.getCode(), e);
            if (ErrorCode.ASTRA_LIBRARY_EMPTY.getCode().equals(e.getCode())) {
                return Flux.just(
                        sessionCreatedEvent,
                        AstraChatEvent.answer("抱歉，该知识库还没有上传任何文档，无法回答您的问题。"),
                        AstraChatEvent.complete(session.getId(), List.of())
                );
            }
            throw e;
        }

        // 4. ReRank
        long t2 = System.currentTimeMillis();
        List<ChunkResponse> rerankedResults = rerank(request.getLibraryId(), request.getPrompt(), searchResults, rerankCandidateK);
        log.info("【CHAT-SVC】ReRank 完成 | results={}, cost={}ms",
                rerankedResults.size(), System.currentTimeMillis() - t2);

        // 5. 引用来源（答案溯源）
        List<String> citations = rerankedResults.stream()
                .map(ChunkResponse::getSource)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        // 6. 构建 Prompt（支持风格切换）
        String style = request.getStyle() != null ? request.getStyle() : "detail";
        String prompt = buildPrompt(request.getPrompt(), rerankedResults, style);
        log.info("【CHAT-SVC】Prompt 构建完成 | style={}, promptLength={}, cost={}ms",
                style, prompt.length(), System.currentTimeMillis() - t0);

        // 7. 流式调用 LLM，事件结构化输出
        Flux<AstraChatEvent> answerFlux = astraClient.prompt()
                .user(prompt)
                .stream()
                .content()
                .map(AstraChatEvent::answer);

        // TODO(消息持久化接入后): complete 事件的 messageId 替换为真实消息 ID，
        //  当前与旧实现保持一致，暂传 sessionId
        return Flux.concat(
                        Flux.just(sessionCreatedEvent),
                        Flux.just(AstraChatEvent.thinking("正在检索相关文档...")),
                        Flux.just(AstraChatEvent.thinking("找到 " + rerankedResults.size() + " 个相关片段，进行重排序...")),
                        answerFlux,
                        Flux.just(AstraChatEvent.complete(session.getId(), citations))
                ).doOnComplete(() -> log.info("【CHAT-SVC】全部完成 | totalCost={}ms", System.currentTimeMillis() - t0))
                .doOnError(e -> log.error("【CHAT-SVC】流式输出异常 | totalCost={}ms", System.currentTimeMillis() - t0, e));
    }

    // ==================== 文件直传对话 ====================

    @Override
    public Flux<AstraChatEvent> chatWithFile(Long userId, AstraChatRequest request) {
        long t0 = System.currentTimeMillis();
        log.info("【CHAT-SVC】文件直传分支 | userId={}, libraryId={}", userId, request.getLibraryId());

        // 仅需要会话上下文，不做知识库权限校验（与旧实现行为一致）
        ChatSession session = getOrCreateSession(userId, request.getLibraryId(), request.getSessionId());

        Flux<AstraChatEvent> answerFlux = astraClient.prompt()
                .user(request.getPrompt())
                .stream()
                .content()
                .map(AstraChatEvent::answer);

        return Flux.concat(
                Flux.just(AstraChatEvent.sessionCreated(session.getId())),
                answerFlux,
                Flux.just(AstraChatEvent.complete(session.getId(), List.of()))
        ).doOnComplete(() -> log.info("【CHAT-SVC】文件直传分支完成 | totalCost={}ms", System.currentTimeMillis() - t0));
    }

    // ==================== 检索 ====================

    @Override
    public List<ChunkResponse> hybridSearch(Long libraryId, String query, int topK) {
        log.debug("混合检索: libraryId={}, query={}", libraryId, query);

        long chunkCount = chunkMapper.countByLibraryId(libraryId);
        if (chunkCount == 0) {
            throw new BusinessException(ErrorCode.ASTRA_LIBRARY_EMPTY);
        }

        return doHybridSearch(List.of(libraryId), libraryId, query, topK);
    }

    /**
     * 联合检索：官方库 + 同名反馈库
     *
     * 重构：原实现调用两次完整 hybridSearch（两次 Query 重写 + 两次 Embedding +
     * 两次 RRF SQL），现改为：
     * 1. Query 重写一次、Embedding 一次
     * 2. 目标库集合（主库 + 反馈库）一次 IN 查询，RRF 在 SQL 层统一融合
     * 3. 反馈库分片在结果映射时打 [反馈] 标记
     */
    private List<ChunkResponse> hybridSearchWithFeedback(Long libraryId, String query, int topK) {
        // 主库为空直接返回空库提示（保持旧行为）
        if (chunkMapper.countByLibraryId(libraryId) == 0) {
            throw new BusinessException(ErrorCode.ASTRA_LIBRARY_EMPTY);
        }

        // 目标库集合：主库 + 同名反馈库
        List<Long> libraryIds = new ArrayList<>();
        libraryIds.add(libraryId);

        KbLibrary mainLib = libraryMapper.selectById(libraryId);
        if (mainLib != null) {
            KbLibrary feedbackLib = libraryMapper.selectByName(mainLib.getName() + "_feedback");
            if (feedbackLib != null) {
                libraryIds.add(feedbackLib.getId());
            }
        }

        return doHybridSearch(libraryIds, libraryId, query, topK);
    }

    /**
     * 混合检索公共实现：一次重写 + 一次 Embedding + 一次 RRF SQL
     *
     * @param libraryIds    目标库集合（单库时为单元素列表）
     * @param mainLibraryId 主库 ID，用于标记反馈库来源；单库场景传 null
     */
    private List<ChunkResponse> doHybridSearch(List<Long> libraryIds, Long mainLibraryId, String query, int topK) {
        // 1. Query 重写（失败回退原 query）
        String rewrittenQuery;
        try {
            rewrittenQuery = queryRewriteService.rewrite(query);
        } catch (Exception e) {
            log.warn("Query 重写失败，使用原始 query: query={}", query, e);
            rewrittenQuery = query;
        }

        // 2. 一次生成查询向量（多库共用）
        float[] queryEmbedding = embeddingModel.embed(rewrittenQuery);

        // 3. 一次 RRF 混合检索（IN 查询，SQL 层融合）
        String queryTerms = rewrittenQuery.toLowerCase().replaceAll("\\s+", " ");
        AstraProperties.Search search = astraProperties.getSearch();
        List<KbChunkSearchResult> results = chunkMapper.hybridSearchRrf(
                libraryIds, queryEmbedding, queryTerms,
                search.getTopK().getVector(), search.getTopK().getBm25(),
                search.getEfSearch(), search.getRrfK(), search.getRrfOutputTopK());

        // 4. 上下文召回：命中 chunk 带上前后邻居
        results = expandWithContext(results);

        // 5. 映射为响应（反馈库打标记）+ 内容去重，截取 topK*2 给 ReRank
        Set<String> seenContents = new HashSet<>();
        return results.stream()
                .map(r -> toChunkResponse(r, mainLibraryId))
                .filter(c -> seenContents.add(c.getContent()))
                .limit(topK * 2L)
                .collect(Collectors.toList());
    }

    /**
     * 上下文扩展 - 按文档合并连续命中的 chunk，避免 Prompt 重复
     * 核心逻辑：
     * 1. 按 mediaId 分组
     * 2. 每组内按 chunkIndex 排序，合并连续区间（如 5,6,7 → [5,7]）
     * 3. 每个区间一次性查询 [起点-1, 终点+1] 的邻居，和区间内 chunk 合并去重
     * 4. 按 chunkIndex 顺序拼接成"超级 chunk"
     *
     * 重构：区间内内容用 Map O(1) 查找（原为 O(n²) 流式 filter）；
     * 邻居查询由每区间 2 次合并为 1 次；新增跨区间去重（consumed 集合），
     * 避免相邻两个区间重复带上同一邻居 chunk。
     */
    private List<KbChunkSearchResult> expandWithContext(List<KbChunkSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return results;
        }

        // 按 mediaId 分组（只处理有 chunkIndex 的）
        Map<Long, List<KbChunkSearchResult>> byMedia = results.stream()
                .filter(r -> r.getMediaId() != null && r.getChunkIndex() != null)
                .collect(Collectors.groupingBy(KbChunkSearchResult::getMediaId));

        List<KbChunkSearchResult> expanded = new ArrayList<>();

        for (List<KbChunkSearchResult> mediaChunks : byMedia.values()) {
            // 按 chunkIndex 排序
            mediaChunks.sort(Comparator.comparingInt(KbChunkSearchResult::getChunkIndex));

            // 命中序号 -> 结果，O(1) 查找
            Map<Integer, KbChunkSearchResult> hitByIndex = new HashMap<>();
            for (KbChunkSearchResult c : mediaChunks) {
                hitByIndex.put(c.getChunkIndex(), c);
            }

            // 跨区间去重：已被某区间扩展消费的邻居不再重复带上
            Set<Integer> consumed = new HashSet<>();

            int i = 0;
            while (i < mediaChunks.size()) {
                // ===== 1. 找出连续区间 [rangeStart, rangeEnd] =====
                int rangeStart = mediaChunks.get(i).getChunkIndex();
                int rangeEnd = rangeStart;

                while (i + 1 < mediaChunks.size()
                        && mediaChunks.get(i + 1).getChunkIndex() == rangeEnd + 1) {
                    rangeEnd++;
                    i++;
                }

                Long mediaId = mediaChunks.get(i).getMediaId();

                // ===== 2. 组装内容：区间内命中 + 前后邻居（一次查询）=====
                Map<Integer, String> indexToContent = new TreeMap<>();

                // 区间内 chunk（优先用 RRF 结果里的 content）
                for (int idx = rangeStart; idx <= rangeEnd; idx++) {
                    KbChunkSearchResult hit = hitByIndex.get(idx);
                    if (hit != null && hit.getContent() != null && !hit.getContent().isEmpty()) {
                        indexToContent.put(idx, hit.getContent());
                    }
                }

                // 前后邻居：[rangeStart-1, rangeEnd+1] 一次查询
                // 排除区间本身，consumed 保证跨区间不重复
                for (KbChunk neighbor : chunkMapper.findRangeNeighbors(mediaId, rangeStart - 1, rangeEnd + 1)) {
                    int idx = neighbor.getChunkIndex();
                    if (idx >= rangeStart && idx <= rangeEnd) {
                        continue;
                    }
                    if (consumed.add(idx) && neighbor.getContent() != null) {
                        indexToContent.put(idx, neighbor.getContent());
                    }
                }

                // ===== 3. 按顺序拼接成"超级 chunk" =====
                StringBuilder fullContent = new StringBuilder();
                for (String content : indexToContent.values()) {
                    if (!fullContent.isEmpty()) {
                        fullContent.append("\n\n");
                    }
                    fullContent.append(content);
                }

                // ===== 4. 用区间内 RRF 分数最高的作为代表 =====
                KbChunkSearchResult representative = null;
                for (int idx = rangeStart; idx <= rangeEnd; idx++) {
                    KbChunkSearchResult hit = hitByIndex.get(idx);
                    if (hit == null) continue;
                    if (representative == null
                            || (hit.getRrfScore() != null
                            && (representative.getRrfScore() == null || hit.getRrfScore() > representative.getRrfScore()))) {
                        representative = hit;
                    }
                }
                if (representative == null) {
                    representative = mediaChunks.get(i);
                }

                representative.setContent(fullContent.toString());
                expanded.add(representative);

                i++;
            }
        }

        // 保留那些没有 mediaId 或 chunkIndex 的原始结果
        results.stream()
                .filter(r -> r.getMediaId() == null || r.getChunkIndex() == null)
                .forEach(expanded::add);

        // 按 RRF 分数降序排序（无分数的排最后）
        expanded.sort(Comparator.comparing(KbChunkSearchResult::getRrfScore,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return expanded;
    }

    // ==================== ReRank（委托 RerankClient） ====================

    @Override
    public List<ChunkResponse> rerank(Long libraryId, String query, List<ChunkResponse> chunks, int topK) {
        log.debug("ReRank重排序: libraryId={}, query={}, chunks={}, topK={}",
                libraryId, query, chunks != null ? chunks.size() : 0, topK);
        return rerankClient.rerank(query, chunks, topK);
    }

    // ==================== Prompt 构建 ====================

    @Override
    public String buildPrompt(String query, List<ChunkResponse> chunks) {
        return buildPrompt(query, chunks, "detail"); // 默认详细风格
    }

    /**
     * 支持切换回答风格的 Prompt 构建，并且支持搜索反馈知识库的文档
     *
     * @param style "detail"=详细文档风格（查资料用）, "concise"=精简客服风格（快速问答用）
     */
    @Override
    public String buildPrompt(String query, List<ChunkResponse> chunks, String style) {
        if (chunks == null || chunks.isEmpty()) {
            return "用户问题: " + query + "\n\n参考内容: 无相关文档";
        }

        // 分离官方和反馈
        List<ChunkResponse> officialChunks = new ArrayList<>();
        List<ChunkResponse> feedbackChunks = new ArrayList<>();

        for (ChunkResponse chunk : chunks) {
            String source = chunk.getSource() != null ? chunk.getSource() : "未知来源";
            if (source.startsWith("[反馈]")) {
                feedbackChunks.add(chunk);
            } else {
                officialChunks.add(chunk);
            }
        }

        StringBuilder context = new StringBuilder();

        // 先拼官方
        if (!officialChunks.isEmpty()) {
            context.append("===== 官方文档（权威依据） =====\n");
            for (ChunkResponse chunk : officialChunks) {
                context.append(String.format("[%s] %s\n\n", chunk.getSource(), chunk.getContent()));
            }
        }

        // 再拼反馈
        if (!feedbackChunks.isEmpty()) {
            context.append("===== 历史工单反馈（仅供参考） =====\n");
            for (ChunkResponse chunk : feedbackChunks) {
                context.append(String.format("[%s] %s\n\n", chunk.getSource(), chunk.getContent()));
            }
        }

        if ("concise".equalsIgnoreCase(style)) {
            return String.format("""
                    # 知识库问答

                    ## 用户问题
                    %s

                    ## 参考内容
                    %s

                    ## 回答要求（必须严格遵守）
                    1. 优先基于【官方文档】给出准确、可操作的步骤
                    2. 如果官方文档无法完全回答，可补充【历史工单反馈】中的信息，但必须标注"根据历史工单..."
                    3. 每条建议控制在 50 字以内，禁止大段复述原文
                    4. 禁止输出文档的章节标题
                    5. 不知道的内容明确说明，禁止编造
                    """, query, context.toString());
        } else {
            return String.format("""
                    # 知识库问答

                    ## 用户问题
                    %s

                    ## 参考内容
                    %s

                    ## 回答要求（必须严格遵守）
                    1. 你是企业知识库助手，请根据参考内容给出准确、完整的回答
                    2. 【官方文档】为权威依据，优先引用；【历史工单反馈】仅作补充参考
                    3. 必须引用来源，官方文档标注【来源：xxx】，反馈知识标注【来源：[反馈] xxx】
                    4. 如果参考内容无法回答，明确说明"根据现有资料无法确定"
                    5. 禁止编造参考内容中没有的信息
                    6. 回答结构清晰，分点说明
                    7. 禁止使用 Markdown 标题（#）、表格等重排版语法，仅使用普通段落与序号列表，保证在简洁的聊天界面中显示正常
                    """, query, context.toString());
        }
    }

    // ==================== 会话 ====================

    @Override
    public ChatSession getOrCreateSession(Long userId, Long libraryId, Long sessionId) {
        if (sessionId != null) {
            // 验证会话存在且属于该用户
            ChatSession session = chatSessionService.getSessionById(sessionId);
            if (session != null && session.getUserId().equals(userId)) {
                return session;
            }
            throw new BusinessException(ErrorCode.ASTRA_SESSION_NOT_FOUND);
        }

        // 获取知识库名称作为会话标题前缀
        KbLibrary library = libraryMapper.selectById(libraryId);
        String title = (library != null ? library.getName() : "知识库") + " 问答";

        // 创建新会话
        return chatSessionService.createSession(userId, "astra", title);
    }

    // ==================== 内部工具方法 ====================

//    /**
//     * 知识库访问权限校验：仅所有者可问答
//     * 注意：与 AstraMediaServiceImpl 的校验规则保持一致；
//     * 若未来支持 team 类型库共享，在此扩展规则。
//     */
//    private void checkLibraryPermission(KbLibrary library, Long userId) {
//        if (!library.getOwnerId().equals(userId)) {
//            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限访问该知识库");
//        }
//    }

    /**
     * 检索结果 → ChunkResponse，反馈库分片打 [反馈] 标记
     */
    private ChunkResponse toChunkResponse(KbChunkSearchResult result, Long mainLibraryId) {
        String source = extractSource(result.getMetadata());
        if (mainLibraryId != null && !mainLibraryId.equals(result.getLibraryId())) {
            source = source != null ? "[反馈] " + source : "[反馈知识]";
        }

        ChunkResponse response = ChunkResponse.builder()
                .id(result.getId())
                .libraryId(result.getLibraryId())
                .mediaId(result.getMediaId())
                .content(result.getContent())
                .chunkIndex(result.getChunkIndex())
                .metadata(result.getMetadata())
                .source(source)
                .build();

        if (result.getRrfScore() != null) {
            response.setScore(result.getRrfScore().floatValue());
        }
        if (result.getVectorScore() != null) {
            response.setVectorScore(result.getVectorScore().floatValue());
        }
        if (result.getBm25Score() != null) {
            response.setBm25Score(result.getBm25Score().floatValue());
        }
        return response;
    }

    /**
     * 从元数据提取来源标识：文件名-第N页 / 文件名
     */
    private String extractSource(Map<String, Object> metadata) {
        if (metadata == null) {
            return null;
        }
        Object fileName = metadata.get("fileName");
        Object page = metadata.get("page");
        if (fileName != null && page != null) {
            return fileName + "-第" + page + "页";
        }
        if (fileName != null) {
            return fileName.toString();
        }
        return null;
    }
}