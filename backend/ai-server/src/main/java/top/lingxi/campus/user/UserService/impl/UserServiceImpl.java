package top.lingxi.campus.user.UserService.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import top.lingxi.campus.common.constant.JwtClaimsConstant;
import top.lingxi.campus.common.context.BaseContext;
import top.lingxi.campus.infra.security.JwtProperties;
import top.lingxi.campus.user.UserMapper.UserMapper;
import top.lingxi.campus.user.UserService.UserService;
import top.lingxi.campus.common.utils.JwtUtil;
import top.lingxi.campus.domain.user.dto.SysUserLoginDTO;
import top.lingxi.campus.domain.user.po.SysUser;
import top.lingxi.campus.domain.user.vo.SysUserLoginVO;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    private final JwtProperties jwtProperties;  // ← 注入统一的配置


    /** JWT 秘钥，从配置读取，默认给一个兜底值（仅开发用） */
    @Value("${jwt.secret-key:haze-ai-hub-secret-key-default}")
    private String secretKey;

    /** Token 有效期，默认 7 天（604800000 毫秒） */
    @Value("${jwt.ttl-millis:604800000}")
    private long ttlMillis;

    @Override
    public SysUserLoginVO login(SysUserLoginDTO loginDTO) {
        String username = loginDTO.getUsername();

        // 1. 查用户
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, username);
        SysUser user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw new IllegalArgumentException("用户名不存在");
        }

        // 2. 校验状态：1=正常，0=禁用
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new IllegalArgumentException("账号已禁用");
        }

        // 3. 校验密码（注意：你现在是明文比对，测试阶段没问题）
        if (!user.getPassword().equals(loginDTO.getPassword())) {
            throw new IllegalArgumentException("密码错误");
        }

        // 4. 生成 JWT（claims 里放 userId、username、roleType，后续拦截器解析用）
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());
        claims.put(JwtClaimsConstant.USERNAME, user.getUsername());
        claims.put(JwtClaimsConstant.ROLE_TYPE, user.getRoleType());

        String token = JwtUtil.createJWT(
                jwtProperties.getUserSecretKey(),  // ← 用同一个密钥
                jwtProperties.getUserTtl(),         // ← 用同一个有效期
                claims
        );

        // 5. 组装返回
        SysUserLoginVO vo = new SysUserLoginVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setRoleType(user.getRoleType());
        vo.setToken(token);

        return vo;
    }

    //获取信息
    @Override
    public SysUser info() {
        Long userId= BaseContext.getCurrentId();
        return userMapper.selectById(userId);
    }
}

