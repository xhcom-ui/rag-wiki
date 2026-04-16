package com.zhiwiki.document.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiwiki.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("document_parse_task")
@Schema(description = "文档解析任务")
public class DocumentParseTask extends BaseEntity {

    @TableField("task_id")
    private String taskId;

    @TableField("document_id")
    private String documentId;

    @TableField("file_name")
    private String fileName;

    @TableField("file_type")
    private String fileType;

    @TableField("space_id")
    private String spaceId;

    @TableField("status")
    private String status;

    @TableField("parser_type")
    private String parserType;

    @TableField("quality_score")
    private Float qualityScore;

    @TableField("chunk_count")
    private Integer chunkCount;

    @TableField("table_count")
    private Integer tableCount;

    @TableField("error_message")
    private String errorMessage;
}
