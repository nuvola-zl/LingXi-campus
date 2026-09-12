package top.lingxi.campus.hr.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import top.lingxi.campus.hr.service.MeetingRoomService;
import top.lingxi.campus.common.context.BaseContext;
import top.lingxi.campus.common.result.Result;

import org.springframework.web.bind.annotation.*;
import top.lingxi.campus.domain.admin.eneity.AdminMeetingRoom;
import top.lingxi.campus.domain.admin.eneity.AdminMeetingRoomBooking;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/admin/meeting")
@RequiredArgsConstructor
public class MeetingRoomController {

    private final MeetingRoomService meetingRoomService;
    
    @GetMapping("/rooms")
    public Result<List<AdminMeetingRoom>> listRooms() {
        return Result.success(meetingRoomService.listAvailable());
    }
    
    @GetMapping("/available")
    public Result<List<AdminMeetingRoom>> available(
            @RequestParam LocalDate date,
            @RequestParam LocalTime startTime,
            @RequestParam LocalTime endTime) {
        return Result.success(meetingRoomService.listAvailableByDate(date, startTime, endTime));
    }
    
    @PostMapping("/book")
    public Result<AdminMeetingRoomBooking> book(@RequestBody BookDTO dto) {
        Long userId = BaseContext.getCurrentId();
        AdminMeetingRoomBooking booking = meetingRoomService.bookRoom(
            userId,
            dto.getRoomId(),
            dto.getDate(),
            dto.getStartTime(),
            dto.getEndTime(),
            dto.getPurpose()
        );
        return Result.success(booking);
    }
    
    @PostMapping("/cancel/{bookingId}")
    public Result<Void> cancel(@PathVariable Long bookingId) {
        meetingRoomService.cancelBooking(bookingId);
        return Result.success();
    }
    
    @GetMapping("/list")
    public Result<List<AdminMeetingRoomBooking>> list() {
        return Result.success(meetingRoomService.listByUser(BaseContext.getCurrentId()));
    }
    
    @Data
    public static class BookDTO {
        private Long roomId;
        private LocalDate date;
        private LocalTime startTime;
        private LocalTime endTime;
        private String purpose;
    }
}