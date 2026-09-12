package top.lingxi.campus.rag.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import top.lingxi.campus.domain.biz.catalog.entity.BizServiceCatalog;
import top.lingxi.campus.domain.biz.catalog.mapper.BizServiceCatalogMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogKeywordService {

    private final BizServiceCatalogMapper catalogMapper;

    private final LoadingCache<String, List<CatalogMatchRule>> ruleCache = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .recordStats()
            .build(key -> loadFromDatabase());

    @PostConstruct
    public void init() {
        refresh();
    }

    @Scheduled(fixedRate = 300_000)
    public void refresh() {
        ruleCache.invalidate("all");
        ruleCache.get("all");
    }

    public MatchResult match(String userMessage) {
        List<CatalogMatchRule> rules = ruleCache.get("all");
        if (rules == null || rules.isEmpty()) return null;

        String msg = userMessage.toLowerCase();
        for (CatalogMatchRule rule : rules) {
            if (msg.contains(rule.getKeyword())) {
                return new MatchResult(rule.getCategoryId(), rule.getDomain(),
                        rule.getPriority(), rule.getGroupId());
            }
        }
        return null;
    }

    private List<CatalogMatchRule> loadFromDatabase() {
        try {
            List<BizServiceCatalog> catalogs = catalogMapper.selectAllWithCategory();
            List<CatalogMatchRule> rules = new ArrayList<>();

            for (BizServiceCatalog c : catalogs) {
                // ===== 改这里：直接拿，不用 readValue =====
                List<String> keywords = c.getTriggerKeywords();
                if (keywords == null || keywords.isEmpty()) continue;

                String domain = c.getCategoryCode() != null ? c.getCategoryCode().toUpperCase() : "IT";

                for (String kw : keywords) {
                    rules.add(new CatalogMatchRule(
                            kw.toLowerCase(),
                            c.getCategoryId(),
                            domain,
                            c.getDefaultPriority() != null ? c.getDefaultPriority() : 2,
                            c.getDefaultGroupId()
                    ));
                }
            }
            log.info("服务目录关键词缓存已加载: {} 条规则", rules.size());
            return Collections.unmodifiableList(rules);
        } catch (Exception e) {
            log.error("加载服务目录失败", e);
            return Collections.emptyList();
        }
    }

    @Data
    @RequiredArgsConstructor
    public static class MatchResult {
        private final Long categoryId;
        private final String domain;
        private final Integer priority;
        private final Long groupId;
    }

    @Data
    @RequiredArgsConstructor
    private static class CatalogMatchRule {
        private final String keyword;
        private final Long categoryId;
        private final String domain;
        private final Integer priority;
        private final Long groupId;
    }
}