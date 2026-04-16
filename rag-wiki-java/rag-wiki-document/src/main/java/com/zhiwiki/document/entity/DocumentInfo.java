package com.zhiwiki.document.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiwiki.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("document_info")
@Schema(description = "文档主表")
public class DocumentInfo extends BaseEntity {

    @TableField("document_id")
    private String documentId;

    @TableField("space_id")
    private String spaceId;

    @TableField("parent_id")
    private String parentId;

    @TableField("document_name")
    private String documentName;

    @TableField("document_type")
    private String documentType;

    @TableField("file_path")
    private String filePath;

    @TableField("file_size")
    private Long fileSize;

    @TableField("version")
    private String version;

    @TableField("creator_id")
    private String creatorId;

    @TableField("last_editor_id")
    private String lastEditorId;

    @TableField("status")
    private Integer status;

    @TableField("is_folder")
    private Integer isFolder;

    @TableField("security_level")
    private Integer securityLevel;

    @TableField("tenant_id")
    private String tenantId;
}
