package com.zhiwiki.audit.controller;

import com.zhiwiki.audit.entity.Badcase;
import com.zhiwiki.audit.service.BadcaseService;
import com.zhiwiki.common.model.PageRequest;
import com.zhiwiki.common.model.PageResult;
import com.zhiwiki.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Badcase管理控制器
 */
@RestController
@RequestMapping("/api/audit/badcase")
@RequiredArgsConstructor
@Tag(name = "Badcase管理", description = "RAG问答Badcase处理")
public class BadcaseController {

    private final BadcaseService badcaseService;

    /**
     * 分页查询Badcase列表
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询Badcase", description = "支持按严重程度、状态筛选")
    public Result<PageResult<Badcase>> page(
            PageRequest pageRequest,
            @RequestParam(required = false) @Parameter(description = "严重程度") String severity,
            @RequestParam(required = false) @Parameter(description = "状态") String status,
            @RequestParam(required = false) @Parameter(description = "关键词") String keyword) {
        
        return Result.success(badcaseService.page(pageRequest, severity, status, keyword));
    }

    /**
     * 获取Badcase统计
     */
    @GetMapping("/stats")
    @Operation(summary = "获取Badcase统计", description = "获取各状态数量统计")
    public Result<Map<String, Integer>> getStats() {
        return Result.success(badcaseService.getStats());
    }

    /**
     * 处理Badcase
     */
    @PostMapping("/{id}/process")
    @Operation(summary = "处理Badcase", description = "更新Badcase处理状态和结果")
    public Result<Boolean> process(
            @PathVariable @Parameter(description = "Badcase ID") Long id,
            @RequestBody Map<String, String> request) {
        
        String status = request.get("status");
        String resolution = request.get("resolution");
        String improvement = request.get("improvement");
        
        return Result.success(badcaseService.process(id, status, resolution, improvement));
    }

    /**
     * 获取Badcase详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取Badcase详情", description = "获取Badcase详细信息")
    public Result<Badcase> getDetail(@PathVariable Long id) {
        return Result.success(badcaseService.getDetail(id));
    }
}