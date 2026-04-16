package com.zhiwiki.auth.controller;

import com.zhiwiki.auth.entity.Tenant;
import com.zhiwiki.auth.service.TenantService;
import com.zhiwiki.common.model.PageRequest;
import com.zhiwiki.common.model.PageResult;
import com.zhiwiki.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 租户管理控制器
 */
@RestController
@RequestMapping("/api/tenant")
@RequiredArgsConstructor
@Tag(name = "租户管理", description = "多租户隔离、租户CRUD、隔离级别切换")
public class TenantController {

    private final TenantService tenantService;

    @PostMapping
    @Operation(summary = "创建租户")
    public Result<Tenant> createTenant(@Valid @RequestBody TenantCreateRequest request) {
        Tenant tenant = new Tenant();
        tenant.setTenantName(request.getTenantName());
        tenant.setTenantCode(request.getTenantCode());
        tenant.setContactName(request.getContactName());
        tenant.setContactPhone(request.getContactPhone());
        tenant.setContactEmail(request.getContactEmail());
        tenant.setIsolationLevel(request.getIsolationLevel());
        tenant.setDomain(request.getDomain());
        tenant.setStorageQuotaMb(request.getStorageQuotaMb());
        tenant.setMaxUsers(request.getMaxUsers());
        tenant.setMaxSpaces(request.getMaxSpaces());
        tenant.setExpireTime(request.getExpireTime());
        return Result.success(tenantService.createTenant(tenant));
    }

    @GetMapping("/{tenantId}")
    @Operation(summary = "获取租户详情")
    public Result<Tenant> getTenant(
            @PathVariable @Parameter(description = "租户ID") String tenantId) {
        return Result.success(tenantService.getTenant(tenantId));
    }

    @PutMapping
    @Operation(summary = "更新租户")
    public Result<Tenant> updateTenant(@Valid @RequestBody Tenant tenant) {
        return Result.success(tenantService.updateTenant(tenant));
    }

    @PutMapping("/{tenantId}/isolation")
    @Operation(summary = "切换租户隔离级别", description = "⚠️ 影响重大，需要数据迁移配合")
    public Result<Tenant> switchIsolation(
            @PathVariable @Parameter(description = "租户ID") String tenantId,
            @RequestParam @Parameter(description = "新的隔离级别") String isolationLevel) {
        return Result.success(tenantService.switchIsolationLevel(tenantId, isolationLevel));
    }

    @PutMapping("/{tenantId}/status/{status}")
    @Operation(summary = "启用/禁用租户", description = "禁用后该租户所有用户将被强制下线")
    public Result<Void> toggleStatus(
            @PathVariable @Parameter(description = "租户ID") String tenantId,
            @PathVariable @Parameter(description = "状态 0-禁用 1-启用") Integer status) {
        tenantService.toggleTenantStatus(tenantId, status);
        return Result.success();
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询租户")
    public Result<PageResult<Tenant>> pageTenants(
            PageRequest pageRequest,
            @RequestParam(required = false) @Parameter(description = "租户名称") String tenantName,
            @RequestParam(required = false) @Parameter(description = "状态") Integer status) {
        return Result.success(tenantService.pageTenants(pageRequest, tenantName, status));
    }

    /**
     * 创建租户请求DTO
     */
    @Data
    public static class TenantCreateRequest {
        @NotBlank(message = "租户名称不能为空")
        @Size(max = 100, message = "租户名称不能超过100字")
        private String tenantName;

        @NotBlank(message = "租户编码不能为空")
        @Size(min = 2, max = 50, message = "租户编码长度2-50")
        @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "租户编码只能包含字母数字下划线连字符")
        private String tenantCode;

        private String contactName;
        private String contactPhone;
        private String contactEmail;

        @Pattern(regexp = "^(metadata_filter|partition|collection)$", message = "隔离级别不合法")
        private String isolationLevel = "metadata_filter";

        @Size(max = 200, message = "域名不能超过200字")
        private String domain;

        private Long storageQuotaMb;
        private Integer maxUsers;
        private Integer maxSpaces;
        private String expireTime;
    }
}
