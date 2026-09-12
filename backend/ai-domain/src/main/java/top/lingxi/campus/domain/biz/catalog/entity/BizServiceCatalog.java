package top.lingxi.campus.domain.biz.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import top.lingxi.campus.domain.handler.JsonListTypeHandler;

import java.util.List;

@Data
public class BizServiceCatalog {
    private Long id;
    private String name;
    private Long categoryId;
    @TableField(typeHandler = JsonListTypeHandler.class)
    private List<String> triggerKeywords;
    private String description;
    private Integer defaultPriority;
    private Long defaultGroupId;
    private Long faqDocId;
    private Integer status;
    
    // 关联字段（Mapper 里 as 出来的）
    private String categoryCode;
}