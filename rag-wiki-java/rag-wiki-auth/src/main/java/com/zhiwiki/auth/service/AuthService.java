package com.zhiwiki.auth.service;

import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiwiki.auth.dto.ChangePasswordRequest;
import com.zhiwiki.auth.dto.LoginRequest;
import com.zhiwiki.auth.dto.LoginResponse;
import com.zhiwiki.auth.dto.UserInfoResponse;
import com.zhiwiki.auth.dto.UserPermissionsResponse;
import com.zhiwiki.auth.entity.Dept;
import com.zhiwiki.auth.entity.Menu;
import com.zhiwiki.auth.entity.Role;
import com.zhiwiki.auth.entity.User;
import com.zhiwiki.auth.entity.UserRole;
import com.zhiwiki.auth.mapper.DeptMapper;
import com.zhiwiki.auth.mapper.MenuMapper;
import com.zhiwiki.auth.mapper.RoleMapper;
import com.zhiwiki.auth.mapper.UserMapper;
import com.zhiwiki.auth.mapper.UserRoleMapper;
import com.zhiwiki.common.context.UserPermissionContext;
import com.zhiwiki.common.exception.BusinessException;
import com.zhiwiki.common.model.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 认证服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final MenuMapper menuMapper;
    private final DeptMapper deptMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final PermissionChangeService permissionChangeService;

    private static final String PERMISSION_CACHE_PREFIX = "user:permissions:";

    /**
     * 用户登录
     */
    public LoginResponse login(LoginRequest request) {
        // 1. 查询用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername())
        );
        if (user == null) {
            throw new BusinessException(ResultCode.AUTH_LOGIN_FAILED);
        }

        // 2. 校验密码
        if (!BCrypt.checkpw(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.AUTH_LOGIN_FAILED);
        }

        // 3. 校验状态
        if (user.getStatus() == 0) {
            throw new BusinessException(ResultCode.AUTH_ACCOUNT_DISABLED);
        }

        // 4. Sa-Token登录
        StpUtil.login(user.getUserId());

        // 5. 缓存用户权限信息到Session
        UserPermissionContext permCtx = buildUserPermissionContext(user);
        StpUtil.getSession().set("userId", user.getUserId());
        StpUtil.getSession().set("username", user.getUsername());
        StpUtil.getSession().set("deptId", user.getDeptId());
        StpUtil.getSession().set("securityLevel", user.getSecurityLevel());
        StpUtil.getSession().set("tenantId", user.getTenantId());
        StpUtil.getSession().set("roleIds", permCtx.getRoleIds());
        // 存储当前权限版本号（用于网关层权限变更检测）
        StpUtil.getSession().set("permissionVersion", getPermissionVersion(user.getUserId()));

        // 6. 缓存权限信息到Redis
        cacheUserPermission(permCtx);

        // 7. 构建返回
        LoginResponse response = new LoginResponse();
        response.setToken(StpUtil.getTokenValue());
        response.setUserId(user.getUserId());
        response.setUsername(user.getUsername());
        response.setRealName(user.getRealName());
        response.setSecurityLevel(user.getSecurityLevel());

        return response;
    }

    /**
     * 用户登出
     */
    public void logout() {
        String userId = StpUtil.getLoginIdAsString();
        StpUtil.logout();
        // 清除权限缓存
        redisTemplate.delete(PERMISSION_CACHE_PREFIX + userId);
    }

    /**
     * 获取当前用户权限上下文
     */
    public UserPermissionContext getCurrentUserPermission() {
        String userId = StpUtil.getLoginIdAsString();
        // 先从缓存获取
        Object cached = redisTemplate.opsForValue().get(PERMISSION_CACHE_PREFIX + userId);
        if (cached instanceof UserPermissionContext) {
            return (UserPermissionContext) cached;
        }
        // 缓存未命中，从数据库构建
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUserId, userId)
        );
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        UserPermissionContext ctx = buildUserPermissionContext(user);
        cacheUserPermission(ctx);
        return ctx;
    }

    /**
     * 构建用户权限上下文
     */
    private UserPermissionContext buildUserPermissionContext(User user) {
        UserPermissionContext ctx = new UserPermissionContext();
        ctx.setUserId(user.getUserId());
        ctx.setUsername(user.getUsername());
        ctx.setRealName(user.getRealName());
        ctx.setDeptId(String.valueOf(user.getDeptId()));
        ctx.setSecurityLevel(user.getSecurityLevel());
        ctx.setActive(user.getStatus() == 1);
        ctx.setTenantId(user.getTenantId());

        // 查询用户角色
        List<UserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, user.getUserId())
        );
        List<String> roleIds = userRoles.stream().map(UserRole::getRoleId).collect(Collectors.toList());
        ctx.setRoleIds(roleIds);

        if (!roleIds.isEmpty()) {
            List<Role> roles = roleMapper.selectList(
                    new LambdaQueryWrapper<Role>().in(Role::getRoleId, roleIds)
            );
            Set<String> roleCodes = roles.stream().map(Role::getRoleCode).collect(Collectors.toSet());
            ctx.setRoleCodes(roleCodes);
            // 最高角色等级
            int maxRoleLevel = roles.stream().mapToInt(Role::getRoleLevel).max().orElse(0);
            ctx.setMaxRoleLevel(maxRoleLevel);
        } else {
            ctx.setRoleCodes(new HashSet<>());
            ctx.setMaxRoleLevel(0);
        }

        return ctx;
    }

    /**
     * 缓存用户权限
     */
    private void cacheUserPermission(UserPermissionContext ctx) {
        redisTemplate.opsForValue().set(
                PERMISSION_CACHE_PREFIX + ctx.getUserId(),
                ctx, 5, TimeUnit.MINUTES
        );
    }

    /**
     * 获取当前用户详细信息
     */
    public UserInfoResponse getCurrentUserInfo() {
        String userId = StpUtil.getLoginIdAsString();
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUserId, userId)
        );
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 查询用户角色
        List<UserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId)
        );
        List<String> roleIds = userRoles.stream()
                .map(UserRole::getRoleId)
                .collect(Collectors.toList());

        List<UserInfoResponse.RoleInfo> roleInfos = new ArrayList<>();
        if (!roleIds.isEmpty()) {
            List<Role> roles = roleMapper.selectList(
                    new LambdaQueryWrapper<Role>().in(Role::getRoleId, roleIds)
            );
            roleInfos = roles.stream()
                    .map(r -> UserInfoResponse.RoleInfo.builder()
                            .roleId(r.getRoleId())
                            .roleName(r.getRoleName())
                            .roleCode(r.getRoleCode())
                            .roleLevel(r.getRoleLevel())
                            .build())
                    .collect(Collectors.toList());
        }

        // 获取安全等级名称
        String securityLevelName = getSecurityLevelName(user.getSecurityLevel());

        return UserInfoResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .deptId(String.valueOf(user.getDeptId()))
                .roles(roleInfos)
                .securityLevel(user.getSecurityLevel())
                .securityLevelName(securityLevelName)
                .avatar(user.getAvatar())
                .status(user.getStatus())
                .build();
    }

    /**
     * 修改密码
     */
    public void changePassword(ChangePasswordRequest request) {
        // 验证新密码和确认密码一致
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "新密码与确认密码不一致");
        }

        String userId = StpUtil.getLoginIdAsString();
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUserId, userId)
        );
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 验证原密码
        if (!BCrypt.checkpw(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.AUTH_LOGIN_FAILED, "原密码错误");
        }

        // 更新密码
        user.setPassword(BCrypt.hashpw(request.getNewPassword(), BCrypt.gensalt()));
        userMapper.updateById(user);

        // 清除权限缓存，强制重新登录
        redisTemplate.delete(PERMISSION_CACHE_PREFIX + userId);
        StpUtil.logout();
    }

    /**
     * 获取当前用户权限列表
     */
    public UserPermissionsResponse getCurrentUserPermissions() {
        String userId = StpUtil.getLoginIdAsString();
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUserId, userId)
        );
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        
        UserPermissionContext permCtx = getCurrentUserPermission();

        // 获取用户菜单权限
        List<Menu> menus = getUserMenus(permCtx.getRoleIds());
        List<UserPermissionsResponse.MenuPermission> menuTree = buildMenuTree(menus);

        // 获取按钮权限标识
        Set<String> permissions = menus.stream()
                .filter(m -> m.getMenuType() == 2 && m.getPermission() != null)
                .map(Menu::getPermission)
                .collect(Collectors.toSet());

        return UserPermissionsResponse.builder()
                .userId(userId)
                .menus(menuTree)
                .permissions(permissions)
                .securityLevel(permCtx.getSecurityLevel())
                .dataScopeDeptIds(getDataScopeDeptIds(user, permCtx))
                .build();
    }
    
    /**
     * 获取用户数据权限范围部门ID列表
     */
    private Set<String> getDataScopeDeptIds(User user, UserPermissionContext permCtx) {
        Set<String> deptIds = new HashSet<>();
        
        // 如果是超级管理员，返回空集合表示全部数据权限
        if (permCtx.getRoleCodes() != null && 
                (permCtx.getRoleCodes().contains("super_admin") || permCtx.getRoleCodes().contains("admin"))) {
            return deptIds; // 空集合表示全部权限
        }
        
        // 添加用户所在部门
        if (user.getDeptId() != null) {
            deptIds.add(String.valueOf(user.getDeptId()));
        }
        
        // 根据角色等级递归查询子部门ID
        if (permCtx.getMaxRoleLevel() >= 3 && user.getDeptId() != null) {
            List<String> childDeptIds = getChildDeptIds(String.valueOf(user.getDeptId()));
            deptIds.addAll(childDeptIds);
        }
        
        return deptIds;
    }

    /**
     * 递归查询子部门ID
     */
    private List<String> getChildDeptIds(String parentDeptId) {
        try {
            List<String> result = new java.util.ArrayList<>();
            Long parentId = Long.parseLong(parentDeptId);
            collectChildDepts(parentId, result, 0);
            return result;
        } catch (NumberFormatException e) {
            log.warn("部门ID格式错误: parentDeptId={}", parentDeptId);
            return java.util.Collections.emptyList();
        } catch (Exception e) {
            log.warn("查询子部门失败: parentDeptId={}, error={}", parentDeptId, e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    /**
     * 递归收集子部门ID
     */
    private void collectChildDepts(Long parentId, List<String> result, int depth) {
        if (depth >= 5) return; // 防止无限递归，最多5层

        List<Dept> children = deptMapper.selectChildDepts(parentId);
        if (children == null || children.isEmpty()) return;

        for (Dept child : children) {
            result.add(child.getDeptId());
            // 递归查询子部门的子部门
            collectChildDepts(Long.valueOf(child.getDeptId()), result, depth + 1);
        }
    }

    /**
     * 获取用户可访问的菜单
     */
    private List<Menu> getUserMenus(List<String> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return new ArrayList<>();
        }
        // 查询角色关联的菜单
        return menuMapper.selectList(
                new LambdaQueryWrapper<Menu>()
                        .eq(Menu::getStatus, 1)
                        .eq(Menu::getIsDeleted, 0)
                        .in(Menu::getMenuType, 0, 1) // 目录和菜单
                        .orderByAsc(Menu::getSort)
        );
    }

    /**
     * 构建菜单树
     */
    private List<UserPermissionsResponse.MenuPermission> buildMenuTree(List<Menu> menus) {
        // 找出顶级菜单
        List<UserPermissionsResponse.MenuPermission> roots = menus.stream()
                .filter(m -> m.getParentId() == null || m.getParentId() == 0)
                .map(m -> UserPermissionsResponse.MenuPermission.builder()
                        .menuId(m.getMenuId())
                        .menuName(m.getMenuName())
                        .path(m.getPath())
                        .component(m.getComponent())
                        .icon(m.getIcon())
                        .parentId(m.getParentId())
                        .sort(m.getSort())
                        .build())
                .collect(Collectors.toList());

        // 递归构建子菜单
        for (UserPermissionsResponse.MenuPermission root : roots) {
            buildChildren(root, menus);
        }

        return roots;
    }

    private void buildChildren(UserPermissionsResponse.MenuPermission parent, List<Menu> menus) {
        List<UserPermissionsResponse.MenuPermission> children = menus.stream()
                .filter(m -> parent.getMenuId() != null && parent.getMenuId().equals(m.getParentId()))
                .map(m -> UserPermissionsResponse.MenuPermission.builder()
                        .menuId(m.getMenuId())
                        .menuName(m.getMenuName())
                        .path(m.getPath())
                        .component(m.getComponent())
                        .icon(m.getIcon())
                        .parentId(m.getParentId())
                        .sort(m.getSort())
                        .build())
                .collect(Collectors.toList());

        if (!children.isEmpty()) {
            parent.setChildren(children);
            for (UserPermissionsResponse.MenuPermission child : children) {
                buildChildren(child, menus);
            }
        }
    }

    /**
     * 获取安全等级名称
     */
    private String getSecurityLevelName(Integer level) {
        if (level == null) {
            return "公开";
        }
        switch (level) {
            case 1: return "公开";
            case 2: return "内部";
            case 3: return "敏感";
            case 4: return "机密";
            default: return "公开";
        }
    }

    /**
     * 获取用户当前权限版本号
     */
    private long getPermissionVersion(String userId) {
        return permissionChangeService.getPermissionVersion(userId);
    }
}
