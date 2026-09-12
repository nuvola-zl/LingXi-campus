package top.lingxi.campus.rag.ingestion;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文本提取器工厂
 *
 * Phase 2 修复：原 FileParserFactory 的 Map 懒加载无线程同步（worker 线程池
 * 并发调用存在重复初始化竞态）。本实现构造期一次性构建不可变 Map，
 * 天然线程安全且无重复初始化。
 */
@Component
public class TextExtractorFactory {

    /**
     * key: 文件类型（大写）; value: 提取器。构造期构建，不可变，线程安全。
     */
    private final Map<String, DocumentTextExtractor> extractorMap;

    /**
     * Spring 自动注入全部 DocumentTextExtractor 实现
     */
    public TextExtractorFactory(List<DocumentTextExtractor> extractors) {
        Map<String, DocumentTextExtractor> map = new HashMap<>();
        for (DocumentTextExtractor extractor : extractors) {
            // 如果重复注册同类型，保留先注册的
            map.putIfAbsent(extractor.getFileType().toUpperCase(), extractor);
        }
        this.extractorMap = Collections.unmodifiableMap(map);
    }

    /**
     * 获取提取器
     *
     * @param fileType 文件类型
     * @return 对应的提取器，不存在返回 null
     */
    public DocumentTextExtractor getExtractor(String fileType) {
        if (fileType == null) {
            return null;
        }
        return extractorMap.get(fileType.toUpperCase());
    }

    /**
     * 检查是否支持该文件类型
     */
    public boolean supports(String fileType) {
        return getExtractor(fileType) != null;
    }
}