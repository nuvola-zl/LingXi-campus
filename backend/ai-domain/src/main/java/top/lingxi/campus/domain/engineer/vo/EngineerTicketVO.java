package top.lingxi.campus.domain.engineer.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class EngineerTicketVO {
    private Long id;
    private String ticketNo;
    private String title;
    private String description;
    private Integer priority;
    private String priorityLabel;
    private Integer status;
    private String statusLabel;
    private Long requesterId;
    private String requesterName;
    private Long assigneeId;
    private String assigneeName;
    private Long groupId;
    private String groupName;
    private Long categoryId;
    private String categoryName;
    private LocalDateTime createdAt;
    private LocalDateTime lastActiveAt;
    private List<TicketCommentVO> comments;
}