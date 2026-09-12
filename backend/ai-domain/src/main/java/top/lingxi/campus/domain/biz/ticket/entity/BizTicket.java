package top.lingxi.campus.domain.biz.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单实体
 * 对应数据库表: biz_ticket
 * 企业内部服务台的核心业务表，记录员工提交的各类服务请求
 */
@Data
@TableName("biz_ticket")
public class BizTicket {

    /**
     * 工单ID，主键自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 工单编号，全局唯一，用于展示和查询
     * 格式示例：IT-20240715-001
     */
    private String ticketNo;

    /**
     * 工单标题，简短描述问题
     * 示例：邮箱无法登录、VPN申请、请假审批
     */
    private String title;

    /**
     * 工单详细描述，记录问题现象、用户补充信息等
     * AI自动建单时，由对话内容总结生成
     */
    private String description;

    /**
     * 分类ID，关联 biz_ticket_category
     * 决定工单属于 IT / HR / 行政 / 财务哪个领域
     */
    private Long categoryId;

    /**
     * 紧急度：1-紧急，2-普通，3-低
     * 员工说"急用"时，AI自动标记为1
     */
    private Integer priority;

    /**
     * 工单状态：1待处理 2处理中 3待确认 4已关闭
     * 状态流转：待处理 → 处理中 → 待确认 → 已关闭
     */
    private Integer status;

    /**
     * 来源：AI（AI自动创建）、WEB（网页人工创建）、ADMIN（后台创建）
     * 用于区分工单创建渠道
     */
    private String source;

    /**
     * 提单人ID，关联 sys_user
     * 员工查询工单时，只能查到自己 requester_id 的记录
     */
    private Long requesterId;

    /**
     * 处理人ID，关联 sys_user
     * 工程师接单后，该字段被更新为工程师的 userId
     */
    private Long assigneeId;

    /**
     * 分配到的工程师组ID，关联 biz_engineer_group
     * 根据分类自动匹配，如 IT问题分配到 IT运维组
     */
    private Long groupId;

    /**
     * 创建时间，工单生成时自动写入
     */
    private LocalDateTime createdAt;

    /**
     * 最后更新时间，任何字段变更时自动刷新
     */
    private LocalDateTime updatedAt;

    /**
     * 解决时间，工程师处理完毕时写入
     */
    private LocalDateTime resolvedAt;

    /**
     * 关闭时间，用户确认解决或超时关闭时写入
     */
    private LocalDateTime closedAt;

    /**
     * 满意度评分，1-5分
     * 工单关闭后，AI询问用户评分
     */
    private Integer satisfaction;

    /**
     * 评价内容，用户对本次服务的文字反馈
     */
    private String feedback;

    /**
     * 关联的AI会话ID，关联 chat_session
     * 用于追溯该工单是由哪次AI对话创建的
     */
    private String aiSessionId;
}