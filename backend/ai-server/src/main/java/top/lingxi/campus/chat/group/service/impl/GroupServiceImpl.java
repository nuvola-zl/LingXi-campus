package top.lingxi.campus.chat.group.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.lingxi.campus.common.constant.CacheConstants;
import top.lingxi.campus.common.context.BaseContext;
import top.lingxi.campus.domain.ai.dto.GroupDTO;
import top.lingxi.campus.domain.ai.entity.Group;
import top.lingxi.campus.domain.ai.mapper.GroupMapper;
import top.lingxi.campus.chat.group.service.IGroupService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import top.lingxi.campus.infra.cache.CacheUtil;

@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements IGroupService {
    private final GroupMapper groupMapper;
    private final CacheUtil cacheUtil;

    @Override
    public void addGroup(GroupDTO groupDTO) {
        // 设置sort默认值
        if(groupDTO.getSort() == null) {
            groupDTO.setSort(0);
        }
        Group group = Group.builder()
                .userId(BaseContext.getCurrentId())
                .name(groupDTO.getName())
                .sort(groupDTO.getSort())
                .status(true)
                .createdAt(LocalDateTime.now())
                .build();
        groupMapper.insert(group);

        // 清除缓存
        cacheUtil.deleteWithUserId(CacheConstants.CAFFEINE_GROUP_LIST,
                CacheConstants.GROUP_LIST_KEY_PREFIX, BaseContext.getCurrentId());
    }

    @Override
    public List<Group> queryGroup() {
        Long currentId = BaseContext.getCurrentId();
        String redisKey = CacheConstants.getGroupListKey(currentId);

        return cacheUtil.queryWithPassThrough(
                CacheConstants.CAFFEINE_GROUP_LIST,
                redisKey,
                new com.fasterxml.jackson.core.type.TypeReference<List<Group>>() {},
                () -> queryGroupFromDB(currentId),
                CacheConstants.BASE_TTL_HOURS,
                TimeUnit.HOURS
        );
    }

    private List<Group> queryGroupFromDB(Long userId) {
        return groupMapper.selectList(
                new LambdaQueryWrapper<Group>()
                        .eq(Group::getUserId, userId)
                        .orderByDesc(Group::getSort)
        );
    }

    @Override
    public void deleteGroup(Long id) {
        // 权限校验
        Group group = groupMapper.selectById(id);
        if (group == null || !BaseContext.getCurrentId().equals(group.getUserId())) {
            throw new RuntimeException("无权限删除该分组");
        }

        groupMapper.deleteById(id);

        // 清除缓存
        if (group != null) {
            cacheUtil.deleteWithUserId(CacheConstants.CAFFEINE_GROUP_LIST,
                    CacheConstants.GROUP_LIST_KEY_PREFIX, BaseContext.getCurrentId());
        }
    }

    @Override
    public void updateGroup(Long id, GroupDTO groupDTO) {
        // 权限校验
        Group group = groupMapper.selectById(id);
        if (group == null || !BaseContext.getCurrentId().equals(group.getUserId())) {
            throw new RuntimeException("无权限修改该分组");
        }

        LambdaUpdateWrapper<Group> updateWrapper = new LambdaUpdateWrapper<Group>()
                .eq(Group::getId, id)
                .set(Group::getName, groupDTO.getName())
                .set(Group::getSort, groupDTO.getSort());
        
        groupMapper.update(updateWrapper);

        // 清除缓存
        cacheUtil.deleteWithUserId(CacheConstants.CAFFEINE_GROUP_LIST,
                    CacheConstants.GROUP_LIST_KEY_PREFIX, BaseContext.getCurrentId());
    }
}
