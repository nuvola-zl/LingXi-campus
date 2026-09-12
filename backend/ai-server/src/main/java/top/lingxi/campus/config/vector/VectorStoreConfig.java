package top.lingxi.campus.config.vector;

import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.lingxi.campus.domain.ai.mapper.KbChunkMapper;
import top.lingxi.campus.rag.vector.HybridVectorStore;

/**
 * VectorStore 配置
 */
@Configuration
public class VectorStoreConfig {

    @Bean
    public HybridVectorStore hybridVectorStore(
            KbChunkMapper chunkMapper,
            DashScopeEmbeddingModel embeddingModel) {
        return new HybridVectorStore(chunkMapper, embeddingModel);
    }
}