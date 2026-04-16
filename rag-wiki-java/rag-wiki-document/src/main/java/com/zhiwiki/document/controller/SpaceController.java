package com.zhiwiki.document.controller;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiwiki.common.exception.BusinessException;
import com.zhiwiki.common.model.PageRequest;
import com.zhiwiki.common.model.PageResult;
import com.zhiwiki.common.model.Result;
import com.zhiwiki.common.model.ResultCode;
import com.zhiwiki.document.entity.KnowledgeSpace;
import com.zhiwiki.document.mapper.KnowledgeSpaceMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 知识库空间管理控制器
 */
@RestController
@RequestMapping("/api/space")
@RequiredArgsConstructor
@Tag(name = "知识库空间管理", description = "知识库空间CRUD、成员管理、权限设置")
public class SpaceController {

    private final KnowledgeSpaceMapper spaceMapper;

    @PostMapping
    @Operation(summary = "创建知识库空间")
    public Result<KnowledgeSpace> createSpace(@RequestBody KnowledgeSpace space) {
        space.setSpaceId(IdUtil.fastSimpleUUID());
        spaceMapper.insert(space);
        return Result.success(space);
    }

    @GetMapping("/{spaceId}")
    @Operation(summary = "根据spaceId查询知识库")
    public Result<KnowledgeSpace> getSpace(@PathVariable String spaceId) {
        KnowledgeSpace space = spaceMapper.selectOne(
            new LambdaQueryWrapper<KnowledgeSpace>()
                .eq(KnowledgeSpace::getSpaceId, spaceId)
        );
        if (space == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "知识库空间不存在");
        }
        return Result.success(space);
    }

    @GetMapping("/list")
    @Operation(summary = "分页查询知识库列表")
    public Result<PageResult<KnowledgeSpace>> listSpaces(PageRequest pageRequest) {
        Page<KnowledgeSpace> page = spaceMapper.selectPage(
            new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize()),
            new LambdaQueryWrapper<KnowledgeSpace>().orderByDesc(KnowledgeSpace::getCreatedAt)
        );
        return Result.success(new PageResult<>(
            page.getRecords(), page.getTotal(), 
            (int) page.getCurrent(), (int) page.getSize()
        ));
    }

    @PutMapping
    @Operation(summary = "更新知识库空间")
    public Result<KnowledgeSpace> updateSpace(@RequestBody KnowledgeSpace space) {
        spaceMapper.updateById(space);
        return Result.success(space);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除知识库空间")
    public Result<Void> deleteSpace(@PathVariable Long id) {
        spaceMapper.deleteById(id);
        return Result.success();
    }
}
