package top.lingxi.campus.config.model;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import top.lingxi.campus.infra.config.AstraProperties;
import top.lingxi.campus.infra.config.ModelProperties;

@Configuration
@RequiredArgsConstructor
public class ModelConfiguration {

    private final ModelProperties modelProperties;
    private final AstraProperties astraProperties;

    @Bean
    @Primary
    public DashScopeChatModel textChatModel(){
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(modelProperties.getApiKey())
                .build();

        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .model("qwen-flash")
                        .enableThinking(true)
                        .stream(true)
                        .temperature(0.8)
                        .maxToken(2000)
                        .build()
                )
                .build();
    }

    /**
     * Agent 决策专用轻量模型
     * ReAct 决策是简单三分类任务（search_knowledge / create_ticket / respond），
     * 不需要深度思考、不需要流式输出、不需要高温度。
     * 关掉 thinking + 降低 maxToken + 降低 temperature，延迟从 5-12s 降到 1-3s。
     */
    @Bean("agentDecisionModel")
    public DashScopeChatModel agentDecisionModel() {
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(modelProperties.getApiKey())
                .build();

        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .model("qwen-flash")
                        .enableThinking(false)
                        .temperature(0.3)
                        .maxToken(512)
                        .build()
                )
                .build();
    }

    /**
     * Embedding 模型
     * Phase 3 修复：模型名由硬编码改为读取 astra.embedding.model
     * （对齐 application.yml 中已有的配置，该配置此前是死配置）
     * 注意：更换 embedding 模型必须全量重建索引，新旧向量空间不一致
     */
    @Bean
    public DashScopeEmbeddingModel dashScopeEmbeddingModel(){
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(modelProperties.getApiKey())
                .build();

        return DashScopeEmbeddingModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(
                        DashScopeEmbeddingOptions.builder()
                                .withModel(astraProperties.getEmbedding().getModel())
                                .build()
                )
                .build();
    }
}