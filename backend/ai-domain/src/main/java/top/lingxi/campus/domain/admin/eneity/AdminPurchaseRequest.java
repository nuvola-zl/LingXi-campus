package top.lingxi.campus.domain.admin.eneity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("admin_purchase_request")
public class AdminPurchaseRequest {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String orderNo;
    
    private String deviceType;
    
    private Integer quantity;
    
    private String reason;
    
    private Integer status;
    
    private Long approvedBy;
    
    private LocalDateTime approvedAt;
    
    private LocalDateTime arrivedAt;
    
    private LocalDateTime createdAt;
}