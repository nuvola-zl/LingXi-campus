package top.lingxi.campus.common.interceptor;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import top.lingxi.campus.common.constant.JwtClaimsConstant;
import top.lingxi.campus.common.context.BaseContext;
import top.lingxi.campus.infra.security.JwtProperties;
import top.lingxi.campus.common.utils.JwtUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 统一 JWT 认证拦截器
 * 覆盖所有业务路径
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtTokenInterceptor implements HandlerInterceptor {

    private final JwtProperties jwtProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        log.info("JWT Interceptor: URI = {}", uri);

        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        String token = request.getHeader(jwtProperties.getUserTokenName());

        try {
            log.info("jwt校验: {}", token);
            Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);

            Long userId = Long.valueOf(claims.get(JwtClaimsConstant.USER_ID).toString());
            String roleType = claims.get(JwtClaimsConstant.ROLE_TYPE).toString();

            BaseContext.setCurrentUser(userId, roleType);
            log.info("当前用户: userId={}, roleType={}", userId, roleType);

            return true;

        } catch (Exception ex) {
            log.error("JWT 校验失败: {}", ex.getMessage());
            response.setStatus(401);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        BaseContext.removeCurrentUser();
    }
}