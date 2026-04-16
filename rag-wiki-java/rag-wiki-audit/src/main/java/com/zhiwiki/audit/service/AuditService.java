package com.zhiwiki.audit.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiwiki.audit.entity.RagAuditLog;
import com.zhiwiki.audit.entity.Badcase;
import com.zhiwiki.audit.mapper.RagAuditLogMapper;
import com.zhiwiki.audit.mapper.BadcaseMapper;
import com.zhiwiki.common.model.PageRequest;
import com.zhiwiki.common.model.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final RagAuditLogMapper auditLogMapper;
    private final BadcaseMapper badcaseMapper;

    /** 记录RAG审计日志 */
    public void logRagQuery(RagAuditLog auditLog) {
        auditLog.setAuditId(IdUtil.fastSimpleUUID());
        auditLogMapper.insert(auditLog);
    }

    /** 分页查询审计日志 */
    public PageResult<RagAuditLog> pageAuditLogs(PageRequest pageRequest, String userId, String startDate, String endDate) {
        Page<RagAuditLog> page = new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize());
        LambdaQueryWrapper<RagAuditLog> wrapper = new LambdaQueryWrapper<>();
        if (userId != null && !userId.isEmpty()) {
            wrapper.eq(RagAuditLog::getUserId, userId);
        }
        wrapper.orderByDesc(RagAuditLog::getCreatedAt);
        Page<RagAuditLog> result = auditLogMapper.selectPage(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), pageRequest.getPageNum(), pageRequest.getPageSize());
    }

    /** 创建Badcase */
    public Badcase createBadcase(Badcase badcase) {
        badcase.setCaseId(IdUtil.fastSimpleUUID());
        badcase.setStatus("NEW");
        badcaseMapper.insert(badcase);
        return badcase;
    }

    /** 分页查询Badcase */
    public PageResult<Badcase> pageBadcases(PageRequest pageRequest, String badcaseType, String status) {
        Page<Badcase> page = new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize());
        LambdaQueryWrapper<Badcase> wrapper = new LambdaQueryWrapper<>();
        if (badcaseType != null && !badcaseType.isEmpty()) {
            wrapper.eq(Badcase::getBadcaseType, badcaseType);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Badcase::getStatus, status);
        }
        wrapper.orderByDesc(Badcase::getCreatedAt);
        Page<Badcase> result = badcaseMapper.selectPage(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), pageRequest.getPageNum(), pageRequest.getPageSize());
    }
}
