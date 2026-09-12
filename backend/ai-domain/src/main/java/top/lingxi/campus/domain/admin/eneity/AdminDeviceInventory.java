package top.lingxi.campus.domain.admin.eneity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("admin_device_inventory")
public class AdminDeviceInventory {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String deviceType;
    
    private String model;
    
    private Integer totalCount;
    
    private Integer availableCount;
    
    private String location;
    
    private Integer status;
    
    private LocalDateTime updatedAt;
}