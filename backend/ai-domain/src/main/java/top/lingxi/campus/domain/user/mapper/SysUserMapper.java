package top.lingxi.campus.domain.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.lingxi.campus.domain.user.po.SysUser;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}