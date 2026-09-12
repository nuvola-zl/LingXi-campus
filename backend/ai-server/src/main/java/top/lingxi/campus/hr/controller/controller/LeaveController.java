package top.lingxi.campus.hr.controller.controller;

import lombok.Data;
import top.lingxi.campus.hr.service.LeaveService;
import top.lingxi.campus.common.context.BaseContext;
import top.lingxi.campus.common.result.Result;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.lingxi.campus.domain.hr.eneity.HrLeaveRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/hr/leave")
public class LeaveController {
    
    @Autowired
    private LeaveService leaveService;
    
    @PostMapping("/submit")
    public Result<HrLeaveRequest> submit(@RequestBody LeaveSubmitDTO dto) {
        HrLeaveRequest request = leaveService.submitLeave(
            BaseContext.getCurrentId(),
            dto.getType(),
            dto.getStartDate(),
            dto.getEndDate(),
            dto.getDays(),
            dto.getReason()
        );
        return Result.success(request);
    }
    
    @GetMapping("/list")
    public Result<List<HrLeaveRequest>> list() {
        return Result.success(leaveService.listByUser(BaseContext.getCurrentId()));
    }
    
    @Data
    public static class LeaveSubmitDTO {
        private String type;
        private LocalDate startDate;
        private LocalDate endDate;
        private BigDecimal days;
        private String reason;
    }
}