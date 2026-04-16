package com.zhiwiki.auth.controller;

import com.zhiwiki.auth.entity.Dept;
import com.zhiwiki.auth.mapper.DeptMapper;
import com.zhiwiki.common.exception.BusinessException;
import com.zhiwiki.common.model.Result;
import com.zhiwiki.common.model.ResultCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门管理控制器
 */
@RestController
@RequestMapping("/api/dept")
@RequiredArgsConstructor
@Tag(name = "部门管理", description = "部门树CRUD、安全等级设置")
public class DeptController {

    private final DeptMapper deptMapper;

    @PostMapping
    @Operation(summary = "创建部门")
    public Result<Dept> createDept(@RequestBody Dept dept) {
        deptMapper.insert(dept);
        return Result.success(dept);
    }

    @GetMapping("/{deptId}")
    @Operation(summary = "根据deptId查询部门")
    public Result<Dept> getDept(@PathVariable String deptId) {
        Dept dept = deptMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Dept>()
                .eq(Dept::getDeptId, deptId)
        );
        if (dept == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "部门不存在");
        }
        return Result.success(dept);
    }

    @GetMapping("/tree")
    @Operation(summary = "获取部门树")
    public Result<List<Dept>> getDeptTree() {
        List<Dept> depts = deptMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Dept>()
                .orderByAsc(Dept::getSort)
        );
        return Result.success(depts);
    }

    @PutMapping
    @Operation(summary = "更新部门")
    public Result<Dept> updateDept(@RequestBody Dept dept) {
        deptMapper.updateById(dept);
        return Result.success(dept);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除部门")
    public Result<Void> deleteDept(@PathVariable Long id) {
        deptMapper.deleteById(id);
        return Result.success();
    }
}
