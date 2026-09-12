package top.lingxi.campus.domain.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.lingxi.campus.domain.admin.eneity.AdminMeetingRoomBooking;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Mapper
public interface AdminMeetingRoomBookingMapper extends BaseMapper<AdminMeetingRoomBooking> {
    
    /**
     * 检查时间段冲突
     */
    @Select("""
        SELECT * FROM admin_meeting_room_booking 
        WHERE room_id = #{roomId} 
          AND booking_date = #{bookingDate} 
          AND status = 1
          AND start_time < #{endTime} 
          AND end_time > #{startTime}
        """)
    List<AdminMeetingRoomBooking> checkConflict(@Param("roomId") Long roomId,
                                               @Param("bookingDate") LocalDate bookingDate,
                                               @Param("startTime") LocalTime startTime,
                                               @Param("endTime") LocalTime endTime);
}