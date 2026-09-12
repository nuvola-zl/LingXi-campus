package top.lingxi.campus.domain.admin.eneity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("admin_meeting_room")
public class AdminMeetingRoom {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String name;
    
    private String location;
    
    private Integer capacity;
    
    private String facilities;
    
    private Integer status;
}