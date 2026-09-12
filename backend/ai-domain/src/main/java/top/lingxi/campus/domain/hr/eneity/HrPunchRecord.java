package top.lingxi.campus.domain.hr.eneity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("hr_punch_record")
public class HrPunchRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String punchNo;

    private Long userId;

    private LocalDate punchDate;

    private String punchType;

    private String reason;

    private Integer status;

    private Integer isManualReview;

    private Long reviewedBy;

    private LocalDateTime reviewedAt;

    private String reviewRemark;

    private LocalDateTime createdAt;
}