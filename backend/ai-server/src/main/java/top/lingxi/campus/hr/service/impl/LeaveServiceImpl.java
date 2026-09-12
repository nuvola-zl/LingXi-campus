package top.lingxi.campus.hr.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.lingxi.campus.hr.service.LeaveService;
import top.lingxi.campus.common.context.BaseContext;
import top.lingxi.campus.common.exception.BusinessException;
import top.lingxi.campus.common.exception.ErrorCode;
import top.lingxi.campus.common.id.service.SerialNumberService;
import top.lingxi.campus.domain.hr.eneity.HrLeaveRequest;
import top.lingxi.campus.domain.hr.mapper.HrLeaveRequestMapper;
import top.lingxi.campus.domain.rocord.eneity.SysApprovalRecord;
import top.lingxi.campus.domain.rocord.mapper.SysApprovalRecordMapper;
import top.lingxi.campus.tool.DuplicateChecker;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 请假服务
 *
 * 重构变更：清理注释掉的死代码（旧的时段校验、旧查重逻辑，均已被
 * DuplicateChecker 与现行校验取代），行为不变。
 */
@Service
@RequiredArgsConstructor
public class LeaveServiceImpl implements LeaveService {

    private final HrLeaveRequestMapper leaveMapper;
    private final SysApprovalRecordMapper approvalMapper;
    private final SerialNumberService serialService;
    private final DuplicateChecker duplicateChecker;

    /**
     * 提交请假申请
     */
    @Override
    @Transactional
    public HrLeaveRequest submitLeave(Long userId, String type, LocalDate startDate,
                                      LocalDate endDate, BigDecimal days, String reason) {

        // 防重复（DuplicateChecker 统一负责：待审批/已通过 且 日期重叠）
        String dupMsg = duplicateChecker.checkDuplicateLeave(userId, startDate, endDate);
        if (dupMsg != null) {
            throw new BusinessException(ErrorCode.DUPLICATE_REQUEST, dupMsg);
        }

        LocalDate today = LocalDate.now();

        // 校验 1：日期不能是过去
        if (startDate.isBefore(today)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "请假开始日期不能早于今天（" + today + "），请重新选择日期。");
        }

        // 校验 2：结束日期不能早于开始日期
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "结束日期不能早于开始日期。");
        }

        // 校验 3：天数必须合理
        long actualDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (days == null || days.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请假天数必须大于 0。");
        }
        if (days.compareTo(new BigDecimal(actualDays)) > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "请假天数（" + days + "）不能超过日期范围（" + actualDays + "天）。");
        }

        String requestNo = serialService.generate("LEAVE");

        int approveType = determineApproveType(type, days);

        HrLeaveRequest request = new HrLeaveRequest();
        request.setRequestNo(requestNo);
        request.setUserId(userId);
        request.setType(type);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setDays(days);
        request.setReason(reason);
        request.setStatus(1); // 待审批
        request.setApproveType(approveType);
        request.setCreatedAt(LocalDateTime.now());

        leaveMapper.insert(request);

        if (approveType == 1) {
            autoApprove(request);
        }

        return request;
    }

    /**
     * 判断审批方式：年假<=2天 / 事假<=1天 → AI审批，其他人工审批
     */
    private int determineApproveType(String type, BigDecimal days) {
        if ("annual".equals(type) && days.compareTo(new BigDecimal("2")) <= 0) {
            return 1;
        }
        if ("personal".equals(type) && days.compareTo(new BigDecimal("1")) <= 0) {
            return 1;
        }
        return 2;
    }

    /**
     * AI自动审批
     */
    private void autoApprove(HrLeaveRequest request) {
        request.setStatus(2); // 已通过
        request.setApprovedAt(LocalDateTime.now());
        leaveMapper.updateById(request);

        SysApprovalRecord record = new SysApprovalRecord();
        record.setBizType("leave");
        record.setBizTable("hr_leave_request");
        record.setBizId(request.getId());
        record.setAction(1); // 通过
        record.setRemark("AI自动审批通过");
        record.setCreatedAt(LocalDateTime.now());
        approvalMapper.insert(record);
    }

    /**
     * 人工审批
     */
    @Override
    @Transactional
    public void manualApprove(Long requestId, boolean approved, String remark) {
        HrLeaveRequest request = leaveMapper.selectById(requestId);
        if (request == null || request.getStatus() != 1) {
            throw new RuntimeException("请假单不存在或已处理");
        }

        request.setStatus(approved ? 2 : 3); // 2通过 3拒绝
        request.setApprovedBy(BaseContext.getCurrentId());
        request.setApprovedAt(LocalDateTime.now());
        request.setApproveRemark(remark);
        leaveMapper.updateById(request);

        SysApprovalRecord record = new SysApprovalRecord();
        record.setBizType("leave");
        record.setBizTable("hr_leave_request");
        record.setBizId(requestId);
        record.setAction(approved ? 1 : 2);
        record.setRemark(remark);
        record.setCreatedAt(LocalDateTime.now());
        approvalMapper.insert(record);
    }

    @Override
    public List<HrLeaveRequest> listByUser(Long userId) {
        QueryWrapper<HrLeaveRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("created_at");
        return leaveMapper.selectList(wrapper);
    }

    /**
     * 取消请假申请（仅待审批状态可取消）
     */
    @Override
    @Transactional
    public void cancelLeave(Long userId, String requestNo) {
        QueryWrapper<HrLeaveRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("request_no", requestNo).eq("user_id", userId);
        HrLeaveRequest request = leaveMapper.selectOne(wrapper);

        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "未找到该请假单，请检查单号是否正确。");
        }
        if (request.getStatus() != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "该请假单当前状态为" + statusName(request.getStatus()) +
                            "，无法取消。只有待审批的请假单可以取消。");
        }

        request.setStatus(4); // 已取消
        leaveMapper.updateById(request);
    }

    private String statusName(Integer status) {
        return switch (status) {
            case 1 -> "待审批";
            case 2 -> "已通过";
            case 3 -> "已拒绝";
            case 4 -> "已取消";
            default -> "未知";
        };
    }
}