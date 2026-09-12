package top.lingxi.campus.hr.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.lingxi.campus.hr.service.PunchService;
import top.lingxi.campus.common.id.service.SerialNumberService;
import top.lingxi.campus.domain.hr.eneity.HrPunchRecord;
import top.lingxi.campus.domain.hr.eneity.HrPunchRule;
import top.lingxi.campus.domain.hr.mapper.HrPunchRecordMapper;
import top.lingxi.campus.domain.hr.mapper.HrPunchRuleMapper;
import top.lingxi.campus.domain.rocord.eneity.SysApprovalRecord;
import top.lingxi.campus.domain.rocord.mapper.SysApprovalRecordMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PunchServiceImpl implements PunchService {

    @Autowired
    private HrPunchRecordMapper punchMapper;

    @Autowired
    private HrPunchRuleMapper ruleMapper;

    @Autowired
    private SysApprovalRecordMapper approvalMapper;

    @Autowired
    private SerialNumberService serialService;

    /**
     * 提交忘打卡/迟到补卡申请
     */
    @Transactional
    public HrPunchRecord submitPunch(Long userId, String punchType, LocalDate punchDate, String reason) {
        // 查询本月该类型次数
        long count = punchMapper.countMonthly(userId, punchType, punchDate);

        // 匹配规则
        HrPunchRule rule = matchRule(punchType, (int) count + 1);
        boolean needReview = rule != null && rule.getNeedReview() == 1;

        // 生成单号
        String punchNo = serialService.generate("PUNCH");

        // 保存申请
        HrPunchRecord record = new HrPunchRecord();
        record.setPunchNo(punchNo);
        record.setUserId(userId);
        record.setPunchDate(punchDate);
        record.setPunchType(punchType);
        record.setReason(reason);
        record.setStatus(needReview ? 1 : 2); // 1待处理 2已通过(AI自动)
        record.setIsManualReview(needReview ? 1 : 0);
        record.setCreatedAt(LocalDateTime.now());

        punchMapper.insert(record);

        // AI自动通过
        if (!needReview) {
            record.setReviewedAt(LocalDateTime.now());
            punchMapper.updateById(record);
        }

        return record;
    }

    /**
     * 匹配规则
     */
    private HrPunchRule matchRule(String punchType, int count) {
        QueryWrapper<HrPunchRule> wrapper = new QueryWrapper<>();
        wrapper.eq("punch_type", punchType)
                .eq("status", 1)
                .le("threshold_min", count)
                .and(w -> w.isNull("threshold_max").or().ge("threshold_max", count))
                .orderByDesc("tier")
                .last("LIMIT 1");
        return ruleMapper.selectOne(wrapper);
    }

    /**
     * 管理员审核补卡申请
     */
    @Transactional
    public void manualReview(Long punchId, Long reviewerId, boolean approved, String remark) {
        HrPunchRecord record = punchMapper.selectById(punchId);
        if (record == null || record.getIsManualReview() != 1 || record.getStatus() != 1) {
            throw new RuntimeException("申请不存在或无需审核");
        }

        record.setStatus(approved ? 2 : 3); // 2通过 3拒绝
        record.setReviewedBy(reviewerId);
        record.setReviewedAt(LocalDateTime.now());
        record.setReviewRemark(remark);
        punchMapper.updateById(record);

        // 记录审批日志
        SysApprovalRecord approval = new SysApprovalRecord();
        approval.setBizType("punch");
        approval.setBizTable("hr_punch_record");
        approval.setBizId(punchId);

        approval.setAction(approved ? 1 : 2);
        approval.setRemark(remark);
        approval.setCreatedAt(LocalDateTime.now());
        approvalMapper.insert(approval);
    }

    /**
     * 查询用户的补卡记录
     */
    public List<HrPunchRecord> listByUser(Long userId) {
        QueryWrapper<HrPunchRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("created_at");
        return punchMapper.selectList(wrapper);
    }
}