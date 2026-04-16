package com.zhiwiki.auth.service;

import com.zhiwiki.auth.entity.RoleMenu;
import com.zhiwiki.auth.entity.UserRole;
import com.zhiwiki.auth.mapper.RoleMenuMapper;
import com.zhiwiki.auth.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色权限服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RolePermissionService {

    private final RoleMenuMapper roleMenuMapper;
    private final UserRoleMapper userRoleMapper;

    /**
     * 保存角色权限
     */
    @Transactional
    public void saveRolePermissions(String roleId, List<String> menuIds) {
        // 1. 删除原有权限
        roleMenuMapper.deleteByRoleId(roleId);
        
        // 2. 插入新权限
        if (menuIds != null && !menuIds.isEmpty()) {
            List<RoleMenu> roleMenus = menuIds.stream()
                    .map(menuId -> {
                        RoleMenu rm = new RoleMenu();
                        rm.setRoleId(roleId);
                        rm.setMenuId(menuId);
                        rm.setCreatedAt(LocalDateTime.now());
                        return rm;
                    })
                    .collect(Collectors.toList());
            
            for (RoleMenu roleMenu : roleMenus) {
                roleMenuMapper.insert(roleMenu);
            }
            log.info("角色权限已更新: roleId={}, menuCount={}", roleId, menuIds.size());
        }
    }

    /**
     * 保存用户角色
     */
    @Transactional
    public void saveUserRoles(String userId, List<String> roleIds) {
        // 1. 删除原有角色关联
        userRoleMapper.deleteByUserId(userId);
        
        // 2. 插入新角色关联
        if (roleIds != null && !roleIds.isEmpty()) {
            List<UserRole> userRoles = roleIds.stream()
                    .map(roleId -> {
                        UserRole ur = new UserRole();
                        ur.setUserId(userId);
                        ur.setRoleId(roleId);
                        ur.setCreatedAt(LocalDateTime.now());
                        return ur;
                    })
                    .collect(Collectors.toList());
            
            for (UserRole userRole : userRoles) {
                userRoleMapper.insert(userRole);
            }
            log.info("用户角色已更新: userId={}, roleCount={}", userId, roleIds.size());
        }
    }
}