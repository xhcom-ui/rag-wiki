package com.zhiwiki.auth.controller;

import com.zhiwiki.auth.dto.LoginResponse;
import com.zhiwiki.auth.service.OAuth2Service;
import com.zhiwiki.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * OAuth2/SSO单点登录控制器
 */
@RestController
@RequestMapping("/api/auth/oauth2")
@RequiredArgsConstructor
@Tag(name = "OAuth2单点登录", description = "企业微信/钉钉/通用OAuth2.0登录")
public class OAuth2Controller {

    private final OAuth2Service oauth2Service;

    @GetMapping("/authorize")
    @Operation(summary = "获取OAuth2授权URL")
    public Result<String> getAuthorizationUrl(@RequestParam String provider) {
        String authUrl = oauth2Service.getAuthorizationUrl(provider);
        return Result.success(authUrl);
    }

    @GetMapping("/callback")
    @Operation(summary = "OAuth2回调处理")
    public Result<LoginResponse> callback(
            @RequestParam String provider,
            @RequestParam String code,
            @RequestParam String state) {
        LoginResponse response = oauth2Service.handleCallback(provider, code, state);
        return Result.success(response);
    }

    @PostMapping("/bind")
    @Operation(summary = "绑定OAuth2账号到已有用户")
    public Result<Void> bindAccount(
            @RequestParam String userId,
            @RequestParam String provider,
            @RequestParam String code) {
        oauth2Service.bindOAuth2Account(userId, provider, code);
        return Result.success();
    }
}
