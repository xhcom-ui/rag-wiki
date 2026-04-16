package com.zhiwiki.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 安全告警规则请求DTO
 */
@Data
@Schema(description = "安全告警规则请求")
public class SecurityAlertRuleRequest {

    @Schema(description = "规则名称", required = true)
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 100, message = "规则名称不能超过100字")
    private String ruleName;

    @Schema(description = "告警类型: HIGH_FREQUENCY_QUERY/UNAUTHORIZED_ACCESS/OFF_HOURS_ACCESS/CROSS_DEPT_ACCESS/SENSITIVE_DOC_ACCESS", required = true)
    @NotBlank(message = "告警类型不能为空")
    @Pattern(regexp = "^(HIGH_FREQUENCY_QUERY|UNAUTHORIZED_ACCESS|OFF_HOURS_ACCESS|CROSS_DEPT_ACCESS|SENSITIVE_DOC_ACCESS)$",
             message = "告警类型不合法")
    private String alertType;

    @Schema(description = "告警级别: LOW/MEDIUM/HIGH/CRITICAL", required = true)
    @NotBlank(message = "告警级别不能为空")
    @Pattern(regexp = "^(LOW|MEDIUM|HIGH|CRITICAL)$", message = "告警级别不合法")
    private String alertLevel;

    @Schema(description = "规则描述")
    @Size(max = 500, message = "规则描述不能超过500字")
    private String description;

    @Schema(description = "触发阈值", required = true)
    @NotNull(message = "触发阈值不能为空")
    @Min(value = 1, message = "触发阈值最小为1")
    @Max(value = 10000, message = "触发阈值最大为10000")
    private Integer threshold;

    @Schema(description = "时间窗口(秒)", required = true)
    @NotNull(message = "时间窗口不能为空")
    @Min(value = 10, message = "时间窗口最小为10秒")
    @Max(value = 86400, message = "时间窗口最大为86400秒(24小时)")
    private Integer timeWindow;

    @Schema(description = "是否启用 0-禁用 1-启用")
    private Integer enabled = 1;

    @Schema(description = "通知渠道，逗号分隔: log,webhook,email,sms")
    @Size(max = 200, message = "通知渠道不能超过200字")
    private String notificationChannels;

    @Schema(description = "Webhook地址")
    @Size(max = 500, message = "Webhook地址不能超过500字")
    private String webhookUrl;

    @Schema(description = "通知邮箱，逗号分隔")
    @Size(max = 500, message = "通知邮箱不能超过500字")
    private String notifyEmails;
}
