package top.lingxi.campus.domain.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.lingxi.campus.domain.ai.entity.KbChunk;
import top.lingxi.campus.result.KbChunkSearchResult;

import java.util.List;

/**
 * 知识库分片 Mapper
 *
 * Phase 1 重构说明：
 * 1. hybridSearchRrf 的 libraryId 参数改为 List<Long>，
 *    支持主库+反馈库一次 IN 查询（原需调用两次完整检索）
 * 2. findNeighbors 升级为 findRangeNeighbors：一次查询覆盖
 *    [fromIndex, toIndex] 区间，替代原来的"每端点各查一次"
 */
@Mapper
public interface KbChunkMapper extends BaseMapper<KbChunk> {

    /**
     * 获取媒体文件的所有分片
     * @param mediaId 媒体ID
     * @return 分片列表
     */
    List<KbChunk> listByMediaId(@Param("mediaId") Long mediaId);

    /**
     * 获取知识库的所有分片
     * @param libraryId 知识库ID
     * @return 分片列表
     */
    List<KbChunk> listByLibraryId(@Param("libraryId") Long libraryId);

    /**
     * 删除媒体文件的所有分片
     * @param mediaId 媒体ID
     */
    void deleteByMediaId(@Param("mediaId") Long mediaId);

    /**
     * 删除知识库的所有分片
     * @param libraryId 知识库ID
     */
    void deleteByLibraryId(@Param("libraryId") Long libraryId);

    /**
     * 批量插入分片
     * @param chunks 分片列表
     */
    void batchInsert(@Param("chunks") List<KbChunk> chunks);

    /**
     * 获取分片数量
     * @param libraryId 知识库ID
     * @return 分片总数
     */
    long countByLibraryId(@Param("libraryId") Long libraryId);

    /**
     * 混合检索 RRF：单条 SQL 完成 BM25 + 向量双路召回 + Reciprocal Rank Fusion 合并
     * <p>将双路召回和 RRF 融合全部下推到 PostgreSQL 执行，减少 DB 往返次数和应用层合并开销</p>
     *
     * Phase 1: libraryIds 支持多库 IN 查询（官方库+反馈库一次融合），
     * 多库场景下向量/BM25 各自的 topK 是多库共享的召回池上限
     *
     * @param libraryIds     目标知识库ID集合
     * @param queryEmbedding 查询向量
     * @param queryTerms     搜索关键词（空格分隔）
     * @param vectorTopK     向量检索召回数量
     * @param bm25TopK       BM25 检索召回数量
     * @param efSearch       HNSW ef_search 参数
     * @param rrfK           RRF 平滑因子（通常为 60）
     * @param topK           最终输出数量
     * @return 包含 KbChunk、rrf_score、vector_score、bm25_score 的 Map 列表
     */
    List<KbChunkSearchResult> hybridSearchRrf(
            @Param("libraryIds") List<Long> libraryIds,
            @Param("queryEmbedding") float[] queryEmbedding,
            @Param("queryTerms") String queryTerms,
            @Param("vectorTopK") int vectorTopK,
            @Param("bm25TopK") int bm25TopK,
            @Param("efSearch") int efSearch,
            @Param("rrfK") int rrfK,
            @Param("topK") int topK);

    /**
     * 查询指定序号区间内的分片（含端点），一次查询替代多次单点邻居查询
     * @param mediaId   文件ID
     * @param fromIndex 起始 chunkIndex（如 rangeStart - 1）
     * @param toIndex   结束 chunkIndex（如 rangeEnd + 1）
     */
    List<KbChunk> findRangeNeighbors(@Param("mediaId") Long mediaId,
                                     @Param("fromIndex") int fromIndex,
                                     @Param("toIndex") int toIndex);

    @Select("SELECT id, library_id AS libraryId, media_id AS mediaId, content, embedding, chunk_index AS chunkIndex, metadata, created_at AS createdAt " +
            "FROM kb_chunk " +
            "WHERE library_id = #{libraryId}")
    List<KbChunk> selectByLibraryId(@Param("libraryId") Long libraryId);

    @Select("SELECT * FROM kb_chunk WHERE library_id = #{libraryId} AND metadata->>'source' = #{source}")
    List<KbChunk> selectByLibraryIdAndSource(@Param("libraryId") Long libraryId, @Param("source") String source);
}