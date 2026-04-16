package com.zhiwiki.common.context;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * 用户权限上下文
 */
@Data
public class UserPermissionContext implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private String userId;

    /** 用户名 */
    private String username;

    /** 真实姓名 */
    private String realName;

    /** 部门ID */
    private String deptId;

    /** 部门名称 */
    private String deptName;

    /** 角色ID列表 */
    private List<String> roleIds;

    /** 角色标识列表 */
    private Set<String> roleCodes;

    /** 角色等级（最高等级） */
    private int maxRoleLevel;

    /** 安全等级 1-公开 2-内部 3-敏感 4-机密 */
    private int securityLevel;

    /** 是否激活 */
    private boolean active;

    /** 租户ID */
    private String tenantId;
}
