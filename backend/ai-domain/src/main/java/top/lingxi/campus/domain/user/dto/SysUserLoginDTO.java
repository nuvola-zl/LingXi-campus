package top.lingxi.campus.domain.user.dto;

import lombok.Data;

/**
 * 用户登录请求DTO
 * 前端传入的用户名和密码
 */
@Data
public class SysUserLoginDTO {

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码（明文传入，后端BCrypt校验）
     */
    private String password;
}