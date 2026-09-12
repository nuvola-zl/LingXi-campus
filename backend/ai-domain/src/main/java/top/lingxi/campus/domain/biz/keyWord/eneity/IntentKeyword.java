package top.lingxi.campus.domain.biz.keyWord.eneity;

import lombok.Data;

@Data
public class IntentKeyword {
    private Long id;
    private String intent;
    private String keyword;
    private String matchType;
    private Integer weight;
    private Integer enabled;
}