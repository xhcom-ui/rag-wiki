package com.zhiwiki.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色菜单权限实体
 */
@Data
@TableName("sys_role_menu")
public class RoleMenu {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("role_id")
    private String roleId;

    @TableField("menu_id")
    private String menuId;

    @TableField("created_at")
    private LocalDateTime createdAt;
}