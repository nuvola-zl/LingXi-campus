package top.lingxi.campus.domain.biz.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.lingxi.campus.domain.biz.ticket.entity.BizTicket;

import java.util.List;

/**
 * 报修单 Mapper
 * 负责 biz_ticket 表的数据库访问
 */
@Mapper
public interface BizTicketMapper extends BaseMapper<BizTicket> {

    /**
     * 根据提单人查询其所有报修单，按创建时间倒序（最新的在前面）
     * 用于 AI 对话中"我有什么报修单"的场景
     *
     * @param requesterId 提单人用户ID
     * @return 该用户提交的报修单列表
     */
    @Select("SELECT * FROM biz_ticket WHERE requester_id = #{requesterId} ORDER BY created_at DESC")
    List<BizTicket> selectByRequesterId(@Param("requesterId") Long requesterId);

    /**
     * 根据报修单编号查询单条记录
     * 用于用户问"IT-20240715-001 怎么样了"这类指定编号的查询
     *
     * @param ticketNo 报修单编号，如 IT-20240715-001
     * @return 单条报修单记录，不存在时返回 null
     */
    @Select("SELECT * FROM biz_ticket WHERE ticket_no = #{ticketNo}")
    BizTicket selectByTicketNo(@Param("ticketNo") String ticketNo);

    /**
     * 查某天某分类的最大序号，用于生成报修单号
     * 示例：查 IT-20240715-% 的最大序号
     */
    @Select("SELECT COALESCE(MAX(CAST(SUBSTRING(ticket_no FROM LENGTH(#{prefix}) + 1) AS INTEGER)), 0) " +
            "FROM biz_ticket WHERE ticket_no LIKE #{prefix} || '%'")
    Integer selectMaxSeqByPrefix(@Param("prefix") String prefix);

    /**
     * 查用户最新一条未关闭的报修单（用于催单）
     */
    @Select("SELECT * FROM biz_ticket WHERE requester_id = #{requesterId} AND status != 4 ORDER BY created_at DESC LIMIT 1")
    BizTicket selectLatestOpenTicket(@Param("requesterId") Long requesterId);

    @Select("SELECT COALESCE(MAX(CAST(SUBSTRING(ticket_no FROM '([0-9]+)$') AS INTEGER)), 0) " +
            "FROM biz_ticket " +
            "WHERE ticket_no LIKE CONCAT(#{code}, '-', #{dateStr}, '-%')")
    Integer selectMaxSeqByCodeAndDate(@Param("code") String code, @Param("dateStr") String dateStr);
}