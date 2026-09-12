package top.lingxi.campus.domain.rocord.eneity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import top.lingxi.campus.common.config.JsonbTypeHandler;

import java.time.LocalDateTime;

@Data
@TableName("sys_dag_execution")
public class SysDagExecution {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String requestNo;

    private String dagName;

    private Integer status;

    private String currentNode;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String context;

    private LocalDateTime createdAt;

    private LocalDateTime completedAt;
}