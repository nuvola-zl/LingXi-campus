package top.lingxi.campus.domain.user.vo;

import lombok.Data;

/**
 * 用户登录响应VO
 * 登录成功后返回给前端的数据（含JWT令牌）
 */
@Data
public class SysUserLoginVO {

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 角色类型：EMPLOYEE / ENGINEER / ADMIN
     */
    private String roleType;

    /**
     * JWT访问令牌
     * 前端后续请求需在Header中携带：Authorization: Bearer <token>
     */
    private String token;

}