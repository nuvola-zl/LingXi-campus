package top.lingxi.campus.domain.engineer.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TicketProcessRequest {
    @NotBlank(message = "处理内容不能为空")
    private String content;
}