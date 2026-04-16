package com.zhiwiki.audit.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiwiki.audit.dto.LLMConfigRequest;
import com.zhiwiki.audit.entity.LLMConfig;
import com.zhiwiki.audit.service.LLMConfigService;
import com.zhiwiki.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * LLM模型配置管理控制器
 */
@RestController
@RequestMapping("/api/ai/llm-config")
@RequiredArgsConstructor
@Tag(name = "LLM模型配置", description = "多LLM提供商配置管理")
public class LLMConfigController {

    private final LLMConfigService llmConfigService;

    @GetMapping("/page")
    @Operation(summary = "分页查询配置列表")
    public Result<Page<LLMConfig>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) Integer isEnabled) {
        return Result.success(llmConfigService.page(pageNum, pageSize, provider, isEnabled));
    }

    @GetMapping("/list")
    @Operation(summary = "获取所有启用的配置")
    public Result<List<LLMConfig>> listEnabled() {
        return Result.success(llmConfigService.listEnabled());
    }

    @GetMapping("/{configId}")
    @Operation(summary = "获取配置详情")
    public Result<LLMConfig> getById(@PathVariable String configId) {
        return Result.success(llmConfigService.getById(configId));
    }

    @PostMapping
    @Operation(summary = "创建配置")
    public Result<LLMConfig> create(@Valid @RequestBody LLMConfigRequest request) {
        return Result.success(llmConfigService.create(request));
    }

    @PutMapping("/{configId}")
    @Operation(summary = "更新配置")
    public Result<LLMConfig> update(@PathVariable String configId, @Valid @RequestBody LLMConfigRequest request) {
        return Result.success(llmConfigService.update(configId, request));
    }

    @DeleteMapping("/{configId}")
    @Operation(summary = "删除配置")
    public Result<Void> delete(@PathVariable String configId) {
        llmConfigService.delete(configId);
        return Result.success();
    }

    @PostMapping("/{configId}/set-default")
    @Operation(summary = "设置默认配置")
    public Result<Void> setDefault(@PathVariable String configId) {
        llmConfigService.setDefault(configId);
        return Result.success();
    }

    @GetMapping("/providers")
    @Operation(summary = "获取支持的提供商列表")
    public Result<List<Map<String, String>>> getProviders() {
        return Result.success(llmConfigService.getProviders());
    }
}
