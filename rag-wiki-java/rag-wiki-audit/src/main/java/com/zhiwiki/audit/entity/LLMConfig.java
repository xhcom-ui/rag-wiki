package com.zhiwiki.audit.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiwiki.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * LLM模型配置实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_llm_config")
@Schema(description = "LLM模型配置")
public class LLMConfig extends BaseEntity {

    @Schema(description = "配置唯一标识")
    @TableField("config_id")
    private String configId;

    @Schema(description = "配置名称")
    @TableField("config_name")
    private String configName;

    @Schema(description = "模型提供商: openai/deepseek/qwen/gemini/glm/kimi/local")
    @TableField("provider")
    private String provider;

    @Schema(description = "模型名称")
    @TableField("model")
    private String modelName;

    @Schema(description = "API密钥")
    @TableField("api_key")
    private String apiKey;

    @Schema(description = "API地址")
    @TableField("api_base")
    private String apiBase;

    @Schema(description = "温度参数 0-2")
    @TableField("temperature")
    private Double temperature;

    @Schema(description = "最大token数")
    @TableField("max_tokens")
    private Integer maxTokens;

    @Schema(description = "是否默认配置 0-否 1-是")
    @TableField("is_default")
    private Integer isDefault;

    @Schema(description = "是否启用 0-禁用 1-启用")
    @TableField("is_enabled")
    private Integer isEnabled;

    @Schema(description = "优先级(重试顺序)")
    @TableField("priority")
    private Integer priority;

    @Schema(description = "配置描述")
    @TableField("description")
    private String remark;

    @Schema(description = "扩展配置JSON")
    @TableField("extra_config")
    private String extraConfig;

    @Schema(description = "租户ID")
    @TableField("tenant_id")
    private String tenantId;
}
