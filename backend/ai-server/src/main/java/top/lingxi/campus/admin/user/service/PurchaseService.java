package top.lingxi.campus.admin.user.service;

import org.springframework.transaction.annotation.Transactional;

/**
 * 采购申请服务接口
 * <p>
 * 提供采购申请的审批管理，包括：
 * <ul>
 *   <li>审批采购申请（通过或拒绝）</li>
 *   <li>记录审批日志</li>
 * </ul>
 * </p>
 *
 * <p>采购单状态说明：</p>
 * <ul>
 *   <li>1 = 待审批</li>
 *   <li>2 = 已批准</li>
 *   <li>5 = 已取消（审批拒绝）</li>
 * </ul>
 *
 * @author hazeaihub
 * @version 1.0
 */
public interface PurchaseService {

    /**
     * 审批采购申请
     * <p>
     * 仅对状态为"待审批"（status = 1）的采购单进行处理。
     * 审批通过后状态变为"已批准"（2），审批拒绝则状态变为"已取消"（5）。
     * 无论通过与否，都会记录审批日志。
     * </p>
     *
     * @param purchaseId 采购单 ID，对应数据库主键
     * @param approved   审批结果：true 表示通过，false 表示拒绝
     * @param remark     审批意见/备注说明
     * @throws RuntimeException 若采购单不存在或已处理（非待审批状态）
     */
    @Transactional
    void approvePurchase(Long purchaseId, boolean approved, String remark);
}