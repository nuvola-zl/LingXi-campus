package top.lingxi.campus.itAgent.ticket.engineer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.lingxi.campus.itAgent.ticket.engineer.service.ITicketEngineerService;
import top.lingxi.campus.domain.biz.ticket.entity.BizTicket;
import top.lingxi.campus.domain.biz.ticket.entity.BizTicketCategory;
import top.lingxi.campus.domain.biz.ticket.entity.BizTicketComment;
import top.lingxi.campus.domain.biz.ticket.mapper.BizTicketCategoryMapper;
import top.lingxi.campus.domain.biz.ticket.mapper.BizTicketCommentMapper;
import top.lingxi.campus.domain.biz.ticket.mapper.BizTicketMapper;
import top.lingxi.campus.domain.engineer.mapper.BizEngineerGroupMemberMapper;
import top.lingxi.campus.domain.engineer.po.BizEngineerGroupMember;
import top.lingxi.campus.domain.engineer.vo.EngineerTicketVO;
import top.lingxi.campus.domain.engineer.vo.TicketCommentVO;
import top.lingxi.campus.domain.user.mapper.SysUserMapper;
import top.lingxi.campus.domain.user.po.SysUser;


import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketEngineerServiceImpl implements ITicketEngineerService {

    private final BizTicketMapper ticketMapper;
    private final BizTicketCommentMapper commentMapper;
    private final BizTicketCategoryMapper categoryMapper;
    private final SysUserMapper sysUserMapper;
    private final BizEngineerGroupMemberMapper groupMemberMapper;  // 新增注入
    private final RedissonClient redissonClient;


    /**
     * 查工程师所在组（通过 biz_engineer_group_member 表）
     * 如果用户没有 BizEngineerGroupMemberMapper，这里用原生 SQL 替代
     */
    // 替换原来的 getMyGroupIds 方法
    private List<Long> getMyGroupIds(Long userId) {
        return groupMemberMapper.selectObjs(
                new LambdaQueryWrapper<BizEngineerGroupMember>()
                        .select(BizEngineerGroupMember::getGroupId)
                        .eq(BizEngineerGroupMember::getUserId, userId)
        ).stream().map(o -> (Long) o).collect(Collectors.toList());
    }

    @Override
    public List<EngineerTicketVO> getPendingList(Long engineerUserId) {
        // 查工程师所在的所有组
        List<Long> myGroupIds = getMyGroupIds(engineerUserId);
        
        LambdaQueryWrapper<BizTicket> wrapper = new LambdaQueryWrapper<>();
        // 状态：待处理(1) 或 处理中(2)
        wrapper.in(BizTicket::getStatus, List.of(1, 2));
        
        // 条件：分配到我所在的组 或 已分配给我
        wrapper.and(w -> {
            if (!myGroupIds.isEmpty()) {
                w.in(BizTicket::getGroupId, myGroupIds);
            }
            w.or().eq(BizTicket::getAssigneeId, engineerUserId);
        });
        
        // 紧急优先，然后按创建时间正序（先来的先处理）
        wrapper.orderByAsc(BizTicket::getPriority);
        wrapper.orderByAsc(BizTicket::getCreatedAt);
        
        List<BizTicket> tickets = ticketMapper.selectList(wrapper);
        return tickets.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    public List<EngineerTicketVO> getHistoryList(Long engineerUserId, Integer page, Integer pageSize) {
        LambdaQueryWrapper<BizTicket> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizTicket::getAssigneeId, engineerUserId);
        wrapper.eq(BizTicket::getStatus, 4); // 已关闭
        wrapper.orderByDesc(BizTicket::getClosedAt);
        
        if (page != null && pageSize != null && page > 0 && pageSize > 0) {
            int offset = (page - 1) * pageSize;
            wrapper.last("LIMIT " + pageSize + " OFFSET " + offset);
        }
        
        List<BizTicket> tickets = ticketMapper.selectList(wrapper);
        return tickets.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    public EngineerTicketVO getDetail(Long ticketId) {
        BizTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new RuntimeException("工单不存在");
        }
        
        EngineerTicketVO vo = convertToVO(ticket);
        
        // 查询处理时间线
        LambdaQueryWrapper<BizTicketComment> cw = new LambdaQueryWrapper<>();
        cw.eq(BizTicketComment::getTicketId, ticketId);
        cw.orderByAsc(BizTicketComment::getCreatedAt);
        List<BizTicketComment> comments = commentMapper.selectList(cw);
        
        vo.setComments(comments.stream().map(c -> {
            TicketCommentVO cv = new TicketCommentVO();
            cv.setId(c.getId());
            cv.setContent(c.getContent());
            cv.setType(c.getType());
            cv.setCreatedAt(c.getCreatedAt());
            
            if ("SYSTEM".equals(c.getType())) {
                cv.setActor("系统");
            } else {
                SysUser u = sysUserMapper.selectById(c.getUserId());
                cv.setActor(u != null ? u.getRealName() : "未知");
            }
            return cv;
        }).collect(Collectors.toList()));
        
        return vo;
    }

    @Override
    public void claim(Long ticketId, Long engineerUserId) {
        // 1. 加分布式锁，key = "ticket:claim:1"
        String lockKey = "ticket:claim:" + ticketId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试加锁，最多等 3 秒，锁持有 10 秒（看门狗会自动续期）
            boolean locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                throw new RuntimeException("系统繁忙，请稍后重试");
            }

            // 2. 执行业务逻辑（此时只有一个线程能进来）
            BizTicket ticket = ticketMapper.selectById(ticketId);
            if (ticket == null) {
                throw new RuntimeException("工单不存在");
            }
            if (ticket.getStatus() != 1) {
                throw new RuntimeException("工单已被接单或处理中");
            }

            // 校验工程师是否属于该组
            List<Long> myGroupIds = getMyGroupIds(engineerUserId);
            if (!myGroupIds.contains(ticket.getGroupId())) {
                throw new RuntimeException("您无权处理该工单");
            }

            // 3. 更新工单
            ticket.setAssigneeId(engineerUserId);
            ticket.setStatus(2);
            ticket.setUpdatedAt(LocalDateTime.now());
            ticketMapper.updateById(ticket);

            // 4. 添加系统记录
            SysUser engineer = sysUserMapper.selectById(engineerUserId);
            String name = engineer != null ? engineer.getRealName() : "工程师";
            addSystemComment(ticketId, name + " 已接单，开始处理");

            log.info("工程师接单成功: ticketId={}, engineerId={}", ticketId, engineerUserId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("接单被中断");
        } finally {
            // 5. 释放锁（必须放在 finally）
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional
    public void addComment(Long ticketId, Long engineerUserId, String content) {
        BizTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new RuntimeException("工单不存在");
        }
        if (!engineerUserId.equals(ticket.getAssigneeId())) {
            throw new RuntimeException("您不是该工单的处理人");
        }
        
        BizTicketComment comment = new BizTicketComment();
        comment.setTicketId(ticketId);
        comment.setUserId(engineerUserId);
        comment.setContent(content);
        comment.setType("COMMENT");
        comment.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(comment);
        
        // 更新工单活跃时间
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketMapper.updateById(ticket);
        
        log.info("工程师添加处理记录: ticketId={}, engineerId={}", ticketId, engineerUserId);
    }

    @Override
    @Transactional
    public void resolve(Long ticketId, Long engineerUserId) {
        BizTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new RuntimeException("工单不存在");
        }
        if (!engineerUserId.equals(ticket.getAssigneeId())) {
            throw new RuntimeException("您不是该工单的处理人");
        }
        if (ticket.getStatus() != 2) {
            throw new RuntimeException("工单不在处理中状态");
        }
        
        ticket.setStatus(3); // 待确认
        ticket.setResolvedAt(LocalDateTime.now());
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketMapper.updateById(ticket);
        
        addSystemComment(ticketId, "工程师已标记解决，等待用户确认");
        
        log.info("工程师标记解决: ticketId={}, engineerId={}", ticketId, engineerUserId);
    }

    private void addSystemComment(Long ticketId, String content) {
        BizTicketComment comment = new BizTicketComment();
        comment.setTicketId(ticketId);
        comment.setUserId(0L);
        comment.setContent(content);
        comment.setType("SYSTEM");
        comment.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(comment);
    }

    private EngineerTicketVO convertToVO(BizTicket t) {
        EngineerTicketVO vo = new EngineerTicketVO();
        vo.setId(t.getId());
        vo.setTicketNo(t.getTicketNo());
        vo.setTitle(t.getTitle());
        vo.setDescription(t.getDescription());
        vo.setPriority(t.getPriority());
        vo.setPriorityLabel(mapPriority(t.getPriority()));
        vo.setStatus(t.getStatus());
        vo.setStatusLabel(mapStatus(t.getStatus()));
        vo.setRequesterId(t.getRequesterId());
        vo.setAssigneeId(t.getAssigneeId());
        vo.setGroupId(t.getGroupId());
        vo.setCategoryId(t.getCategoryId());
        vo.setCreatedAt(t.getCreatedAt());
        vo.setLastActiveAt(t.getUpdatedAt());
        
        // 查用户名
        SysUser requester = sysUserMapper.selectById(t.getRequesterId());
        vo.setRequesterName(requester != null ? requester.getRealName() : "未知");
        
        if (t.getAssigneeId() != null) {
            SysUser assignee = sysUserMapper.selectById(t.getAssigneeId());
            vo.setAssigneeName(assignee != null ? assignee.getRealName() : "未分配");
        } else {
            vo.setAssigneeName("未分配");
        }
        
        // 查分类名
        if (t.getCategoryId() != null) {
            BizTicketCategory category = categoryMapper.selectById(t.getCategoryId());
            vo.setCategoryName(category != null ? category.getName() : "未知");
        }
        
        return vo;
    }

    private String mapPriority(Integer p) {
        return switch (p) {
            case 1 -> "紧急";
            case 2 -> "普通";
            case 3 -> "低";
            default -> "普通";
        };
    }

    private String mapStatus(Integer s) {
        return switch (s) {
            case 1 -> "待处理";
            case 2 -> "处理中";
            case 3 -> "待确认";
            case 4 -> "已关闭";
            default -> "未知";
        };
    }
}