package top.lingxi.campus.ai.service.impl;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import top.lingxi.campus.domain.biz.keyWord.eneity.IntentKeyword;
import top.lingxi.campus.domain.biz.keyWord.mapper.IntentKeywordMapper;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntentKeywordService {

    private final IntentKeywordMapper keywordMapper;

    // Caffeine 本地缓存，5分钟过期，和 CatalogKeywordService 一样
    private final LoadingCache<String, List<IntentKeyword>> keywordCache = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .recordStats()
            .build(key -> loadFromDatabase());

    @PostConstruct
    public void init() {
        refresh();
    }

    /**
     * 刷新缓存（手动调用或定时任务）
     */
    @Scheduled(fixedRate = 300_000) // 5分钟自动刷新
    public void refresh() {
        keywordCache.invalidate("all");
        keywordCache.get("all");
    }

    /**
     * 关键词匹配意图
     * @return 匹配到的意图，没匹配到返回 null
     */
    public String matchKeyword(String message) {
        if (message == null || message.trim().isEmpty()) {
            return null;
        }

        List<IntentKeyword> keywords = keywordCache.get("all");
        if (keywords == null || keywords.isEmpty()) {
            return null;
        }

        String lowerMsg = message.toLowerCase().trim();
        Map<String, Integer> scores = new HashMap<>();

        for (IntentKeyword k : keywords) {
            boolean matched = switch (k.getMatchType()) {
                case "exact" -> lowerMsg.equals(k.getKeyword().toLowerCase());
                case "contains" -> lowerMsg.contains(k.getKeyword().toLowerCase());
                default -> lowerMsg.contains(k.getKeyword().toLowerCase());
            };

            if (matched) {
                scores.merge(k.getIntent(), k.getWeight(), Integer::sum);
            }
        }

        // 取最高分，超过阈值才认定
        return scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .filter(e -> e.getValue() >= 2)
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private List<IntentKeyword> loadFromDatabase() {
        try {
            List<IntentKeyword> keywords = keywordMapper.selectAllEnabled();
            log.info("意图关键词缓存已加载: {} 条规则", keywords.size());
            return Collections.unmodifiableList(keywords);
        } catch (Exception e) {
            log.error("加载意图关键词失败", e);
            return Collections.emptyList();
        }
    }
}