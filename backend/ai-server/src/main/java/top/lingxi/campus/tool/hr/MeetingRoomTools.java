package top.lingxi.campus.tool.hr;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import top.lingxi.campus.common.context.BaseContext;
import top.lingxi.campus.domain.admin.eneity.AdminMeetingRoom;
import top.lingxi.campus.domain.admin.eneity.AdminMeetingRoomBooking;
import top.lingxi.campus.hr.service.MeetingRoomService;
import top.lingxi.campus.tool.ToolCallGateway;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 后勤域工具：场地预约（研讨间/活动室/体育馆）
 *
 * 主题变更（灵犀校园）：会议室 → 校园场地，仅显示文案变更，业务逻辑不变
 *
 * 身份安全修复：用户 ID 通过 ToolContext 注入（见 HrTools 类注释）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MeetingRoomTools {

    private final MeetingRoomService meetingRoomService;
    private final ToolCallGateway toolGateway;

    @Tool(description = "查询某日期空闲场地（研讨间/活动室/体育馆等）")
    public String findAvailableRooms(
            @ToolParam(description = "日期，格式：yyyy-MM-dd") String date,
            @ToolParam(description = "开始时间，格式：HH:mm") String startTime,
            @ToolParam(description = "结束时间，格式：HH:mm") String endTime) {

        return toolGateway.execute("findAvailableRooms",
                Map.of("date", date, "startTime", startTime, "endTime", endTime),
                () -> doFindAvailableRooms(date, startTime, endTime));
    }

    private String doFindAvailableRooms(String date, String startTime, String endTime) {
        List<AdminMeetingRoom> rooms = meetingRoomService.listAvailableByDate(
                LocalDate.parse(date),
                LocalTime.parse(startTime),
                LocalTime.parse(endTime)
        );

        if (rooms.isEmpty()) {
            return String.format("%s %s~%s 暂无空闲场地", date, startTime, endTime);
        }

        return "找到以下空闲场地：\n" + rooms.stream()
                .map(r -> String.format("ID:%d %s（容量%d人）%s",
                        r.getId(), r.getName(), r.getCapacity(), r.getLocation()))
                .collect(Collectors.joining("\n"))
                + "\n\n请告诉我你想预定哪个（提供场地ID）";
    }

    @Tool(description = "预定校园场地")
    public String bookRoom(
            ToolContext toolContext,
            @ToolParam(description = "场地ID") Long roomId,
            @ToolParam(description = "日期，格式：yyyy-MM-dd") String date,
            @ToolParam(description = "开始时间，格式：HH:mm") String startTime,
            @ToolParam(description = "结束时间，格式：HH:mm") String endTime,
            @ToolParam(description = "用途说明") String purpose) {

        Long realUserId = resolveUserId(toolContext);
        return toolGateway.execute("bookRoom", Map.of("roomId", roomId, "date", date),
                () -> doBookRoom(realUserId, roomId, date, startTime, endTime, purpose));
    }

    private String doBookRoom(Long userId, Long roomId, String date,
                              String startTime, String endTime, String purpose) {
        try {
            AdminMeetingRoomBooking booking = meetingRoomService.bookRoom(
                    userId, roomId,
                    LocalDate.parse(date),
                    LocalTime.parse(startTime),
                    LocalTime.parse(endTime),
                    purpose
            );
            return String.format(
                    "✅ 预定成功！单号：%s\n" +
                            "📅 %s %s~%s\n" +
                            "🏢 场地ID：%d\n" +
                            "📝 用途：%s\n\n" +
                            "如需取消，请提供单号调用取消功能。",
                    booking.getBookingNo(), date, startTime, endTime, roomId, purpose
            );
        } catch (Exception e) {
            log.error("场地预定失败: userId={}, roomId={}, date={}", userId, roomId, date, e);
            return "❌ 预定失败：" + e.getMessage();
        }
    }

    @Tool(description = "取消场地预定。参数为预定单号（如 MR20260804-001）")
    public String cancelBooking(
            ToolContext toolContext,
            @ToolParam(description = "预定单号") String bookingNo) {

        Long realUserId = resolveUserId(toolContext);
        return toolGateway.execute("cancelBooking", Map.of("bookingNo", bookingNo),
                () -> doCancelBooking(realUserId, bookingNo));
    }

    private String doCancelBooking(Long userId, String bookingNo) {
        try {
            meetingRoomService.cancelBookingByNo(bookingNo);
            return "✅ 预定 " + bookingNo + " 已取消。";
        } catch (Exception e) {
            log.error("场地取消失败: userId={}, bookingNo={}", userId, bookingNo, e);
            return "❌ 取消失败：" + e.getMessage();
        }
    }

    @Tool(description = "查询学生的场地预定记录")
    public String listMyBookings(ToolContext toolContext) {

        Long realUserId = resolveUserId(toolContext);
        return toolGateway.execute("listMyBookings", Map.of(),
                () -> doListMyBookings(realUserId));
    }

    private String doListMyBookings(Long userId) {
        List<AdminMeetingRoomBooking> list = meetingRoomService.listByUser(userId);
        if (list.isEmpty()) return "暂无预定记录";

        return "您的场地预定记录如下（共 " + list.size() + " 条）：\n" +
                list.stream()
                        .map(b -> String.format("[%s] %s %s %s~%s %s",
                                b.getBookingNo(),
                                b.getBookingDate(),
                                b.getStartTime(),
                                b.getEndTime(),
                                b.getPurpose(),
                                bookingStatusName(b.getStatus())))
                        .collect(Collectors.joining("\n"));
    }

    /**
     * 从工具调用上下文解析用户 ID（ToolContext 优先，BaseContext 兜底）
     */
    private Long resolveUserId(ToolContext toolContext) {
        if (toolContext != null && toolContext.getContext() != null) {
            Object uid = toolContext.getContext().get("userId");
            if (uid instanceof Long l) return l;
            if (uid instanceof Number n) return n.longValue();
        }
        return BaseContext.getCurrentId();
    }

    private String bookingStatusName(Integer status) {
        return switch (status) {
            case 1 -> "有效";
            case 2 -> "已取消";
            default -> "未知";
        };
    }
}