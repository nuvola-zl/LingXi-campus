package top.lingxi.campus.admin.engineer.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import top.lingxi.campus.admin.user.service.DeviceRequestService;
import top.lingxi.campus.admin.user.service.PurchaseService;
import top.lingxi.campus.admin.engineer.service.ApprovalTaskService;
import top.lingxi.campus.hr.service.LeaveService;
import top.lingxi.campus.hr.service.PunchService;
import top.lingxi.campus.common.annotation.RequireRole;
import top.lingxi.campus.common.context.BaseContext;
import top.lingxi.campus.common.exception.BusinessException;
import top.lingxi.campus.common.exception.ErrorCode;
import top.lingxi.campus.common.result.Result;
import org.springframework.web.bind.annotation.*;
import top.lingxi.campus.domain.admin.eneity.AdminDeviceRequest;
import top.lingxi.campus.domain.admin.eneity.AdminDeviceReturn;
import top.lingxi.campus.domain.admin.eneity.AdminPurchaseRequest;
import top.lingxi.campus.domain.admin.mapper.AdminDeviceReturnMapper;
import top.lingxi.campus.domain.admin.mapper.AdminPurchaseRequestMapper;

import java.util.List;
import java.util.Map;

/**
 * 管理端控制台（审批 + 设备/采购管理）
 *
 * 重构变更（DAG 移除）：
 * 1. confirmArrived 移除 DAG 恢复编排——onPurchaseArrived 内部已直接完成分配
 * 2. confirmReturn 业务逻辑下移至 DeviceRequestService.confirmReturn（Controller 不再写业务）
 */
@RestController
@RequestMapping("/admin/manage")
@RequiredArgsConstructor
public class AdminManageController {

    private final ApprovalTaskService approvalTaskService;
    private final LeaveService leaveService;
    private final PunchService punchService;
    private final PurchaseService purchaseService;
    private final DeviceRequestService deviceRequestService;
    private final AdminDeviceReturnMapper returnMapper;
    private final AdminPurchaseRequestMapper purchaseMapper;

    @RequireRole("ADMIN")
    @GetMapping("/pending")
    public Result<List<Map<String, Object>>> pendingList() {
        return Result.success(approvalTaskService.getPendingTasks());
    }

    @RequireRole("ADMIN")
    @PostMapping("/purchase/{orderNo}/arrived")
    public Result<Void> confirmArrived(@PathVariable String orderNo) {
        // 入库 + 自动分配全部在 service 内完成（原 DAG resumeOnArrived 已移除）
        deviceRequestService.onPurchaseArrived(orderNo);
        return Result.success();
    }

    @RequireRole("ADMIN")
    @PostMapping("/leave/{requestId}/approve")
    public Result<Void> approveLeave(@PathVariable Long requestId,
                                     @RequestBody ApproveDTO dto) {
        leaveService.manualApprove(requestId, dto.isApproved(), dto.getRemark());
        return Result.success();
    }

    @RequireRole("ADMIN")
    @PostMapping("/fine/{fineId}/approve")
    public Result<Void> approveFine(@PathVariable Long fineId,
                                    @RequestBody ApproveDTO dto) {
        punchService.manualReview(fineId, BaseContext.getCurrentId(), dto.isApproved(), dto.getRemark());
        return Result.success();
    }

    @RequireRole("ADMIN")
    @PostMapping("/purchase/{purchaseId}/approve")
    public Result<Void> approvePurchase(@PathVariable Long purchaseId,
                                        @RequestBody ApproveDTO dto) {
        purchaseService.approvePurchase(purchaseId, dto.isApproved(), dto.getRemark());
        return Result.success();
    }

    /**
     * 管理员确认用户已提货（线下场景：用户到设备间，管理员确认）
     */
    @RequireRole("ADMIN")
    @PostMapping("/device/{requestNo}/confirmHandover")
    public Result<Void> confirmHandover(@PathVariable String requestNo) {
        AdminDeviceRequest request = deviceRequestService.getByRequestNo(requestNo);
        if (request == null) throw new BusinessException(ErrorCode.DEVICE_REQUEST_NOT_FOUND);
        if (request.getStatus() != 4) {
            throw new BusinessException(ErrorCode.DEVICE_REQUEST_STATUS_INVALID);
        }
        deviceRequestService.completeRequest(requestNo);
        return Result.success();
    }

    @RequireRole("ADMIN")
    @PostMapping("/device/return/{returnId}/confirm")
    public Result<Void> confirmReturn(
            @PathVariable Long returnId,
            @RequestParam(required = false) String remark) {
        if (returnId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        // 业务逻辑已下移至 service（设备入库 + 库存 + 归还记录 + 申领单 四连更新）
        deviceRequestService.confirmReturn(returnId, remark);
        return Result.success();
    }

    @RequireRole("ADMIN")
    @GetMapping("/purchase/list")
    public Result<Page<AdminPurchaseRequest>> purchaseList(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        QueryWrapper<AdminPurchaseRequest> wrapper = new QueryWrapper<>();
        if (status != null) wrapper.eq("status", status);
        wrapper.orderByDesc("created_at");

        Page<AdminPurchaseRequest> pageResult = purchaseMapper.selectPage(
                new Page<>(page, size), wrapper
        );
        return Result.success(pageResult);
    }

    @RequireRole("ADMIN")
    @GetMapping("/device/returns/pending")
    public Result<List<AdminDeviceReturn>> pendingReturns() {
        QueryWrapper<AdminDeviceReturn> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 1)  // 1=待确认
                .orderByDesc("created_at");
        return Result.success(returnMapper.selectList(wrapper));
    }

    @Data
    public static class ApproveDTO {
        private boolean approved;
        private String remark;
    }
}