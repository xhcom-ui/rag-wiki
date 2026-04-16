package com.zhiwiki.auth.controller;

import com.zhiwiki.auth.service.UserBatchImportService;
import com.zhiwiki.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 批量用户导入控制器
 */
@RestController
@RequestMapping("/api/user/import")
@RequiredArgsConstructor
@Tag(name = "批量用户导入", description = "Excel批量导入用户")
public class UserBatchImportController {

    private final UserBatchImportService batchImportService;

    @PostMapping
    @Operation(summary = "批量导入用户")
    public Result<Map<String, Object>> batchImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "defaultPassword", defaultValue = "RagWiki@2024") String defaultPassword,
            @RequestParam(value = "defaultRoleId", required = false) Long defaultRoleId) {
        UserBatchImportService.BatchImportResult result = batchImportService.batchImport(file, defaultPassword, defaultRoleId);
        return Result.success(Map.of(
                "successCount", result.successCount,
                "failedCount", result.getFailedCount(),
                "failedRows", result.failedRows
        ));
    }

    @GetMapping("/template")
    @Operation(summary = "获取导入模板数据")
    public Result<List<Map<String, String>>> getImportTemplate() {
        return Result.success(batchImportService.getImportTemplate());
    }
}
