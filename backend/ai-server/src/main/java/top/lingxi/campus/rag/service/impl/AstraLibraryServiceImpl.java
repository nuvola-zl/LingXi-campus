package top.lingxi.campus.rag.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.lingxi.campus.domain.ai.dto.LibraryCreateRequest;
import top.lingxi.campus.domain.ai.dto.LibraryUpdateRequest;
import top.lingxi.campus.domain.ai.entity.KbLibrary;
import top.lingxi.campus.domain.ai.entity.KbMedia;
import top.lingxi.campus.common.exception.BusinessException;
import top.lingxi.campus.common.exception.ErrorCode;
import top.lingxi.campus.domain.ai.mapper.KbChunkMapper;
import top.lingxi.campus.domain.ai.mapper.KbLibraryMapper;
import top.lingxi.campus.domain.ai.mapper.KbMediaMapper;
import top.lingxi.campus.rag.service.IAstraLibraryService;
import top.lingxi.campus.domain.ai.dto.LibraryResponse;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库服务实现
 *
 * 重构变更：移除知识库列表的 Caffeine+Redis 两级缓存。
 * 原因：1) 列表查询是毫秒级，缓存收益趋近于零；
 *      2) 直改数据库后缓存造成数据不一致（本次排障亲身体验）；
 *      3) 原缓存 key 不含 keyword 参数，搜索结果被串扰（隐藏 bug）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AstraLibraryServiceImpl implements IAstraLibraryService {

    private final KbLibraryMapper libraryMapper;
    private final KbMediaMapper mediaMapper;
    private final KbChunkMapper chunkMapper;

    @Override
    @Transactional
    public LibraryResponse createLibrary(Long userId, LibraryCreateRequest request) {

        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "登录后才能创建知识库");
        }
        // 校验知识库类型（缺省默认为 personal；大小写不敏感，统一归一为小写存储）
        String type = (request.getType() == null || request.getType().isBlank())
                ? "personal"
                : request.getType().trim().toLowerCase();
        if (!"personal".equals(type) && !"team".equals(type)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "知识库类型必须为 personal 或 team");
        }

        KbLibrary library = KbLibrary.builder()
                .name(request.getName())
                .description(request.getDescription())
                .type(type)
                .ownerId(userId)
                .isTop(false)
                .coverImage(request.getCoverImage())
                .build();

        libraryMapper.insert(library);
        log.info("知识库创建成功: id={}", library.getId());

        return toResponse(library, 0L, 0L);
    }

    @Override
    public List<LibraryResponse> listLibraries(Long userId, String keyword, Integer page, Integer size) {
        log.debug("查询知识库列表: userId={}, keyword={}", userId, keyword);
        List<KbLibrary> libraries = libraryMapper.listByOwnerWithStats(userId, keyword);
        return libraries.stream()
                .map(lib -> toResponse(lib, getMediaCount(lib.getId()), getChunkCount(lib.getId())))
                .collect(Collectors.toList());
    }

    @Override
    public LibraryResponse getLibrary(Long libraryId, Long userId) {
        log.debug("获取知识库详情: libraryId={}, userId={}", libraryId, userId);

        KbLibrary library = libraryMapper.selectById(libraryId);
        if (library == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "知识库不存在");
        }

        // 权限：personal 库仅所有者可见；team 库所有人可见
        if ("personal".equals(library.getType()) && !library.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限访问此知识库");
        }

        return toResponse(library, getMediaCount(libraryId), getChunkCount(libraryId));
    }

    @Override
    @Transactional
    public LibraryResponse updateLibrary(Long libraryId, Long userId, LibraryUpdateRequest request) {
        log.info("更新知识库: libraryId={}, userId={}", libraryId, userId);

        KbLibrary library = libraryMapper.selectById(libraryId);
        if (library == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "知识库不存在");
        }

        if (!library.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限修改此知识库");
        }

        if (request.getName() != null) {
            library.setName(request.getName());
        }
        if (request.getDescription() != null) {
            library.setDescription(request.getDescription());
        }
        if (request.getCoverImage() != null) {
            library.setCoverImage(request.getCoverImage());
        }

        libraryMapper.updateById(library);
        return toResponse(library, getMediaCount(libraryId), getChunkCount(libraryId));
    }

    @Override
    @Transactional
    public void deleteLibrary(Long libraryId, Long userId) {
        log.info("删除知识库: libraryId={}, userId={}", libraryId, userId);
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "用户未登录");
        }

        KbLibrary library = libraryMapper.selectById(libraryId);
        if (library == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "知识库不存在");
        }

        if (!library.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限删除此知识库");
        }

        // 硬删除 - MyBatis-Plus 默认物理删除
        // 级联删除由数据库外键约束处理(kb_chunk, kb_media)
        libraryMapper.deleteById(libraryId);
        log.info("知识库删除成功: id={}", libraryId);
    }

    @Override
    @Transactional
    public LibraryResponse toggleTop(Long libraryId, Long userId) {
        log.info("切换置顶状态: libraryId={}, userId={}", libraryId, userId);
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "用户未登录");
        }
        KbLibrary library = libraryMapper.selectById(libraryId);
        if (library == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "知识库不存在");
        }

        if (!library.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限修改此知识库");
        }

        library.setIsTop(!library.getIsTop());
        libraryMapper.updateById(library);

        return toResponse(library, getMediaCount(libraryId), getChunkCount(libraryId));
    }

    @Override
    public boolean hasAccess(Long libraryId, Long userId) {
        KbLibrary library = libraryMapper.selectById(libraryId);
        if (library == null) {
            return false;
        }
        return library.getOwnerId().equals(userId);
    }

    private LibraryResponse toResponse(KbLibrary library, Long mediaCount, Long chunkCount) {
        return LibraryResponse.builder()
                .id(library.getId())
                .name(library.getName())
                .description(library.getDescription())
                .type(library.getType())
                .ownerId(library.getOwnerId())
                .isTop(library.getIsTop())
                .coverImage(library.getCoverImage())
                .mediaCount(mediaCount)
                .chunkCount(chunkCount)
                .createdAt(library.getCreatedAt())
                .updatedAt(library.getUpdatedAt())
                .build();
    }

    private Long getMediaCount(Long libraryId) {
        return mediaMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KbMedia>()
                        .eq(KbMedia::getLibraryId, libraryId)
                        .eq(KbMedia::getStatus, "PARSED")
        ).longValue();
    }

    private Long getChunkCount(Long libraryId) {
        return chunkMapper.countByLibraryId(libraryId);
    }
}