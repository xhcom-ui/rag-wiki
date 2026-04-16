package com.zhiwiki.auth.controller;

import com.zhiwiki.auth.entity.SysConfig;
import com.zhiwiki.auth.service.SysConfigService;
import com.zhiwiki.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统配置管理控制器
 */
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@Tag(name = "系统配置管理", description = "基础参数、安全策略、通知模板配置")
public class SysConfigController {

    private final SysConfigService sysConfigService;

    @GetMapping("/groups")
    @Operation(summary = "获取所有配置分组")
    public Result<List<SysConfigService.ConfigGroupVO>> getAllGroups() {
        return Result.success(sysConfigService.getAllGroups());
    }

    @GetMapping("/group/{group}")
    @Operation(summary = "获取指定分组的配置")
    public Result<List<SysConfig>> getConfigsByGroup(@PathVariable String group) {
        return Result.success(sysConfigService.getConfigsByGroup(group));
    }

    @GetMapping("/value/{key}")
    @Operation(summary = "获取单个配置值")
    public Result<Map<String, String>> getConfigValue(@PathVariable String key) {
        return Result.success(Map.of("key", key, "value", sysConfigService.getConfigValue(key, "")));
    }

    @PutMapping("/value")
    @Operation(summary = "更新配置值")
    public Result<Void> updateConfigValue(@RequestBody Map<String, String> params) {
        sysConfigService.updateConfigValue(params.get("key"), params.get("value"));
        return Result.success();
    }

    @PostMapping
    @Operation(summary = "新增配置项")
    public Result<SysConfig> addConfig(@RequestBody SysConfig config) {
        return Result.success(sysConfigService.addConfig(config));
    }

    @DeleteMapping("/{key}")
    @Operation(summary = "删除配置项")
    public Result<Void> deleteConfig(@PathVariable String key) {
        sysConfigService.deleteConfig(key);
        return Result.success();
    }
}
