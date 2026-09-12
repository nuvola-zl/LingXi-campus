package top.lingxi.campus.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import top.lingxi.campus.common.annotation.RequireRole;
import top.lingxi.campus.common.context.BaseContext;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RoleCheckAspect {

    @Around("@annotation(requireRole)")
    public Object around(ProceedingJoinPoint point, RequireRole requireRole) throws Throwable {
        Long userId = BaseContext.getCurrentId();
        String actualRole = BaseContext.getCurrentRoleType();

        if (userId == null || actualRole == null) {
            throw new RuntimeException("用户未登录");
        }

        Set<String> requiredRoles = Arrays.stream(requireRole.value().split(","))
                .map(String::trim)
                .collect(Collectors.toSet());

        if (!requiredRoles.contains(actualRole)) {
            log.warn("权限校验失败: userId={}, role={}, required={}",
                    userId, actualRole, requiredRoles);
            throw new RuntimeException("权限不足，需要 " + requireRole.value() + " 角色");
        }

        return point.proceed();
    }
}