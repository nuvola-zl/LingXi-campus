package top.lingxi.campus.domain.biz.catalog.mapper;

import org.apache.ibatis.annotations.*;
import top.lingxi.campus.domain.biz.catalog.entity.BizServiceCatalog;


import java.util.List;

@Mapper
public interface BizServiceCatalogMapper {

    @Select("""
        SELECT 
            sc.id, sc.name, sc.category_id, sc.trigger_keywords,
            sc.default_priority, sc.default_group_id,
            c.code as category_code
        FROM biz_service_catalog sc
        LEFT JOIN biz_ticket_category c ON sc.category_id = c.id
        WHERE sc.status = 1
        """)
    List<BizServiceCatalog> selectAllWithCategory();

    @Select("""
        SELECT sc.*, c.code as category_code
        FROM biz_service_catalog sc
        LEFT JOIN biz_ticket_category c ON sc.category_id = c.id
        WHERE sc.id = #{id}
        """)
    BizServiceCatalog selectById(Long id);

    @Insert("""
        INSERT INTO biz_service_catalog 
        (name, category_id, trigger_keywords, description, default_priority, default_group_id, faq_doc_id, status, created_at, updated_at)
        VALUES 
        (#{name}, #{categoryId}, #{triggerKeywords}, #{description}, #{defaultPriority}, #{defaultGroupId}, #{faqDocId}, 1, NOW(), NOW())
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(BizServiceCatalog catalog);

    @Update("""
        UPDATE biz_service_catalog SET
            name = #{name},
            category_id = #{categoryId},
            trigger_keywords = #{triggerKeywords},
            description = #{description},
            default_priority = #{defaultPriority},
            default_group_id = #{defaultGroupId},
            faq_doc_id = #{faqDocId},
            updated_at = NOW()
        WHERE id = #{id}
        """)
    int updateById(BizServiceCatalog catalog);

    @Update("UPDATE biz_service_catalog SET status = 0, updated_at = NOW() WHERE id = #{id}")
    int deleteById(Long id);

    @Select("SELECT COUNT(*) FROM biz_service_catalog WHERE status = 1")
    long count();
}