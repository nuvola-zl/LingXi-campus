package top.lingxi.campus.hr.service;

import top.lingxi.campus.domain.hr.eneity.HrLeaveRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 请假服务接口
 * <p>
 * 提供请假申请的全流程管理，包括：
 * <ul>
 *   <li>提交请假申请（支持 AI 自动审批与人工审批两种模式）</li>
 *   <li>人工审批处理</li>
 *   <li>查询用户历史请假记录</li>
 * </ul>
 * </p>
 *
 * @author hazeaihub
 * @version 1.0
 */
public interface LeaveService {

    /**
     * 提交请假申请
     * <p>
     * 根据请假类型和天数自动判断审批方式：
     * <ul>
     *   <li>年假 ≤ 2 天 或 事假 ≤ 1 天 → AI 自动审批（approveType = 1）</li>
     *   <li>其他情况 → 人工审批（approveType = 2）</li>
     * </ul>
     * 提交后自动生成唯一单号，状态置为"待审批"（status = 1）。
     * 若为 AI 审批模式，系统会自动完成审批并记录日志。
     * </p>
     *
     * @param userId    申请人用户 ID，不可为空
     * @param type      请假类型，如 "annual"（年假）、"personal"（事假）等
     * @param startDate 请假开始日期，不可为空
     * @param endDate   请假结束日期，不可为空
     * @param days      请假天数，精确到小数位（如 0.5 表示半天）
     * @param reason    请假原因/备注说明
     * @return 提交成功的请假申请实体，包含生成的单号、审批方式及当前状态
     */
    @Transactional
    HrLeaveRequest submitLeave(Long userId, String type, LocalDate startDate,
                               LocalDate endDate, BigDecimal days, String reason);

    /**
     * 人工审批请假申请
     * <p>
     * 仅对状态为"待审批"（status = 1）的请假单进行处理。
     * 审批完成后更新请假单状态，并记录审批日志。
     * </p>
     *
     * @param requestId  请假单 ID，对应数据库主键
     * @param approved   审批结果：true 表示通过，false 表示拒绝
     * @param remark     审批意见/备注说明
     * @throws RuntimeException 若请假单不存在或已处理（非待审批状态）
     */
    @Transactional
    void manualApprove(Long requestId, boolean approved, String remark);

    /**
     * 查询指定用户的请假记录列表
     * <p>
     * 返回该用户提交的所有请假申请，按创建时间降序排列（最新的排在最前面）。
     * </p>
     *
     * @param userId 用户 ID
     * @return 该用户的请假记录列表，若该用户无记录则返回空列表
     */
    List<HrLeaveRequest> listByUser(Long userId);


    /**
     * 用户取消假期
     * <p>
     * </p>
     *
     * @param userId 用户 ID
     */
    void cancelLeave(Long userId, String requestNo);
}