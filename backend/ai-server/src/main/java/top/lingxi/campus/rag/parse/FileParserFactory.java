package top.lingxi.campus.rag.parse;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件解析器工厂（聊天直传等非入库场景使用）
 *
 * Phase 2 修复：原实现 Map 懒加载无线程同步，worker 线程池并发调用
 * getParser 时存在重复初始化竞态。现改为 ConcurrentHashMap + 双重检查，
 * 保证线程安全。
 *
 * 注意：知识库入库链路已迁移至 IngestionPipeline + DocumentTextExtractor，
 * 本工厂仅服务 extractText（聊天上传文本提取）等遗留调用点。
 */
@Component
@RequiredArgsConstructor
public class FileParserFactory {

    /**
     * Spring 自动注入全部 FileParser 实现
     */
    private final List<FileParser> parsers;

    private volatile Map<String, FileParser> parserMap;

    /**
     * 获取解析器映射（双重检查 + ConcurrentHashMap，线程安全）
     */
    private Map<String, FileParser> getParserMap() {
        if (parserMap == null) {
            synchronized (this) {
                if (parserMap == null) {
                    Map<String, FileParser> map = new ConcurrentHashMap<>();
                    for (FileParser parser : parsers) {
                        // 如果重复,保留已有的解析器
                        map.putIfAbsent(parser.getFileType().toUpperCase(), parser);
                    }
                    parserMap = map;
                }
            }
        }
        return parserMap;
    }

    /**
     * 获取解析器
     * @param fileType 文件类型
     * @return 对应的解析器，如果不存在返回 null
     */
    public FileParser getParser(String fileType) {
        return getParserMap().get(fileType.toUpperCase());
    }

    /**
     * 检查是否支持该文件类型
     */
    public boolean supports(String fileType) {
        return getParserMap().containsKey(fileType.toUpperCase());
    }
}