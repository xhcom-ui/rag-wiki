package com.zhiwiki.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiwiki.auth.entity.Dept;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 部门Mapper
 */
@Mapper
public interface DeptMapper extends BaseMapper<Dept> {

    /**
     * 查询指定父部门下的直属子部门ID列表
     */
    @Select("SELECT dept_id FROM sys_dept WHERE parent_id = #{parentId} AND is_deleted = 0 AND status = 1")
    List<String> selectChildDeptIds(Long parentId);

    /**
     * 查询指定父部门下的直属子部门列表（含parentId用于递归）
     */
    @Select("SELECT dept_id, parent_id FROM sys_dept WHERE parent_id = #{parentId} AND is_deleted = 0 AND status = 1")
    List<Dept> selectChildDepts(Long parentId);
}
