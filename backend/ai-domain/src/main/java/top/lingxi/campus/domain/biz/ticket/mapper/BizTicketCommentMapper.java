package top.lingxi.campus.domain.biz.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.lingxi.campus.domain.biz.ticket.entity.BizTicketComment;

import java.util.List;

@Mapper
public interface BizTicketCommentMapper extends BaseMapper<BizTicketComment> {
    
    @Select("SELECT * FROM biz_ticket_comment WHERE ticket_id = #{ticketId} ORDER BY created_at ASC")
    List<BizTicketComment> selectByTicketId(@Param("ticketId") Long ticketId);
}