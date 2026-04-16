package com.zhiwiki.audit.controller;

import com.zhiwiki.audit.entity.SecurityAlert;
import com.zhiwiki.audit.service.SecurityAlertService;
import com.zhiwiki.common.model.PageRequest;
import com.zhiwiki.common.model.PageResult;
import com.zhiwiki.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 安全告警控制器
 */
@RestController
@RequestMapping("/api/security/alert")
@RequiredArgsConstructor
@Tag(name = "安全告警", description = "安全告警管理与处理")
public class SecurityAlertController {

    private final SecurityAlertService alertService;

    /**
     * 分页查询告警
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询告警", description = "支持按严重程度、状态、类型筛选")
    public Result<PageResult<SecurityAlert>> page(
            PageRequest pageRequest,
            @RequestParam(required = false) @Parameter(description = "严重程度") String severity,
            @RequestParam(required = false) @Parameter(description = "状态") String status,
            @RequestParam(required = false) @Parameter(description = "告警类型") String alertType) {
        return Result.success(alertService.page(pageRequest, severity, status, alertType));
    }

    /**
     * 获取告警统计
     */
    @GetMapping("/statistics")
    @Operation(summary = "告警统计", description = "获取告警统计数据")
    public Result<Map<String, Object>> getStatistics() {
        return Result.success(alertService.getAlertStatistics());
    }

    /**
     * 获取未处理告警
     */
    @GetMapping("/unhandled")
    @Operation(summary = "未处理告警", description = "获取未处理的安全告警列表")
    public Result<List<SecurityAlert>> getUnhandledAlerts(
            @RequestParam(defaultValue = "10") @Parameter(description = "数量限制") int limit) {
        return Result.success(alertService.getUnhandledAlerts(limit));
    }

    /**
     * 处理告警
     */
    @PostMapping("/{alertId}/handle")
    @Operation(summary = "处理告警", description = "处理安全告警")
    public Result<Boolean> handleAlert(
            @PathVariable @Parameter(description = "告警ID") String alertId,
            @RequestBody Map<String, String> request) {
        String handlerId = request.get("handlerId");
        String handleResult = request.get("handleResult");
        String newStatus = request.getOrDefault("status", "RESOLVED");
        return Result.success(alertService.handleAlert(alertId, handlerId, handleResult, newStatus));
    }

    /**
     * 忽略告警
     */
    @PostMapping("/{alertId}/ignore")
    @Operation(summary = "忽略告警", description = "忽略安全告警")
    public Result<Boolean> ignoreAlert(
            @PathVariable @Parameter(description = "告警ID") String alertId,
            @RequestBody Map<String, String> request) {
        String handlerId = request.get("handlerId");
        String reason = request.get("reason");
        return Result.success(alertService.handleAlert(alertId, handlerId, reason, "IGNORED"));
    }
}