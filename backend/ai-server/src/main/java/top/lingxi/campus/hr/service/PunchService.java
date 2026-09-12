package top.lingxi.campus.hr.service;

import top.lingxi.campus.domain.hr.eneity.HrPunchRecord;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 打卡补卡服务接口
 * <p>
 * 提供忘打卡/迟到补卡申请的全流程管理，包括：
 * <ul>
 *   <li>提交补卡申请（支持 AI 自动审批与人工审批两种模式）</li>
 *   <li>管理员人工审核补卡申请</li>
 *   <li>查询用户补卡记录</li>
 * </ul>
 * </p>
 *
 * <p>补卡状态说明：</p>
 * <ul>
 *   <li>1 = 待处理（需人工审核）</li>
 *   <li>2 = 已通过（AI 自动通过或人工审核通过）</li>
 *   <li>3 = 已拒绝（人工审核拒绝）</li>
 * </ul>
 *
 * <p>审核方式说明：</p>
 * <ul>
 *   <li>isManualReview = 0：AI 自动审批（无需人工介入）</li>
 *   <li>isManualReview = 1：需人工审核</li>
 * </ul>
 *
 * @author hazeaihub
 * @version 1.0
 */
public interface PunchService {

    /**
     * 提交忘打卡/迟到补卡申请
     * <p>
     * 根据用户本月同类型补卡次数自动匹配规则，判断是否需要人工审核：
     * <ul>
     *   <li>无需审核：状态直接置为"已通过"（2），AI 自动处理</li>
     *   <li>需要审核：状态置为"待处理"（1），等待管理员人工审核</li>
     * </ul>
     * 提交后自动生成唯一补卡单号。
     * </p>
     *
     * @param userId    申请人用户 ID，不可为空
     * @param punchType 补卡类型，如 "forget"（忘打卡）、"late"（迟到）等
     * @param punchDate 补卡日期，不可为空
     * @param reason    补卡原因/备注说明
     * @return 提交成功的补卡记录实体，包含生成的单号、审核方式及当前状态
     */
    @Transactional
    HrPunchRecord submitPunch(Long userId, String punchType, LocalDate punchDate, String reason);

    /**
     * 管理员人工审核补卡申请
     * <p>
     * 仅对标记为需要人工审核（isManualReview = 1）且状态为"待处理"（status = 1）的申请进行处理。
     * 审核通过后状态变为"已通过"（2），审核拒绝则状态变为"已拒绝"（3）。
     * 无论通过与否，都会记录审批日志。
     * </p>
     *
     * @param punchId   补卡记录 ID，对应数据库主键
     * @param reviewerId 审核人用户 ID（管理员）
     * @param approved   审核结果：true 表示通过，false 表示拒绝
     * @param remark     审核意见/备注说明
     * @throws RuntimeException 若申请不存在、无需审核或已处理
     */
    @Transactional
    void manualReview(Long punchId, Long reviewerId, boolean approved, String remark);

    /**
     * 查询指定用户的补卡记录列表
     * <p>
     * 返回该用户的所有补卡申请记录，按创建时间降序排列（最新的排在最前面）。
     * </p>
     *
     * @param userId 用户 ID
     * @return 该用户的补卡记录列表，若该用户无记录则返回空列表
     */
    List<HrPunchRecord> listByUser(Long userId);
}