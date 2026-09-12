package top.lingxi.campus.common.annotation;

import java.lang.annotation.*;

/**
 * 角色权限校验注解
 * 标注在 Controller 方法上，限制只有指定角色能访问
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {
    /**
     * 需要的角色，多个用逗号分隔（满足其一即可）
     * 例："ADMIN" 或 "ADMIN,ENGINEER"
     */
    String value();
}