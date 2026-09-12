package top.lingxi.campus.domain.admin.eneity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("admin_device_return")
public class AdminDeviceReturn {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String requestNo;
    private Long userId;
    private Long deviceId;
    private String sn;
    private String returnReason;
    private Integer status;        // 1待确认 2已入库 3已拒绝
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
    private Long confirmedBy;
}