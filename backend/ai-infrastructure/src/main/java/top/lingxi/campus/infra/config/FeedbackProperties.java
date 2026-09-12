package top.lingxi.campus.infra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 报修单反馈功能配置
 *
 * Phase 3 收口说明：原 AstraSearchServiceImpl 通过
 * @Value("${feedback.search.enabled:false}) 读取，现收敛为类型安全的配置类。
 * 配置键与 application.yml 中已有的 feedback.* 完全一致，无需迁移：
 *
 * feedback:
 *   enabled: false
 *   search:
 *     enabled: false
 */
@Data
@Component
@ConfigurationProperties(prefix = "feedback")
public class FeedbackProperties {

    /**
     * 工单沉淀总开关
     */
    private boolean enabled = false;

    private Search search = new Search();

    @Data
    public static class Search {
        /**
         * 是否联合检索反馈库（默认 false，保守）
         */
        private boolean enabled = false;
    }
}