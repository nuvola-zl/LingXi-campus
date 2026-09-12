package top.lingxi.campus.admin.engineer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.lingxi.campus.admin.engineer.service.ApprovalTaskService;
import top.lingxi.campus.domain.admin.eneity.AdminPurchaseRequest;
import top.lingxi.campus.domain.admin.mapper.AdminPurchaseRequestMapper;

import top.lingxi.campus.domain.hr.eneity.HrLeaveRequest;
import top.lingxi.campus.domain.hr.eneity.HrPunchRecord;
import top.lingxi.campus.domain.hr.mapper.HrLeaveRequestMapper;
import top.lingxi.campus.domain.hr.mapper.HrPunchRecordMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ApprovalTaskServiceImpl implements ApprovalTaskService {

    @Autowired
    private HrLeaveRequestMapper leaveMapper;

    @Autowired
    private HrPunchRecordMapper punchMapper;

    @Autowired
    private AdminPurchaseRequestMapper purchaseMapper;

    /**
     * 获取所有待审批任务
     */
    public List<Map<String, Object>> getPendingTasks() {
        List<Map<String, Object>> tasks = new ArrayList<>();

        // 待人工审批的请假（status=1 且 approve_type=2）
        QueryWrapper<HrLeaveRequest> leaveWrapper = new QueryWrapper<>();
        leaveWrapper.eq("status", 1).eq("approve_type", 2).orderByDesc("created_at");
        List<HrLeaveRequest> leaves = leaveMapper.selectList(leaveWrapper);
        for (HrLeaveRequest leave : leaves) {
            Map<String, Object> task = new HashMap<>();
            task.put("type", "leave");
            task.put("id", leave.getId());
            task.put("no", leave.getRequestNo());
            task.put("userId", leave.getUserId());
            task.put("content", leave.getType() + " " + leave.getDays() + "天");
            task.put("createdAt", leave.getCreatedAt());
            tasks.add(task);
        }

        // 待审核的罚单（status=5 自定义为待审核，或者按 is_manual_review=1 且 status=1）
        QueryWrapper<HrPunchRecord> punchWrapper = new QueryWrapper<>();
        punchWrapper.eq("is_manual_review", 1).eq("status", 1).orderByDesc("created_at");
        List<HrPunchRecord> punches = punchMapper.selectList(punchWrapper);
        for (HrPunchRecord punch : punches) {
            Map<String, Object> task = new HashMap<>();
            task.put("type", "punch");
            task.put("id", punch.getId());
            task.put("no", punch.getPunchNo());
            task.put("userId", punch.getUserId());
            task.put("content", punch.getPunchType());
            task.put("createdAt", punch.getCreatedAt());
            tasks.add(task);
        }

        // 待审批的采购（status=1）以及已批准待到货的采购（status=2）
        QueryWrapper<AdminPurchaseRequest> purchaseWrapper = new QueryWrapper<>();
        purchaseWrapper.in("status", java.util.List.of(1, 2)).orderByDesc("created_at");
        List<AdminPurchaseRequest> purchases = purchaseMapper.selectList(purchaseWrapper);
        for (AdminPurchaseRequest purchase : purchases) {
            Map<String, Object> task = new HashMap<>();
            task.put("type", "purchase");
            task.put("id", purchase.getId());
            task.put("orderNo", purchase.getOrderNo());
            task.put("status", purchase.getStatus());
            task.put("title", purchase.getDeviceType() + " x" + purchase.getQuantity());
            task.put("reason", purchase.getReason());
            task.put("requester", "采购单 " + purchase.getOrderNo());
            task.put("createdAt", purchase.getCreatedAt());
            tasks.add(task);
        }

        return tasks;
    }
}