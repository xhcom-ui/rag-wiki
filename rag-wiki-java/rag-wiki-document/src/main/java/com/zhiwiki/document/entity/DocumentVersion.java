package com.zhiwiki.document.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiwiki.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("document_version")
@Schema(description = "文档版本")
public class DocumentVersion extends BaseEntity {

    @TableField("version_id")
    private String versionId;

    @TableField("document_id")
    private String documentId;

    @TableField("version")
    private String version;

    @TableField("content")
    private String content;

    @TableField("change_log")
    private String changeLog;

    @TableField("editor_id")
    private String editorId;
}
