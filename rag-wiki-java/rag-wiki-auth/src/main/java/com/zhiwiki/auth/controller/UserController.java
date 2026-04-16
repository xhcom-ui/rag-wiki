package com.zhiwiki.auth.controller;

import com.zhiwiki.auth.dto.UserCreateRequest;
import com.zhiwiki.auth.entity.User;
import com.zhiwiki.auth.service.UserService;
import com.zhiwiki.common.model.PageRequest;
import com.zhiwiki.common.model.PageResult;
import com.zhiwiki.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理控制器
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户CRUD、状态管理")
public class UserController {

    private final UserService userService;

    @PostMapping
    @Operation(summary = "创建用户")
    public Result<User> createUser(@Valid @RequestBody UserCreateRequest request) {
        return Result.success(userService.createUser(request));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "根据userId查询用户")
    public Result<User> getUser(@PathVariable String userId) {
        return Result.success(userService.getUserByUserId(userId));
    }

    @PutMapping
    @Operation(summary = "更新用户")
    public Result<User> updateUser(@Valid @RequestBody User user) {
        return Result.success(userService.updateUser(user));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户")
    public Result<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.success();
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询用户")
    public Result<PageResult<User>> pageUsers(PageRequest pageRequest,
                                               @RequestParam(required = false) String username,
                                               @RequestParam(required = false) Integer status) {
        return Result.success(userService.pageUsers(pageRequest, username, status));
    }

    @PutMapping("/{id}/status/{status}")
    @Operation(summary = "启用/禁用用户")
    public Result<Void> toggleStatus(@PathVariable Long id, @PathVariable Integer status) {
        userService.toggleUserStatus(id, status);
        return Result.success();
    }
}
