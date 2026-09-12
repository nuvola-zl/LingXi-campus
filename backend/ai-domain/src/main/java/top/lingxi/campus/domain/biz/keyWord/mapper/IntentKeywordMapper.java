package top.lingxi.campus.domain.biz.keyWord.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import top.lingxi.campus.domain.biz.keyWord.eneity.IntentKeyword;

import java.util.List;

@Mapper
public interface IntentKeywordMapper extends BaseMapper<IntentKeyword> {
    
    @Select("SELECT * FROM ai_intent_keyword WHERE enabled = 1")
    List<IntentKeyword> selectAllEnabled();
}