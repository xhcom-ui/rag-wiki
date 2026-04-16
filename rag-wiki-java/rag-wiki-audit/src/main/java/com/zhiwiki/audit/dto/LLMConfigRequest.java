package com.zhiwiki.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * LLM配置请求DTO
 */
@Data
@Schema(description = "LLM模型配置请求")
public class LLMConfigRequest {

    @Schema(description = "配置名称", required = true)
    @NotBlank(message = "配置名称不能为空")
    @Size(max = 128, message = "配置名称不能超过128字")
    private String configName;

    @Schema(description = "模型提供商: openai/deepseek/qwen/gemini/glm/kimi/local", required = true)
    @NotBlank(message = "模型提供商不能为空")
    private String provider;

    @Schema(description = "模型名称", required = true)
    @NotBlank(message = "模型名称不能为空")
    @Size(max = 64, message = "模型名称不能超过64字")
    private String modelName;

    @Schema(description = "API密钥")
    @Size(max = 255, message = "API密钥不能超过255字")
    private String apiKey;

    @Schema(description = "API地址")
    @Size(max = 255, message = "API地址不能超过255字")
    private String apiBase;

    @Schema(description = "温度参数 0-2，默认0.7")
    @Min(value = 0, message = "温度参数不能小于0")
    @Max(value = 2, message = "温度参数不能大于2")
    private Double temperature = 0.7;

    @Schema(description = "最大token数，默认4096")
    @Min(value = 1, message = "最大token数不能小于1")
    @Max(value = 128000, message = "最大token数不能超过128000")
    private Integer maxTokens = 4096;

    @Schema(description = "是否默认配置")
    private Boolean isDefault = false;

    @Schema(description = "是否启用")
    private Boolean isEnabled = true;

    @Schema(description = "优先级(重试顺序)")
    private Integer priority = 0;

    @Schema(description = "配置描述")
    @Size(max = 512, message = "描述不能超过512字")
    private String remark;

    @Schema(description = "扩展配置JSON")
    private String extraConfig;

    @Schema(description = "租户ID")
    private String tenantId;
}
