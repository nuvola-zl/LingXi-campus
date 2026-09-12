package top.lingxi.campus.domain.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.lingxi.campus.domain.ai.entity.KbLibrary;

import java.util.List;

/**
 * 知识库 Mapper
 */
@Mapper
public interface KbLibraryMapper extends BaseMapper<KbLibrary> {

    /**
     * 获取用户知识库列表(带统计)
     * @param ownerId 用户ID
     * @param keyword 搜索关键字
     * @return 知识库列表
     */
    List<KbLibrary> listByOwnerWithStats(@Param("ownerId") Long ownerId, @Param("keyword") String keyword);

    /**
     * 获取知识库详情(带统计)
     * @param id 知识库ID
     * @return 知识库信息
     */
    KbLibrary getByIdWithStats(@Param("id") Long id);

    /**
     * 根据知识库名称查询（用于领域映射：IT知识库/HR知识库）
     */
    @Select("SELECT * FROM kb_library WHERE name = #{name} LIMIT 1")
    KbLibrary selectByName(@Param("name") String name);
}