package top.lingxi.campus.hr.controller.controller;

import lombok.Data;
import top.lingxi.campus.hr.service.PunchService;
import top.lingxi.campus.common.context.BaseContext;
import top.lingxi.campus.common.result.Result;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.lingxi.campus.domain.hr.eneity.HrPunchRecord;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/hr/punch")
public class PunchController {
    
    @Autowired
    private PunchService punchService;
    
    /**
     * 提交忘打卡/迟到补卡申请
     */
    @PostMapping("/apply")
    public Result<HrPunchRecord> apply(@RequestBody PunchApplyDTO dto) {
        HrPunchRecord record = punchService.submitPunch(
            BaseContext.getCurrentId(),
            dto.getPunchType(),
            dto.getPunchDate(),
            dto.getReason()
        );
        
        String msg = record.getIsManualReview() == 1 
            ? "已提交，等待管理员审核" 
            : "AI自动通过，补卡成功";
        return Result.success(record);
    }


    /**
     * 查询用户的补卡记录
     */
    @GetMapping("/list")
    public Result<List<HrPunchRecord>> list() {
        return Result.success(punchService.listByUser(BaseContext.getCurrentId()));
    }
    
    @Data
    public static class PunchApplyDTO {
        private String punchType;    // miss_punch/late
        private LocalDate punchDate;
        private String reason;
    }
}