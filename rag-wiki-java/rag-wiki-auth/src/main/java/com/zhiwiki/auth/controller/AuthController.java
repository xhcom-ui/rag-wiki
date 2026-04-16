package com.zhiwiki.auth.controller;

import com.zhiwiki.auth.dto.ChangePasswordRequest;
import com.zhiwiki.auth.dto.LoginRequest;
import com.zhiwiki.auth.dto.LoginResponse;
import com.zhiwiki.auth.dto.UserInfoResponse;
import com.zhiwiki.auth.dto.UserPermissionsResponse;
import com.zhiwiki.auth.service.AuthService;
import com.zhiwiki.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "登录、登出、Token管理")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "用户登出")
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }

    @GetMapping("/current")
    @Operation(summary = "获取当前登录用户信息")
    public Result<UserInfoResponse> currentUser() {
        return Result.success(authService.getCurrentUserInfo());
    }

    @PutMapping("/password")
    @Operation(summary = "修改密码")
    public Result<Void> changePassword(@RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return Result.success();
    }

    @GetMapping("/permissions")
    @Operation(summary = "获取当前用户权限列表")
    public Result<UserPermissionsResponse> getCurrentPermissions() {
        return Result.success(authService.getCurrentUserPermissions());
    }
}
