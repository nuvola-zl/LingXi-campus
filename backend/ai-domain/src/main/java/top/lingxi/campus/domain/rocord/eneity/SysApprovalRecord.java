package top.lingxi.campus.domain.rocord.eneity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_approval_record")
public class SysApprovalRecord {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String bizType;
    
    private String bizTable;
    
    private Long bizId;
    
    //private Long approverId;
    
    private Integer action;
    
    private String remark;
    
    private LocalDateTime createdAt;
}