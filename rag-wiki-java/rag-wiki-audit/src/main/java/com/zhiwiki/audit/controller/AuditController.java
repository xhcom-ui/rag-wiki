package com.zhiwiki.audit.controller;

import com.zhiwiki.audit.entity.RagAuditLog;
import com.zhiwiki.audit.service.AuditService;
import com.zhiwiki.common.model.PageRequest;
import com.zhiwiki.common.model.PageResult;
import com.zhiwiki.common.model.Result;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@Tag(name = "审计管理", description = "审计日志、Badcase管理")
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/logs")
    public Result<PageResult<RagAuditLog>> pageLogs(PageRequest pageRequest,
                                                     @RequestParam(required = false) String userId,
                                                     @RequestParam(required = false) String startDate,
                                                     @RequestParam(required = false) String endDate) {
        return Result.success(auditService.pageAuditLogs(pageRequest, userId, startDate, endDate));
    }

    @PostMapping("/logs")
    public Result<Void> addLog(@RequestBody RagAuditLog auditLog) {
        auditService.logRagQuery(auditLog);
        return Result.success();
    }
}
