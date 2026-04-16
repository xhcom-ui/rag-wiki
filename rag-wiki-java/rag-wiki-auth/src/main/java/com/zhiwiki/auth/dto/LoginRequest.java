package com.zhiwiki.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求DTO
 */
@Data
@Schema(description = "登录请求")
public class LoginRequest {

    @Schema(description = "用户名", example = "admin")
    @NotBlank(message = "用户名不能为空")
    private String username;

    @Schema(description = "密码", example = "admin123")
    @NotBlank(message = "密码不能为空")
    private String password;

    @Schema(description = "验证码Key")
    private String captchaKey;

    @Schema(description = "验证码")
    private String captchaCode;

    @Schema(description = "登录类型 PASSWORD/OAUTH2/SMS")
    private String loginType = "PASSWORD";
}
