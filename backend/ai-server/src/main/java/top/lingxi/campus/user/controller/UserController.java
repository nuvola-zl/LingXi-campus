package top.lingxi.campus.user.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.lingxi.campus.domain.user.po.SysUser;
import top.lingxi.campus.user.UserService.UserService;
import top.lingxi.campus.common.result.Result;
import top.lingxi.campus.domain.user.dto.SysUserLoginDTO;
import top.lingxi.campus.domain.user.vo.SysUserLoginVO;


@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/login")
    public Result<SysUserLoginVO> login(@RequestBody SysUserLoginDTO sysUserLoginDTO){
        return Result.success(userService.login(sysUserLoginDTO));

    }

    @GetMapping("/me")
    public Result<SysUser> info() {
        return Result.success(userService.info());
    }
}
