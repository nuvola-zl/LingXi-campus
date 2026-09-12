package top.lingxi.campus.domain.engineer.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TicketCommentVO {
    private Long id;
    private String actor;      // 工程师名字 或 "系统"
    private String content;
    private String type;       // COMMENT / SYSTEM / HANDOVER
    private LocalDateTime createdAt;
}