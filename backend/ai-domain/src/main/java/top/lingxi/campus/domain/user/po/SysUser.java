package top.lingxi.campus.domain.user.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 系统用户实体
 * 对应数据库表: sys_user
 */
@Data
@TableName("sys_user")
public class SysUser {

    /**
     * 用户ID，主键，自增
     */
    private Long id;

    /**
     * 用户名，唯一标识，用于登录
     * 例如：zhangsan、employee001
     */
    private String username;

    /**
     * 登录密码，存储为BCrypt加密后的密文
     * 明文密码不会存储
     */
    private String password;

    /**
     * 真实姓名，用于界面展示和工单系统中的提单人/处理人显示
     * 例如：张三
     */
    private String realName;

    /**
     * 邮箱地址，用于通知和找回密码
     */
    private String email;

    /**
     * 手机号，用于短信通知和紧急联系
     */
    private String phone;

    /**
     * 头像URL，存储在OSS上的访问地址
     */
    private String avatar;

    /**
     * 所属部门ID，关联 sys_dept 表
     * 用于按部门筛选工单和工程师组分配
     */
    private Long deptId;

    /**
     * 角色类型：EMPLOYEE(员工)、ENGINEER(工程师)、ADMIN(管理员)
     * 决定用户在系统中的权限边界
     */
    private String roleType;

    /**
     * 账号状态：1-正常，0-禁用
     * 禁用的用户无法登录系统
     */
    private Integer status;

    /**
     * 创建时间，注册时自动生成
     */
    private LocalDateTime createdAt;

    /**
     * 最后更新时间，信息修改时自动刷新
     */
    private LocalDateTime updatedAt;
}