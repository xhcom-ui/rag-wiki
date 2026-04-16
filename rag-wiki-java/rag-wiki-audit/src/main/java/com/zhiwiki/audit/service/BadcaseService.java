package com.zhiwiki.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiwiki.audit.entity.Badcase;
import com.zhiwiki.audit.mapper.BadcaseMapper;
import com.zhiwiki.common.model.PageRequest;
import com.zhiwiki.common.model.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Badcase服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BadcaseService {

    private final BadcaseMapper badcaseMapper;

    /**
     * 分页查询Badcase
     */
    public PageResult<Badcase> page(PageRequest pageRequest, String severity, String status, String keyword) {
        Page<Badcase> page = new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize());
        LambdaQueryWrapper<Badcase> wrapper = new LambdaQueryWrapper<>();
        
        wrapper.eq(Badcase::getIsDeleted, 0);
        
        if (severity != null && !severity.isEmpty()) {
            wrapper.eq(Badcase::getSeverity, severity);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Badcase::getStatus, status);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Badcase::getQuery, keyword)
                    .or()
                    .like(Badcase::getDescription, keyword));
        }
        
        wrapper.orderByDesc(Badcase::getCreatedAt);
        
        Page<Badcase> result = badcaseMapper.selectPage(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), 
                pageRequest.getPageNum(), pageRequest.getPageSize());
    }

    /**
     * 获取统计信息
     */
    public Map<String, Integer> getStats() {
        Map<String, Object> dbStats = badcaseMapper.selectStats();
        Map<String, Integer> stats = new HashMap<>();
        stats.put("pending", ((Number) dbStats.getOrDefault("pending", 0)).intValue());
        stats.put("processing", ((Number) dbStats.getOrDefault("processing", 0)).intValue());
        stats.put("resolved", ((Number) dbStats.getOrDefault("resolved", 0)).intValue());
        stats.put("weeklyNew", ((Number) dbStats.getOrDefault("weeklyNew", 0)).intValue());
        return stats;
    }

    /**
     * 处理Badcase
     */
    @Transactional
    public boolean process(Long id, String status, String resolution, String improvement) {
        Badcase badcase = badcaseMapper.selectById(id);
        if (badcase == null) {
            return false;
        }
        
        badcase.setStatus(status);
        badcase.setResolution(resolution);
        badcase.setImprovement(improvement);
        badcase.setUpdatedAt(LocalDateTime.now());
        
        badcaseMapper.updateById(badcase);
        log.info("Badcase已处理: id={}, status={}", id, status);
        return true;
    }

    /**
     * 获取详情
     */
    public Badcase getDetail(Long id) {
        return badcaseMapper.selectById(id);
    }
}