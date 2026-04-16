package com.zhiwiki.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiwiki.auth.entity.RoleMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色菜单权限Mapper
 */
@Mapper
public interface RoleMenuMapper extends BaseMapper<RoleMenu> {

    /**
     * 根据角色ID查询菜单ID列表
     */
    @Select("SELECT menu_id FROM sys_role_menu WHERE role_id = #{roleId}")
    List<String> selectMenuIdsByRoleId(@Param("roleId") String roleId);

    /**
     * 批量插入角色菜单关联
     */
    void batchInsert(@Param("roleId") String roleId, @Param("menuIds") List<String> menuIds);

    /**
     * 根据角色ID删除关联
     */
    int deleteByRoleId(@Param("roleId") String roleId);
}