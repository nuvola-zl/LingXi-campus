package top.lingxi.campus.domain.engineer.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("biz_engineer_group_member")
public class BizEngineerGroupMember {

    private Long id;

    private Long groupId;

    private Long userId;
}