package top.lingxi.campus.rag.file.service.impl;

import cn.hutool.core.lang.UUID;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import top.lingxi.campus.infra.oss.AliOssUtil;
import top.lingxi.campus.rag.parse.FileParser;
import top.lingxi.campus.rag.parse.FileParserFactory;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class FileChatService {

    private final AliOssUtil aliOssUtil;
    private final FileParserFactory parserFactory;

    // 直接注入的安全阈值：3000 个中文字符（约 1500 tokens）
    private static final int DIRECT_INJECT_MAX_CHARS = 3000;

    @Data
    public static class FileProcessResult {
        private final String ossUrl;
        private final String fileName;
        private final String extractedText;
        private final boolean tooLong;  // 是否超出直接注入阈值
    }

    public FileProcessResult process(MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename();
        String ext = getExtension(originalName).toUpperCase();

        // 上传 OSS
        String ossKey = "chat/temp/" + UUID.randomUUID() + "_" + originalName;
        String ossUrl = aliOssUtil.upload(file.getBytes(), ossKey);

        // 提取文本
        String text = null;
        FileParser parser = parserFactory.getParser(ext);
        if (parser != null) {
            text = parser.extractText(file.getBytes());
        }

        // 长度判断
        boolean tooLong = text != null && text.length() > DIRECT_INJECT_MAX_CHARS;

        return new FileProcessResult(ossUrl, originalName, text, tooLong);
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}
