package com.zhiwiki.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

/**
 * 用户权限响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPermissionsResponse {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 菜单权限列表
     */
    private List<MenuPermission> menus;

    /**
     * 按钮权限标识列表
     */
    private Set<String> permissions;

    /**
     * 数据权限部门ID列表
     */
    private Set<String> dataScopeDeptIds;

    /**
     * 安全等级
     */
    private Integer securityLevel;

    /**
     * 菜单权限
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuPermission {
        /**
         * 菜单ID
         */
        private Long menuId;

        /**
         * 菜单名称
         */
        private String menuName;

        /**
         * 路由路径
         */
        private String path;

        /**
         * 组件路径
         */
        private String component;

        /**
         * 图标
         */
        private String icon;

        /**
         * 父菜单ID
         */
        private Long parentId;

        /**
         * 排序
         */
        private Integer sort;

        /**
         * 子菜单
         */
        private List<MenuPermission> children;
    }
}
