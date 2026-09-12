package top.lingxi.campus.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 知识库检索结果 DTO
 * 统一接收 vectorSearch / bm25Search / hybridSearchRrf 的返回
 */
@Data
public class KbChunkSearchResult {

    // ========== KbChunk 实体字段 ==========
    private Long id;
    private Long libraryId;
    private Long mediaId;
    private String content;
    private Integer chunkIndex;
    private Map<String, Object> metadata;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    // ========== 检索得分字段（根据查询类型填充） ==========
    /** 向量相似度（cosine similarity），vectorSearch 时用 */
    private Double similarity;

    /** BM25 全文检索得分 */
    private Double bm25Score;

    /** 向量检索得分（hybridSearchRrf 内部用） */
    private Double vectorScore;

    /** RRF 融合后的最终得分 */
    private Double rrfScore;
}