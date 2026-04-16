package com.zhiwiki.auth.controller;

import com.zhiwiki.auth.service.UserDataExportService;
import com.zhiwiki.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户数据导出/销毁控制器 (GDPR)
 */
@RestController
@RequestMapping("/api/user/data")
@RequiredArgsConstructor
@Tag(name = "用户数据管理", description = "数据导出/销毁(GDPR被遗忘权)")
public class UserDataExportController {

    private final UserDataExportService dataExportService;

    @GetMapping("/export")
    @Operation(summary = "导出我的全部数据")
    public Result<Map<String, Object>> exportMyData() {
        String userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
        return Result.success(dataExportService.exportUserData(userId));
    }

    @DeleteMapping("/destroy")
    @Operation(summary = "永久销毁我的数据(GDPR被遗忘权)")
    public Result<Void> destroyMyData(@RequestParam String confirmation) {
        String userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
        dataExportService.destroyUserData(userId, confirmation);
        return Result.success();
    }

    @PostMapping("/anonymize")
    @Operation(summary = "匿名化我的数据")
    public Result<Void> anonymizeMyData() {
        String userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
        dataExportService.anonymizeUserData(userId);
        return Result.success();
    }
}
