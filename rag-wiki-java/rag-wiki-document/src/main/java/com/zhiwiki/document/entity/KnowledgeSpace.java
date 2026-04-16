package com.zhiwiki.document.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiwiki.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_space")
@Schema(description = "知识库空间")
public class KnowledgeSpace extends BaseEntity {

    @TableField("space_id")
    private String spaceId;

    @TableField("space_name")
    private String spaceName;

    @TableField("description")
    private String description;

    @TableField("space_type")
    private Integer spaceType;

    @TableField("owner_id")
    private String ownerId;

    @TableField("dept_id")
    private String deptId;

    @TableField("storage_quota")
    private Long storageQuota;

    @TableField("status")
    private Integer status;

    @TableField("tenant_id")
    private String tenantId;
}
