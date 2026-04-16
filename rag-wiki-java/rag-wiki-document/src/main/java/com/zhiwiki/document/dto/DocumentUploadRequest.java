package com.zhiwiki.document.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 文档上传请求DTO
 */
@Data
@Schema(description = "文档上传请求")
public class DocumentUploadRequest {

    @Schema(description = "知识库空间ID", required = true)
    @NotBlank(message = "知识库空间ID不能为空")
    private String spaceId;

    @Schema(description = "文档名称", required = true)
    @NotBlank(message = "文档名称不能为空")
    @Size(max = 200, message = "文档名称不能超过200字")
    private String documentName;

    @Schema(description = "文档类型: PDF/WORD/EXCEL/PPT/TXT/HTML/MARKDOWN")
    @NotBlank(message = "文档类型不能为空")
    @Pattern(regexp = "^(PDF|WORD|EXCEL|PPT|TXT|HTML|MARKDOWN)$",
             message = "不支持的文档类型，支持: PDF/WORD/EXCEL/PPT/TXT/HTML/MARKDOWN")
    private String documentType;

    @Schema(description = "安全等级 1-公开 2-内部 3-敏感 4-机密")
    @NotNull(message = "安全等级不能为空")
    @Min(value = 1, message = "安全等级最小为1")
    @Max(value = 4, message = "安全等级最大为4")
    private Integer securityLevel;

    @Schema(description = "文件大小(字节)")
    @Min(value = 1, message = "文件大小不能为0")
    @Max(value = 524288000, message = "单文件大小不能超过500MB")
    private Long fileSize;

    @Schema(description = "文件MD5校验值")
    @Size(min = 32, max = 32, message = "MD5校验值长度必须为32")
    private String fileMd5;

    @Schema(description = "标签，逗号分隔")
    @Size(max = 500, message = "标签不能超过500字")
    private String tags;

    @Schema(description = "备注说明")
    @Size(max = 500, message = "备注不能超过500字")
    private String remark;

    @Schema(description = "是否自动发布 0-草稿 1-发布")
    private Integer autoPublish = 0;
}
