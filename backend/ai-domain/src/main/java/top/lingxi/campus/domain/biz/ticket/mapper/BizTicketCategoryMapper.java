package top.lingxi.campus.domain.biz.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.lingxi.campus.domain.biz.ticket.entity.BizTicketCategory;

/**
 * 工单分类 Mapper
 */
@Mapper
public interface BizTicketCategoryMapper extends BaseMapper<BizTicketCategory> {

    /**
     * 根据编码查询分类
     */
    @Select("SELECT * FROM biz_ticket_category WHERE code = #{code} AND status = 1")
    BizTicketCategory selectByCode(@Param("code") String code);
}