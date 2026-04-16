package com.zhiwiki.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户信息响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResponse {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 部门ID
     */
    private String deptId;

    /**
     * 部门名称
     */
    private String deptName;

    /**
     * 角色列表
     */
    private List<RoleInfo> roles;

    /**
     * 安全等级: 1-公开 2-内部 3-敏感 4-机密
     */
    private Integer securityLevel;

    /**
     * 安全等级名称
     */
    private String securityLevelName;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 用户状态: 0-禁用 1-启用
     */
    private Integer status;

    /**
     * 角色信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleInfo {
        private String roleId;
        private String roleName;
        private String roleCode;
        private Integer roleLevel;
    }
}
