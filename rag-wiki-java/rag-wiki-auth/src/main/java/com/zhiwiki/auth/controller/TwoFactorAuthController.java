package com.zhiwiki.auth.controller;

import com.zhiwiki.auth.service.TwoFactorAuthService;
import com.zhiwiki.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 双因素认证(2FA/TOTP)控制器
 */
@RestController
@RequestMapping("/api/auth/2fa")
@RequiredArgsConstructor
@Tag(name = "双因素认证", description = "TOTP双因素认证启用/验证/禁用")
public class TwoFactorAuthController {

    private final TwoFactorAuthService twoFactorAuthService;

    @PostMapping("/enable")
    @Operation(summary = "启用2FA - 生成密钥和二维码URI")
    public Result<Map<String, Object>> enable2FA() {
        String userId = getCurrentUserId();
        TwoFactorAuthService.TwoFactorSetupResponse setup = twoFactorAuthService.enable2FA(userId);
        return Result.success(Map.of(
                "secret", setup.secret(),
                "otpauthUri", setup.otpauthUri(),
                "backupCodes", setup.backupCodes()
        ));
    }

    @PostMapping("/verify")
    @Operation(summary = "验证2FA验证码并正式启用")
    public Result<Boolean> verifyAndEnable(@RequestParam String code) {
        String userId = getCurrentUserId();
        return Result.success(twoFactorAuthService.verifyAndEnable2FA(userId, code));
    }

    @PostMapping("/disable")
    @Operation(summary = "禁用2FA")
    public Result<Boolean> disable2FA(@RequestParam String code) {
        String userId = getCurrentUserId();
        return Result.success(twoFactorAuthService.disable2FA(userId, code));
    }

    @GetMapping("/qrcode")
    @Operation(summary = "获取2FA二维码数据")
    public Result<Map<String, Object>> getQRCode() {
        String userId = getCurrentUserId();
        TwoFactorAuthService.TwoFactorSetupResponse setup = twoFactorAuthService.enable2FA(userId);
        return Result.success(Map.of(
                "otpauthUri", setup.otpauthUri(),
                "secret", setup.secret()
        ));
    }

    @GetMapping("/status")
    @Operation(summary = "查询2FA启用状态")
    public Result<Map<String, Object>> getStatus() {
        String userId = getCurrentUserId();
        boolean enabled = twoFactorAuthService.is2FAEnabled(userId);
        return Result.success(Map.of("enabled", enabled));
    }

    private String getCurrentUserId() {
        return cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
    }
}
