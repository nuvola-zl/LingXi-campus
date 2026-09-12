package top.lingxi.campus.user.UserService;

import top.lingxi.campus.domain.user.dto.SysUserLoginDTO;
import top.lingxi.campus.domain.user.po.SysUser;
import top.lingxi.campus.domain.user.vo.SysUserLoginVO;

public interface UserService {
    //登录
    SysUserLoginVO login(SysUserLoginDTO loginVO);

    //获取信息
    SysUser info();
}
