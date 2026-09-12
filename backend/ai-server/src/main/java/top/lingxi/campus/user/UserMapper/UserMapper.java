package top.lingxi.campus.user.UserMapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.lingxi.campus.domain.user.po.SysUser;

@Mapper
public interface UserMapper extends BaseMapper<SysUser> {
}
