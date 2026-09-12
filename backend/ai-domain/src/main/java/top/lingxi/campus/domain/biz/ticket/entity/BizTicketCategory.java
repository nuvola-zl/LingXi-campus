package top.lingxi.campus.domain.biz.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单分类实体
 * 对应数据库表: biz_ticket_category
 * 预置数据：IT运维、人力资源、行政服务、财务服务
 */
@Data
@TableName("biz_ticket_category")
public class BizTicketCategory {

    /**
     * 分类ID，主键自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 分类名称
     * 示例：IT运维、人力资源、行政服务、财务服务
     */
    private String name;

    /**
     * 分类编码，工单号生成用
     * 示例：it、hr、admin、finance
     */
    private String code;

    /**
     * 父分类ID，0表示顶级分类
     */
    private Long parentId;

    /**
     * 默认分配工程师组ID
     */
    private Long defaultGroupId;

    /**
     * SLA响应时效（分钟）
     * 示例：30分钟、60分钟
     */
    private Integer slaResponseMinutes;

    /**
     * SLA解决时效（分钟）
     * 示例：240分钟（4小时）、480分钟（8小时）
     */
    private Integer slaResolveMinutes;

    /**
     * 排序号
     */
    private Integer sortOrder;

    /**
     * 状态：1启用 0禁用
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}