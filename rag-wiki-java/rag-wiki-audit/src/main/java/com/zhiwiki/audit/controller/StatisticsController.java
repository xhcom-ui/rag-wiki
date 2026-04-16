package com.zhiwiki.audit.controller;

import com.zhiwiki.audit.service.StatisticsService;
import com.zhiwiki.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 统计报表控制器
 */
@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
@Tag(name = "统计报表", description = "系统数据统计与可视化")
public class StatisticsController {

    private final StatisticsService statisticsService;

    /**
     * 系统概览
     */
    @GetMapping("/overview")
    @Operation(summary = "系统概览", description = "获取系统核心指标概览")
    public Result<Map<String, Object>> getOverview() {
        return Result.success(statisticsService.getSystemOverview());
    }

    /**
     * AI调用统计
     */
    @GetMapping("/ai")
    @Operation(summary = "AI统计", description = "获取AI问答调用统计")
    public Result<Map<String, Object>> getAIStatistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) 
            @Parameter(description = "开始日期") LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) 
            @Parameter(description = "结束日期") LocalDate endDate) {
        return Result.success(statisticsService.getAIStatistics(startDate, endDate));
    }

    /**
     * 文档统计
     */
    @GetMapping("/documents")
    @Operation(summary = "文档统计", description = "获取文档相关统计")
    public Result<Map<String, Object>> getDocumentStatistics() {
        return Result.success(statisticsService.getDocumentStatistics());
    }

    /**
     * 用户活跃度统计
     */
    @GetMapping("/user-activity")
    @Operation(summary = "用户活跃度", description = "获取用户活跃度统计")
    public Result<Map<String, Object>> getUserActivityStatistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) 
            @Parameter(description = "开始日期") LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) 
            @Parameter(description = "结束日期") LocalDate endDate) {
        return Result.success(statisticsService.getUserActivityStatistics(startDate, endDate));
    }

    /**
     * Badcase统计
     */
    @GetMapping("/badcases")
    @Operation(summary = "Badcase统计", description = "获取Badcase统计信息")
    public Result<Map<String, Object>> getBadcaseStatistics() {
        return Result.success(statisticsService.getBadcaseStatistics());
    }

    /**
     * 热门查询
     */
    @GetMapping("/hot-queries")
    @Operation(summary = "热门查询", description = "获取热门查询排行")
    public Result<List<Map<String, Object>>> getHotQueries(
            @RequestParam(defaultValue = "10") @Parameter(description = "数量限制") int limit) {
        return Result.success(statisticsService.getHotQueries(limit));
    }

    /**
     * 热门文档
     */
    @GetMapping("/hot-documents")
    @Operation(summary = "热门文档", description = "获取热门文档排行")
    public Result<List<Map<String, Object>>> getHotDocuments(
            @RequestParam(defaultValue = "10") @Parameter(description = "数量限制") int limit) {
        return Result.success(statisticsService.getHotDocuments(limit));
    }

    /**
     * 综合仪表盘数据
     */
    @GetMapping("/dashboard")
    @Operation(summary = "仪表盘数据", description = "获取仪表盘所需的所有统计数据")
    public Result<Map<String, Object>> getDashboardData() {
        Map<String, Object> dashboard = new java.util.HashMap<>();
        
        // 系统概览
        dashboard.put("overview", statisticsService.getSystemOverview());
        
        // 最近7天AI统计
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(7);
        dashboard.put("aiStats", statisticsService.getAIStatistics(startDate, endDate));
        
        // 文档统计
        dashboard.put("documentStats", statisticsService.getDocumentStatistics());
        
        // 用户活跃度
        dashboard.put("userActivity", statisticsService.getUserActivityStatistics(startDate, endDate));
        
        // Badcase统计
        dashboard.put("badcaseStats", statisticsService.getBadcaseStatistics());
        
        // 热门查询
        dashboard.put("hotQueries", statisticsService.getHotQueries(5));
        
        return Result.success(dashboard);
    }
}