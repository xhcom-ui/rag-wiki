package com.zhiwiki.document.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建知识库空间请求DTO
 */
@Data
@Schema(description = "创建知识库空间请求")
public class SpaceCreateRequest {

    @Schema(description = "空间名称", required = true)
    @NotBlank(message = "空间名称不能为空")
    @Size(min = 2, max = 100, message = "空间名称长度必须在2-100之间")
    private String spaceName;

    @Schema(description = "空间描述")
    @Size(max = 500, message = "空间描述不能超过500字")
    private String description;

    @Schema(description = "空间图标")
    @Size(max = 200, message = "图标URL不能超过200字")
    private String icon;

    @Schema(description = "安全等级 1-公开 2-内部 3-敏感 4-机密")
    @NotNull(message = "安全等级不能为空")
    @Min(value = 1, message = "安全等级最小为1")
    @Max(value = 4, message = "安全等级最大为4")
    private Integer securityLevel;

    @Schema(description = "是否公开 0-私有 1-公开")
    private Integer isPublic = 0;

    @Schema(description = "存储配额(MB)，0表示不限制")
    @Min(value = 0, message = "存储配额不能为负数")
    private Long storageQuotaMb = 0L;

    @Schema(description = "允许访问的部门ID列表")
    private java.util.List<String> allowedDeptIds;

    @Schema(description = "允许访问的角色ID列表")
    private java.util.List<String> allowedRoleIds;
}
