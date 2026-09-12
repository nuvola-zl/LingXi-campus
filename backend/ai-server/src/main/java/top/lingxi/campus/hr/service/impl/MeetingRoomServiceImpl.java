package top.lingxi.campus.hr.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.lingxi.campus.hr.service.MeetingRoomService;
import top.lingxi.campus.common.id.service.SerialNumberService;
import top.lingxi.campus.common.exception.BusinessException;
import top.lingxi.campus.common.exception.ErrorCode;
import top.lingxi.campus.domain.admin.eneity.AdminMeetingRoom;
import top.lingxi.campus.domain.admin.eneity.AdminMeetingRoomBooking;
import top.lingxi.campus.domain.admin.mapper.AdminMeetingRoomBookingMapper;
import top.lingxi.campus.domain.admin.mapper.AdminMeetingRoomMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
public class MeetingRoomServiceImpl implements MeetingRoomService {

    @Autowired
    private AdminMeetingRoomMapper roomMapper;

    @Autowired
    private AdminMeetingRoomBookingMapper bookingMapper;

    @Autowired
    private SerialNumberService serialService;

    /**
     * 查询所有可用会议室
     */
    public List<AdminMeetingRoom> listAvailable() {
        QueryWrapper<AdminMeetingRoom> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 1);
        return roomMapper.selectList(wrapper);
    }

    /**
     * 查询某日期空闲会议室
     */
    public List<AdminMeetingRoom> listAvailableByDate(LocalDate date, LocalTime startTime, LocalTime endTime) {
        List<AdminMeetingRoom> allRooms = listAvailable();

        QueryWrapper<AdminMeetingRoomBooking> wrapper = new QueryWrapper<>();
        wrapper.eq("booking_date", date)
                .eq("status", 1)
                .lt("start_time", endTime)
                .gt("end_time", startTime);
        List<AdminMeetingRoomBooking> bookings = bookingMapper.selectList(wrapper);

        List<Long> bookedRoomIds = bookings.stream()
                .map(AdminMeetingRoomBooking::getRoomId)
                .distinct()
                .toList();

        return allRooms.stream()
                .filter(r -> !bookedRoomIds.contains(r.getId()))
                .toList();
    }

    /**
     * 预定会议室
     */
    @Transactional
    public AdminMeetingRoomBooking bookRoom(Long userId, Long roomId, LocalDate date,
                                            LocalTime startTime, LocalTime endTime, String purpose) {

        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        if (date.isBefore(today)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "不能预订过去的日期（" + date + "），今天是 " + today + "。");
        }

        if (date.equals(today) && startTime.isBefore(now)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "预订开始时间 " + startTime + " 已过期，当前时间 " + now + "，请重新选择。");
        }

        if (!endTime.isAfter(startTime)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "结束时间必须晚于开始时间。");
        }

        // 重复预订校验
        QueryWrapper<AdminMeetingRoomBooking> dup = new QueryWrapper<>();
        dup.eq("room_id", roomId)
                .eq("booking_date", date)
                .and(w -> w.lt("start_time", endTime).gt("end_time", startTime))
                .ne("status", 2);

        if (bookingMapper.selectCount(dup) > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_REQUEST,
                    "该会议室在 " + startTime + "-" + endTime + " 已被预订，请选择其他时段。");
        }

        String bookingNo = serialService.generate("MR");

        AdminMeetingRoomBooking booking = new AdminMeetingRoomBooking();
        booking.setBookingNo(bookingNo);
        booking.setRoomId(roomId);
        booking.setUserId(userId);
        booking.setBookingDate(date);
        booking.setStartTime(startTime);
        booking.setEndTime(endTime);
        booking.setPurpose(purpose);
        booking.setStatus(1);

        bookingMapper.insert(booking);
        return booking;
    }

    /**
     * 按 ID 取消预定（保留，供内部或其他接口使用）
     */
    @Transactional
    public void cancelBooking(Long bookingId) {
        AdminMeetingRoomBooking booking = bookingMapper.selectById(bookingId);
        if (booking == null || booking.getStatus() != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "预定不存在或已取消");
        }
        booking.setStatus(2);
        bookingMapper.updateById(booking);
    }

    /**
     * 按单号取消预定（供 AI 工具调用）
     */
    @Transactional
    public void cancelBookingByNo(String bookingNo) {
        QueryWrapper<AdminMeetingRoomBooking> wrapper = new QueryWrapper<>();
        wrapper.eq("booking_no", bookingNo);
        AdminMeetingRoomBooking booking = bookingMapper.selectOne(wrapper);

        if (booking == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "预定单号不存在，请检查单号是否正确。");
        }
        if (booking.getStatus() != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "该预定当前状态为" + bookingStatusName(booking.getStatus()) +
                            "，无法取消。只有有效的预定可以取消。");
        }

        booking.setStatus(2); // 已取消
        bookingMapper.updateById(booking);
    }

    /**
     * 查询用户的预定记录
     */
    public List<AdminMeetingRoomBooking> listByUser(Long userId) {
        QueryWrapper<AdminMeetingRoomBooking> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("created_at");
        return bookingMapper.selectList(wrapper);
    }

    /**
     * 【新增】状态映射（和 HR 对齐）
     */
    private String bookingStatusName(Integer status) {
        return switch (status) {
            case 1 -> "有效";
            case 2 -> "已取消";
            default -> "未知";
        };
    }
}