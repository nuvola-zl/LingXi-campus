package top.lingxi.campus.domain.admin.eneity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("admin_device_detail")
public class AdminDeviceDetail {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String deviceType;
    
    private String sn;
    
    private Integer status;
    
    private Long assignedUserId;
    
    private LocalDateTime assignedAt;
    
    private String location;
}