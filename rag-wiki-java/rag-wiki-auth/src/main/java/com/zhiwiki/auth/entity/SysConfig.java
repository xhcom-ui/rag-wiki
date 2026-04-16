package com.zhiwiki.auth.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiwiki.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_config")
public class SysConfig extends BaseEntity {

    @TableField("config_key")
    private String configKey;

    @TableField("config_value")
    private String configValue;

    @TableField("config_type")
    private String configType;

    @TableField("config_group")
    private String configGroup;

    @TableField("description")
    private String description;

    @TableField("is_system")
    private Integer isSystem;

    @TableField("is_readonly")
    private Integer isReadonly;

    @TableField("sort_order")
    private Integer sortOrder;
}
