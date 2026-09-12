package top.lingxi.campus.hr.service;


import org.springframework.transaction.annotation.Transactional;
import top.lingxi.campus.domain.admin.eneity.AdminMeetingRoom;
import top.lingxi.campus.domain.admin.eneity.AdminMeetingRoomBooking;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 会议室预定服务接口
 * <p>
 * 提供会议室资源管理与预定全流程服务，包括：
 * <ul>
 *   <li>查询可用会议室列表</li>
 *   <li>按日期和时间段查询空闲会议室</li>
 *   <li>提交会议室预定</li>
 *   <li>取消会议室预定</li>
 *   <li>查询用户预定记录</li>
 * </ul>
 * </p>
 *
 * <p>预定状态说明：</p>
 * <ul>
 *   <li>1 = 有效预定</li>
 *   <li>2 = 已取消</li>
 * </ul>
 *
 * @author hazeaihub
 * @version 1.0
 */
public interface MeetingRoomService {

    /**
     * 查询所有可用会议室
     * <p>
     * 返回状态为"可用"（status = 1）的所有会议室列表。
     * </p>
     *
     * @return 可用会议室列表，若无可用会议室则返回空列表
     */
    List<AdminMeetingRoom> listAvailable();

    /**
     * 查询指定日期和时间段内的空闲会议室
     * <p>
     * 先获取所有可用会议室，再过滤掉该日期、该时间段内已被预定的会议室。
     * 时间冲突判断逻辑：已有预定的结束时间 &gt; 查询的开始时间，且已有预定的开始时间 &lt; 查询的结束时间。
     * </p>
     *
     * @param date      预定日期
     * @param startTime 会议开始时间
     * @param endTime   会议结束时间，必须晚于 startTime
     * @return 该日期和时间段内空闲的会议室列表
     */
    List<AdminMeetingRoom> listAvailableByDate(LocalDate date, LocalTime startTime, LocalTime endTime);

    /**
     * 预定会议室
     * <p>
     * 提交会议室预定前会检查时间冲突（同一会议室同一时间段不可重复预定）。
     * 预定成功后生成唯一预定单号，状态置为"有效预定"（1）。
     * </p>
     *
     * @param userId    预定人用户 ID，不可为空
     * @param roomId    会议室 ID，不可为空
     * @param date      预定日期，不可为空
     * @param startTime 会议开始时间，不可为空
     * @param endTime   会议结束时间，不可为空
     * @param purpose   会议用途/主题说明
     * @return 预定成功的会议室预定记录实体，包含生成的预定单号
     * @throws RuntimeException 若该时间段会议室已被预定
     */
    @Transactional
    AdminMeetingRoomBooking bookRoom(Long userId, Long roomId, LocalDate date,
                                     LocalTime startTime, LocalTime endTime, String purpose);

    /**
     * 取消会议室预定
     * <p>
     * 仅对状态为"有效预定"（status = 1）的预定记录进行取消操作。
     * 取消后状态变为"已取消"（2）。
     * </p>
     *
     * @param bookingId 预定记录 ID，对应数据库主键
     * @throws RuntimeException 若预定记录不存在或已取消
     */
    @Transactional
    void cancelBooking(Long bookingId);

    /**
     * 【新增】按单号取消预定（供 AI 工具调用）
     * <p>
     * 根据预定单号（如 MR20260804-001）查询并取消预定。
     * 仅对状态为"有效预定"（status = 1）的预定记录进行取消操作。
     * 取消后状态变为"已取消"（2）。
     * </p>
     *
     * @param bookingNo 预定单号
     * @throws RuntimeException 若预定单号不存在或已取消
     */
    @Transactional
    void cancelBookingByNo(String bookingNo);




    /**
     * 查询指定用户的会议室预定记录列表
     * <p>
     * 返回该用户提交的所有会议室预定记录，按创建时间降序排列（最新的排在最前面）。
     * </p>
     *
     * @param userId 用户 ID
     * @return 该用户的预定记录列表，若该用户无记录则返回空列表
     */
    List<AdminMeetingRoomBooking> listByUser(Long userId);
}