package com.zhiwiki.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiwiki.auth.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户角色关联Mapper
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {

    /**
     * 根据用户ID查询角色ID列表
     */
    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<String> selectRoleIdsByUserId(@Param("userId") String userId);

    /**
     * 根据用户ID删除关联
     */
    int deleteByUserId(@Param("userId") String userId);

    /**
     * 批量插入用户角色关联
     */
    void batchInsert(@Param("userId") String userId, @Param("roleIds") List<String> roleIds);
}