package top.lingxi.campus.domain.engineer.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("biz_engineer_group")
public class BizEngineerGroup {

    private Long id;

    private String name;

    private String categoryCodes;

    private Long leaderId;

    private Integer status;

    private LocalDateTime createdAt;
}