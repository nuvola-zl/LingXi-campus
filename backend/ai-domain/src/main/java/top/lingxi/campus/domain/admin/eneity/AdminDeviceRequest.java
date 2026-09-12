package top.lingxi.campus.domain.admin.eneity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("admin_device_request")
public class AdminDeviceRequest {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String requestNo;
    
    private Long userId;
    
    private String deviceType;
    
    private String reason;
    
    private Integer status;
    
    private Integer stockStatus;
    
    private Long allocatedDeviceId;
    
    private String purchaseOrderNo;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime completedAt;
}