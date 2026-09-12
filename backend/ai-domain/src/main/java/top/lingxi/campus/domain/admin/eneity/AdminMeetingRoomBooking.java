package top.lingxi.campus.domain.admin.eneity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Data
@TableName("admin_meeting_room_booking")
public class AdminMeetingRoomBooking {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String bookingNo;
    
    private Long roomId;
    
    private Long userId;
    
    private LocalDate bookingDate;
    
    private LocalTime startTime;
    
    private LocalTime endTime;
    
    private String purpose;
    
    private Integer status;
    
    private LocalDateTime createdAt;
}