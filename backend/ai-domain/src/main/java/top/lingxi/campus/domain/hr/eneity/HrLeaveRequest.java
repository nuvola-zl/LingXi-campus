package top.lingxi.campus.domain.hr.eneity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("hr_leave_request")
public class HrLeaveRequest {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String requestNo;
    
    private Long userId;
    
    private String type;
    
    private LocalDate startDate;
    
    private LocalDate endDate;
    
    private BigDecimal days;
    
    private String reason;
    
    private Integer status;
    
    private Integer approveType;
    
    private Long approvedBy;
    
    private LocalDateTime approvedAt;
    
    private String approveRemark;
    
    private LocalDateTime createdAt;
}