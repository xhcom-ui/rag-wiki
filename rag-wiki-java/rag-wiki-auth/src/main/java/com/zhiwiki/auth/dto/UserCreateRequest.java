package com.zhiwiki.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建用户请求DTO
 */
@Data
@Schema(description = "创建用户请求")
public class UserCreateRequest {

    @Schema(description = "用户名", required = true)
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度必须在3-50之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String username;

    @Schema(description = "密码", required = true)
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度必须在6-20位之间")
    private String password;

    @Schema(description = "真实姓名", required = true)
    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 50, message = "真实姓名不能超过50字")
    private String realName;

    @Schema(description = "邮箱")
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱不能超过100字")
    private String email;

    @Schema(description = "手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Schema(description = "所属部门ID")
    private Long deptId;

    @Schema(description = "安全等级 1-公开 2-内部 3-敏感 4-机密")
    @NotNull(message = "安全等级不能为空")
    @Min(value = 1, message = "安全等级最小为1")
    @Max(value = 4, message = "安全等级最大为4")
    private Integer securityLevel;

    @Schema(description = "租户ID")
    @Size(max = 64, message = "租户ID不能超过64字")
    private String tenantId;

    @Schema(description = "角色ID列表")
    private java.util.List<Long> roleIds;
}
