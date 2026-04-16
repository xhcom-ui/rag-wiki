package com.zhiwiki.auth.controller;

import com.zhiwiki.auth.entity.Role;
import com.zhiwiki.auth.mapper.RoleMapper;
import com.zhiwiki.common.exception.BusinessException;
import com.zhiwiki.common.model.Result;
import com.zhiwiki.common.model.ResultCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理控制器
 */
@RestController
@RequestMapping("/api/role")
@RequiredArgsConstructor
@Tag(name = "角色管理", description = "角色CRUD、权限分配")
public class RoleController {

    private final RoleMapper roleMapper;

    @PostMapping
    @Operation(summary = "创建角色")
    public Result<Role> createRole(@RequestBody Role role) {
        roleMapper.insert(role);
        return Result.success(role);
    }

    @GetMapping("/{roleId}")
    @Operation(summary = "根据roleId查询角色")
    public Result<Role> getRole(@PathVariable String roleId) {
        Role role = roleMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Role>()
                .eq(Role::getRoleId, roleId)
        );
        if (role == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
        }
        return Result.success(role);
    }

    @GetMapping("/list")
    @Operation(summary = "查询所有角色")
    public Result<List<Role>> listRoles() {
        List<Role> roles = roleMapper.selectList(null);
        return Result.success(roles);
    }

    @PutMapping
    @Operation(summary = "更新角色")
    public Result<Role> updateRole(@RequestBody Role role) {
        roleMapper.updateById(role);
        return Result.success(role);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除角色")
    public Result<Void> deleteRole(@PathVariable Long id) {
        roleMapper.deleteById(id);
        return Result.success();
    }
}
