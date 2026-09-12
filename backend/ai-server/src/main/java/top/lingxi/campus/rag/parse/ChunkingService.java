package top.lingxi.campus.rag.parse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.lingxi.campus.infra.config.AstraProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能分块服务
 *
 * Phase 3 收口说明：4 个切分参数由 @Value 注入改为 AstraProperties 强类型配置
 * （构造器注入）。配置键不变（astra.parser.chunk.*），yml 无需迁移。
 *
 * 设计要点：
 * 1. Token 与字符不混用，重叠量基于 token 比例反推字符数
 * 2. 超长段落（代码/表格/大段文字）强制拆分
 * 3. 保守的 token 估算，避免实际超出模型限制
 * 4. 重叠内容保证语义完整（从后往前找句子边界，且保证最小重叠量）
 * 5. 合并小段落时检查上限，避免产生超大块
 * 6. 代码/表格段落改用固定长度硬切，避免被句子边界切得稀碎
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkingService {

    private final AstraProperties astraProperties;

    /**
     * 对文本进行智能分块
     */
    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }

        AstraProperties.Parser.Chunk config = astraProperties.getParser().getChunk();
        int targetChunkSize = config.getTargetSize();
        int minChunkSize = config.getMinSize();
        int maxParagraphSize = config.getMaxParagraphSize();

        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        int currentTokenSize = 0;

        // 按段落分割（兼容多空行）
        String[] paragraphs = text.split("\n\\s*\n");

        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (paragraph.isBlank()) {
                continue;
            }

            int paraTokenSize = estimateTokenSize(paragraph);

            // 单段落超过上限：先落袋当前累积，再用专用逻辑拆分该段落
            if (paraTokenSize > maxParagraphSize) {
                if (!currentChunk.isEmpty()) {
                    addChunk(chunks, currentChunk.toString().trim());
                    currentChunk.setLength(0);
                    currentTokenSize = 0;
                }
                chunks.addAll(splitLargeParagraph(paragraph));
                continue;
            }

            // 当前累积 + 新段落超过目标大小，先保存当前块
            if (currentTokenSize > 0 && currentTokenSize + paraTokenSize > targetChunkSize) {
                addChunk(chunks, currentChunk.toString().trim());

                // 新块以旧块末尾重叠内容开头
                String overlap = extractOverlap(currentChunk.toString());
                currentChunk.setLength(0);
                currentTokenSize = 0;
                if (!overlap.isEmpty()) {
                    currentChunk.append(overlap);
                    currentTokenSize = estimateTokenSize(overlap);
                }
            }

            // 追加段落
            if (!currentChunk.isEmpty()) {
                currentChunk.append("\n\n");
            }
            currentChunk.append(paragraph);
            currentTokenSize += paraTokenSize;
        }

        // 处理最后剩余的内容
        if (!currentChunk.isEmpty()) {
            String lastChunk = currentChunk.toString().trim();
            if (!chunks.isEmpty() && estimateTokenSize(lastChunk) < minChunkSize) {
                // 尝试合并到前一块，但检查合并后是否超限
                String merged = chunks.get(chunks.size() - 1) + "\n\n" + lastChunk;
                if (estimateTokenSize(merged) <= (int) (targetChunkSize * 1.2)) {
                    chunks.set(chunks.size() - 1, merged);
                } else {
                    chunks.add(lastChunk);
                }
            } else {
                chunks.add(lastChunk);
            }
        }

        log.debug("文本分块完成: {} 个分块, 原文约 {} tokens", chunks.size(), estimateTokenSize(text));
        return chunks;
    }

    /**
     * 判断段落是否为代码/表格类内容（无中文标点、换行多、特征符号密集）
     */
    private boolean isCodeLike(String text) {
        // 有中文句号的，大概率是正常文本
        if (text.indexOf('。') >= 0 || text.indexOf('！') >= 0 || text.indexOf('？') >= 0) {
            return false;
        }

        long lineBreaks = text.chars().filter(c -> c == '\n').count();
        if (lineBreaks < 3) {
            return false;
        }

        // 代码特征符号密度
        int codeChars = 0;
        for (char c : text.toCharArray()) {
            if ("{};[]=+-*/<>|&!@#$%^`~\\\"".indexOf(c) >= 0) {
                codeChars++;
            }
        }
        return (double) codeChars / text.length() > 0.03;
    }

    /**
     * 拆分超大段落：代码/表格用固定长度硬切，普通文本按句子切分
     */
    private List<String> splitLargeParagraph(String paragraph) {
        if (isCodeLike(paragraph)) {
            return splitByFixedLength(paragraph);
        }
        return splitBySentences(paragraph);
    }

    /**
     * 代码/表格专用：按固定字符长度切分 + 重叠
     * 不在代码内部找句子边界，避免把代码切得稀碎
     */
    private List<String> splitByFixedLength(String text) {
        AstraProperties.Parser.Chunk config = astraProperties.getParser().getChunk();
        List<String> result = new ArrayList<>();
        // 粗略：1 token ≈ 1.5 字符（代码中英文字符多）
        int chunkChars = (int) (config.getTargetSize() * 1.5);
        int overlapChars = (int) (chunkChars * config.getOverlapRatio());

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkChars, text.length());

            // 尽量在换行处截断，保持代码行完整
            if (end < text.length()) {
                int lastBreak = text.lastIndexOf('\n', end);
                if (lastBreak > start + chunkChars / 2) {
                    end = lastBreak;
                }
            }

            result.add(text.substring(start, end).trim());

            if (end >= text.length()) {
                break;  // 已经到末尾，结束
            }

            start = end - overlapChars;
            if (start >= end) {
                start = end; // 防止死循环
            }
        }
        return result;
    }

    /**
     * 普通文本：按句子边界切分
     */
    private List<String> splitBySentences(String text) {
        int targetChunkSize = astraProperties.getParser().getChunk().getTargetSize();
        List<String> result = new ArrayList<>();
        // 按中文/英文句子结束标点切分
        String[] rawSentences = text.split("(?<=[。！？.?!])\\s+");

        StringBuilder current = new StringBuilder();
        int currentTokens = 0;

        for (String sentence : rawSentences) {
            sentence = sentence.trim();
            if (sentence.isBlank()) {
                continue;
            }

            // 过滤掉小数点误切产生的数字碎片（如 "5"）
            if (sentence.length() <= 2 && sentence.matches("\\d+")) {
                continue;
            }

            int sentTokens = estimateTokenSize(sentence);

            // 累积超过目标大小，保存当前块
            if (currentTokens > 0 && currentTokens + sentTokens > targetChunkSize) {
                result.add(current.toString().trim());

                String overlap = extractOverlap(current.toString());
                current.setLength(0);
                currentTokens = 0;
                if (!overlap.isEmpty()) {
                    current.append(overlap);
                    currentTokens = estimateTokenSize(overlap);
                }
            }

            if (!current.isEmpty()) {
                current.append(" ");
            }
            current.append(sentence);
            currentTokens += sentTokens;
        }

        if (!current.isEmpty()) {
            result.add(current.toString().trim());
        }
        return result;
    }

    /**
     * 提取字符串末尾指定比例的内容作为重叠
     * 保证：1) 重叠量不低于预期的 80%；2) 尽量在句子边界截断
     */
    private String extractOverlap(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        double overlapRatio = astraProperties.getParser().getChunk().getOverlapRatio();
        int totalTokens = estimateTokenSize(text);
        int overlapTokens = (int) (totalTokens * overlapRatio);
        if (overlapTokens <= 0) {
            return "";
        }

        // 反推字符数范围（中文混合文本平均 1.5 字符/token）
        int maxChars = Math.min((int) (overlapTokens * 2.0), text.length());
        int minChars = Math.min((int) (overlapTokens * 0.8), text.length());

        int searchEnd = text.length() - minChars;

        // 从末尾往前找句子边界，但保证重叠量 >= minChars
        for (int i = text.length() - 1; i >= 0 && i >= text.length() - maxChars; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '.' || c == '?' || c == '!' || c == '\n') {
                if (i <= searchEnd) {
                    return text.substring(i + 1).trim();
                }
            }
        }

        // 找不到句子边界，硬截
        return text.substring(Math.max(0, text.length() - maxChars)).trim();
    }

    /**
     * 保守的 token 估算
     * - CJK 字符（含中文标点、全角）：每个算 1 token（保守，实际约 0.6~0.8）
     * - 英文/数字：每 3 字符算 1 token
     * - 空格/换行：每 4 字符算 1 token
     * - 其他符号（标点等）：每个算 1 token
     */
    private int estimateTokenSize(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int cjkCount = 0;
        int asciiLetterDigit = 0;
        int whitespace = 0;
        int other = 0;

        for (char c : text.toCharArray()) {
            if (isCjkChar(c)) {
                cjkCount++;
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                asciiLetterDigit++;
            } else if (Character.isWhitespace(c)) {
                whitespace++;
            } else {
                other++;
            }
        }

        return cjkCount + other
                + (asciiLetterDigit + 2) / 3
                + (whitespace + 3) / 4;
    }

    /**
     * 判断是否为 CJK 字符（含中文、日文、韩文、中文标点、全角符号）
     */
    private boolean isCjkChar(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
                || block == Character.UnicodeBlock.GENERAL_PUNCTUATION
                || block == Character.UnicodeBlock.ENCLOSED_CJK_LETTERS_AND_MONTHS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY;
    }

    /**
     * 安全添加 chunk（避免空内容入库）
     */
    private void addChunk(List<String> chunks, String content) {
        if (content != null && !content.isBlank()) {
            chunks.add(content);
        }
    }
}