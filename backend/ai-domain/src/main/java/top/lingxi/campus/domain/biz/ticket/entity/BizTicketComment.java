package top.lingxi.campus.domain.biz.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_ticket_comment")
public class BizTicketComment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ticketId;
    private Long userId;
    private String content;
    private String type;  // COMMENT / SYSTEM / HANDOVER
    private LocalDateTime createdAt;
}