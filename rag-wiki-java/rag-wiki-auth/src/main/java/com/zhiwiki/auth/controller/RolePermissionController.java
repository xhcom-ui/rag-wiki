package com.zhiwiki.auth.controller;

import com.zhiwiki.auth.mapper.RoleMenuMapper;
import com.zhiwiki.auth.mapper.UserRoleMapper;
import com.zhiwiki.auth.service.RolePermissionService;
import com.zhiwiki.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 角色权限管理控制器
 */
@RestController
@RequestMapping("/api/role")
@RequiredArgsConstructor
@Tag(name = "角色权限管理", description = "角色与权限的关联管理")
public class RolePermissionController {

    private final RoleMenuMapper roleMenuMapper;
    private final UserRoleMapper userRoleMapper;
    private final RolePermissionService rolePermissionService;

    /**
     * 获取角色的权限列表
     */
    @GetMapping("/{roleId}/permissions")
    @Operation(summary = "获取角色权限", description = "获取指定角色的权限ID列表")
    public Result<List<String>> getRolePermissions(@PathVariable String roleId) {
        List<String> menuIds = roleMenuMapper.selectMenuIdsByRoleId(roleId);
        return Result.success(menuIds);
    }

    /**
     * 保存角色的权限
     */
    @PutMapping("/{roleId}/permissions")
    @Operation(summary = "保存角色权限", description = "更新角色的权限配置")
    @Transactional
    public Result<Boolean> saveRolePermissions(
            @PathVariable String roleId,
            @RequestBody Map<String, List<String>> request) {
        List<String> permissionIds = request.get("permissionIds");
        rolePermissionService.saveRolePermissions(roleId, permissionIds);
        return Result.success(true);
    }

    /**
     * 获取用户的角色列表
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "获取用户角色", description = "获取指定用户的角色ID列表")
    public Result<List<String>> getUserRoles(@PathVariable String userId) {
        List<String> roleIds = userRoleMapper.selectRoleIdsByUserId(userId);
        return Result.success(roleIds);
    }

    /**
     * 保存用户的角色
     */
    @PutMapping("/user/{userId}")
    @Operation(summary = "保存用户角色", description = "更新用户的角色配置")
    @Transactional
    public Result<Boolean> saveUserRoles(
            @PathVariable String userId,
            @RequestBody Map<String, List<String>> request) {
        List<String> roleIds = request.get("roleIds");
        rolePermissionService.saveUserRoles(userId, roleIds);
        return Result.success(true);
    }
}