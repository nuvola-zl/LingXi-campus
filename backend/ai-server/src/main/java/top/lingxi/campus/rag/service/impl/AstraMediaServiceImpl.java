package top.lingxi.campus.rag.service.impl;

import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import top.lingxi.campus.domain.ai.entity.KbLibrary;
import top.lingxi.campus.domain.ai.entity.KbMedia;
import top.lingxi.campus.domain.ai.entity.MediaStatus;
import top.lingxi.campus.common.exception.ErrorCode;
import top.lingxi.campus.common.exception.BusinessException;
import top.lingxi.campus.domain.ai.dto.ParseMessage;
import top.lingxi.campus.domain.ai.mapper.KbChunkMapper;
import top.lingxi.campus.domain.ai.mapper.KbLibraryMapper;
import top.lingxi.campus.domain.ai.mapper.KbMediaMapper;
import top.lingxi.campus.rag.service.IAstraMediaService;
import top.lingxi.campus.rag.producter.RedissonStreamProducer;
import top.lingxi.campus.infra.config.AstraProperties;
import top.lingxi.campus.infra.oss.AliOssUtil;
import top.lingxi.campus.common.utils.Sha256Util;
import top.lingxi.campus.domain.ai.dto.MediaResponse;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 媒体文件服务实现
 *
 * Phase 2 变更：
 * 1. 修复 DOCX 映射：docx MIME 原映射 "WORD"，与解析器 FILE_TYPE "DOCX"
 *    不一致，导致 Word 文件永远匹配不到解析器（重试后进死信）。现统一为 "DOCX"
 * 2. 修复 MD 识别：text/plain 原统一映射 "TXT"，.md 文件永远走不到 Markdown
 *    解析器。现按文件名后缀识别 .md → "MD"
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AstraMediaServiceImpl implements IAstraMediaService {

    private final KbMediaMapper mediaMapper;
    private final KbLibraryMapper libraryMapper;
    private final KbChunkMapper chunkMapper;
    private final AliOssUtil aliOssUtil;
    private final RedissonStreamProducer streamProducer;
    private final AstraProperties astraProperties;

    @Override
    @Transactional
    public MediaResponse uploadFile(Long libraryId, Long userId, MultipartFile file) {
        log.info("上传文件: libraryId={}, userId={}, fileName={}",
                libraryId, userId, file.getOriginalFilename());

        // 校验知识库存在和权限
        KbLibrary library = libraryMapper.selectById(libraryId);
        if (library == null) {
            throw new BusinessException(ErrorCode.ASTRA_LIBRARY_NOT_FOUND);
        }
        if (!library.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限上传到此知识库");
        }

        AstraProperties.Upload uploadConfig = astraProperties.getUpload();

        // 校验文件类型（配置驱动白名单）
        String mimeType = file.getContentType();
        if (mimeType == null || !uploadConfig.getAllowedTypes().contains(mimeType)) {
            throw new BusinessException(ErrorCode.ASTRA_UNSUPPORTED_FILE_TYPE,
                    "不支持的文件类型: " + mimeType);
        }

        // 校验文件大小（配置驱动，单位 MB）
        long fileSize = file.getSize();
        long maxFileSizeBytes = uploadConfig.getMaxFileSize() * 1024L * 1024L;
        if (fileSize > maxFileSizeBytes) {
            throw new BusinessException(ErrorCode.ASTRA_FILE_SIZE_EXCEEDED,
                    "文件大小超出限制，最大" + uploadConfig.getMaxFileSize() + "MB");
        }

        // 计算 SHA256
        String sha256;
        try {
            sha256 = Sha256Util.calculate(file.getInputStream());
        } catch (Exception e) {
            log.error("计算SHA256失败", e);
            throw new BusinessException(ErrorCode.ASTRA_SHA256_MISMATCH, "文件校验失败");
        }

        // 检查重复文件（数据库另有 uk_media_sha256_library 唯一约束兜底）
        Optional<KbMedia> existing = mediaMapper.findByLibraryIdAndSha256(libraryId, sha256);
        if (existing.isPresent()) {
            log.info("文件已存在: sha256={}", sha256);
            return toResponse(existing.get());
        }

        // 1.【先上传】文件到 OSS，拿到真实存储路径
        String dirPrefix = "astra/" + libraryId;
        String uploadUrl;
        try {
            uploadUrl = aliOssUtil.upload(file.getBytes(), dirPrefix, Objects.requireNonNull(file.getOriginalFilename()));
        } catch (IOException e) {
            log.error("上传文件到OSS失败", e);
            throw new BusinessException(ErrorCode.ASTRA_UPLOAD_FAILED, "文件上传失败");
        }

        // 2.【再入库】此时 storagePath 已经有值了
        KbMedia media = KbMedia.builder()
                .libraryId(libraryId)
                .fileName(file.getOriginalFilename())
                .mimeType(mimeType)
                .fileSize(fileSize)
                .storagePath(uploadUrl)   // ← 用 OSS 返回的真实 URL
                .sha256(sha256)
                .status(MediaStatus.PENDING.getCode())
                .totalChunks(0)
                .parsedChunks(0)
                .build();

        mediaMapper.insert(media);
        log.info("媒体记录创建成功: id={}", media.getId());

        // 3. 发送解析任务
        String fileType = getFileType(mimeType, file.getOriginalFilename());
        ParseMessage parseMessage = ParseMessage.of(
                media.getId(),
                libraryId,
                fileType,
                media.getStoragePath()
        );
        streamProducer.sendParseTask(parseMessage);

        return toResponse(media);
    }

    @Override
    public List<MediaResponse> listMedia(Long libraryId, Long userId, String status, Integer page, Integer size) {
        // 校验权限
        KbLibrary library = libraryMapper.selectById(libraryId);
        if (library == null) {
            throw new BusinessException(ErrorCode.ASTRA_LIBRARY_NOT_FOUND);
        }
        if (!library.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限访问此知识库");
        }

        List<KbMedia> mediaList = mediaMapper.listByLibraryId(libraryId, status);

        return mediaList.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public MediaResponse getMedia(Long mediaId, Long userId) {
        KbMedia media = mediaMapper.findById(mediaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASTRA_MEDIA_NOT_FOUND));

        // 校验权限
        KbLibrary library = libraryMapper.selectById(media.getLibraryId());
        if (library == null || !library.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限访问此文件");
        }

        return toResponse(media);
    }

    @Override
    public MediaResponse getMediaStatus(Long mediaId, Long userId) {
        // 状态查询与详情一致，直接复用
        return getMedia(mediaId, userId);
    }

    @Override
    @Transactional
    public void deleteMedia(Long mediaId, Long userId) {
        KbMedia media = mediaMapper.findById(mediaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASTRA_MEDIA_NOT_FOUND));

        // 校验权限
        KbLibrary library = libraryMapper.selectById(media.getLibraryId());
        if (library == null || !library.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限删除此文件");
        }

        // 删除 OSS 文件 - 从完整 URL 中提取 objectKey
        String storagePath = media.getStoragePath();
        if (storagePath != null) {
            String objectKey = aliOssUtil.extractObjectKey(storagePath);
            aliOssUtil.delete(objectKey);
        }

        // 级联删除向量分片（kb_chunk 无外键，不删会留下孤儿 chunk）
        chunkMapper.deleteByMediaId(mediaId);

        // 删除媒体记录
        mediaMapper.deleteById(mediaId);
        log.info("媒体文件删除成功: id={}", mediaId);
    }

    @Override
    public Long checkDuplicate(Long libraryId, String sha256) {
        return mediaMapper.findByLibraryIdAndSha256(libraryId, sha256)
                .map(KbMedia::getId)
                .orElse(null);
    }

    private MediaResponse toResponse(KbMedia media) {
        return BeanUtil.copyProperties(media, MediaResponse.class);
    }

    /**
     * 将 MIME 类型映射为文件类型标识
     *
     * 注意：返回值必须与提取器/解析器的 FILE_TYPE 严格一致：
     * - docx → "DOCX"（原 "WORD" 与 WordFileParser.FILE_TYPE 不匹配，已修复）
     * - text/plain 且文件名以 .md 结尾 → "MD"（原统一 "TXT"，MD 永远匹配不到，已修复）
     */
    private String getFileType(String mimeType, String originalFileName) {
        if (mimeType == null) {
            return "UNKNOWN";
        }
        return switch (mimeType) {
            case "text/plain" -> isMarkdownFile(originalFileName) ? "MD" : "TXT";
            case "application/pdf" -> "PDF";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "DOCX";
            default -> "UNKNOWN";
        };
    }

    private boolean isMarkdownFile(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".md");
    }
}