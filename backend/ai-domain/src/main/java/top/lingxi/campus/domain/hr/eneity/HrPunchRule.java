package top.lingxi.campus.domain.hr.eneity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("hr_punch_rule")
public class HrPunchRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String punchType;

    private Integer tier;

    private Integer thresholdMin;

    private Integer thresholdMax;

    private Integer needReview;

    private String description;

    private Integer status;

    private LocalDateTime createdAt;
}