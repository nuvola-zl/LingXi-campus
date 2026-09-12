package top.lingxi.campus.admin.engineer.service;

import java.util.List;
import java.util.Map;

/**
 * 审批任务服务接口
 * <p>
 * 聚合各类待审批任务，提供统一的审批任务查询入口。
 * 目前覆盖以下审批类型：
 * <ul>
 *   <li>请假申请（人工审批模式）</li>
 *   <li>补卡申请（需人工审核）</li>
 *   <li>采购申请</li>
 * </ul>
 * </p>
 *
 * <p>任务类型标识：</p>
 * <ul>
 *   <li>leave = 请假申请</li>
 *   <li>punch = 补卡申请</li>
 *   <li>purchase = 采购申请</li>
 * </ul>
 *
 * <p>各业务筛选条件：</p>
 * <ul>
 *   <li>请假：status = 1（待审批）且 approve_type = 2（人工审批）</li>
 *   <li>补卡：is_manual_review = 1（需人工审核）且 status = 1（待处理）</li>
 *   <li>采购：status = 1（待审批）</li>
 * </ul>
 *
 * @author hazeaihub
 * @version 1.0
 */
public interface ApprovalTaskService {

    /**
     * 获取所有待审批任务
     * <p>
     * 聚合查询请假、补卡、采购三类待审批记录，统一封装为任务列表返回。
     * 各类任务按创建时间降序排列。
     * </p>
     *
     * <p>每个任务以 Map 形式返回，包含以下字段：</p>
     * <ul>
     *   <li>type：任务类型（leave / punch / purchase）</li>
     *   <li>id：记录主键 ID</li>
     *   <li>no：业务单号</li>
     *   <li>userId：申请人用户 ID（请假/补卡有，采购无）</li>
     *   <li>content：任务内容摘要</li>
     *   <li>createdAt：创建时间</li>
     * </ul>
     *
     * @return 待审批任务列表，按各业务系统内的创建时间降序排列，若无任务则返回空列表
     */
    List<Map<String, Object>> getPendingTasks();
}