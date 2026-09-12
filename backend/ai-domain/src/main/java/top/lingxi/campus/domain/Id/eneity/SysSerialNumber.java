package top.lingxi.campus.domain.Id.eneity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;

@Data
@TableName("sys_serial_number")
public class SysSerialNumber {
    
    @TableId
    private String prefix;
    
    private LocalDate currDate;
    
    private Integer currentNo;
}