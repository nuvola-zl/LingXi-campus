package top.lingxi.campus.infra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Astra 知识库配置属性
 *
 * Phase 2 变更：Upload.allowedTypes 白名单收敛为知识库实际支持入库的
 * 三种 MIME 类型（PDF / DOCX / TXT）。MD 文件走 text/plain 通道，
 * 由上传侧按 .md 后缀识别。
 * 对应 application.yml 的 astra.upload.allowed-types 也需同步收敛。
 */
@Data
@Component
@ConfigurationProperties(prefix = "astra")
public class AstraProperties {

    private QueryRewrite queryRewrite = new QueryRewrite();
    private Search search = new Search();
    private Rerank rerank = new Rerank();
    private Parser parser = new Parser();
    private Upload upload = new Upload();
    private Embedding embedding = new Embedding();

    @Data
    public static class QueryRewrite {
        private boolean enabled = true;
        private String model = "qwen-flash";
        private int maxTokens = 128;
        private double temperature = 0.3;
    }

    @Data
    public static class Search {
        private TopK topK = new TopK();
        /**
         * RRF 合并后的输出数量，取前 N 个送入 ReRank
         */
        private int rrfOutputTopK = 30;
        /**
         * RRF 平滑因子，值越大则高排名与低排名的分差越小
         * 通常取 60，详见 Reciprocal Rank Fusion 论文
         */
        private int rrfK = 60;
        /**
         * pgvector HNSW ef_search 参数：候选集大小，越大召回率越高但越慢
         * 配合 iterative_scan=relaxed_order 使用，建议范围 100~400
         */
        private int efSearch = 200;
        /**
         * 送入 ReRank 的候选数量（原 AstraSearchServiceImpl.RERANK_TOP_K 硬编码 10）
         */
        private int rerankCandidateK = 10;

        @Data
        public static class TopK {
            private int bm25 = 50;
            private int vector = 50;
        }
    }

    @Data
    public static class Rerank {
        private boolean enabled = true;
        /**
         * 默认值修正：线上实际使用 qwen3-rerank（DashScope 兼容模式 rerank 端点）
         */
        private String model = "qwen3-rerank";
        private int topK = 10;
    }

    /**
     * 文档切分参数
     * 配置键：astra.parser.chunk.*
     */
    @Data
    public static class Parser {
        private Chunk chunk = new Chunk();

        @Data
        public static class Chunk {
            /** 目标 chunk 大小（token 估算），建议 500~1000 */
            private int targetSize = 800;
            /** 重叠比例，防止语义在边界丢失 */
            private double overlapRatio = 0.2;
            /** 最小 chunk，小于此值合并到前一块 */
            private int minSize = 100;
            /** 单段落超过此值强制按句子/固定长度切分 */
            private int maxParagraphSize = 1200;
        }
    }

    /**
     * 文件上传限制（Phase 2：白名单收敛为知识库实际支持的入库类型）
     * 配置键：astra.upload.*
     */
    @Data
    public static class Upload {
        /** 单文件大小上限（MB） */
        private int maxFileSize = 100;
        /**
         * 允许的 MIME 类型白名单。
         * 图片/音频/XMind 已不属于知识库入库范围（入库链路仅支持 PDF/DOCX/TXT/MD），
         * 如聊天直传场景需要，请在该场景单独维护白名单。
         */
        private List<String> allowedTypes = List.of(
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "text/plain"
        );
    }

    /**
     * Embedding 模型配置
     * 配置键：astra.embedding.*
     * 注意：更换模型必须全量重建索引（向量空间不一致，旧向量失效）
     */
    @Data
    public static class Embedding {
        private String model = "text-embedding-v3";
        private int dimensions = 1024;
        private int maxTokens = 8192;
    }
}