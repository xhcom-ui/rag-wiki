package com.zhiwiki.audit.service;

import com.zhiwiki.audit.mapper.StatisticsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 统计报表服务
 * 使用Spring Cache自动缓存统计数据，避免频繁聚合查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final StatisticsMapper statisticsMapper;

    /**
     * 获取系统概览统计
     */
    @Cacheable(value = "statistics", key = "'overview'")
    public Map<String, Object> getSystemOverview() {
        Map<String, Object> overview = new HashMap<>();
        
        // 用户统计
        overview.put("totalUsers", statisticsMapper.countUsers());
        
        // 文档统计
        overview.put("totalDocuments", statisticsMapper.countDocuments());
        
        // 知识库统计
        overview.put("totalSpaces", statisticsMapper.countSpaces());
        
        // 今日新增文档
        overview.put("todayNewDocuments", statisticsMapper.countTodayDocuments());
        
        return overview;
    }

    /**
     * 获取AI调用统计
     */
    @Cacheable(value = "statistics", key = "'ai:' + #startDate + ':' + #endDate")
    public Map<String, Object> getAIStatistics(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> stats = new HashMap<>();
        LocalDateTime startTime = startDate.atStartOfDay();
        LocalDateTime endTime = endDate.plusDays(1).atStartOfDay();
        
        // 总查询次数
        stats.put("totalQueries", statisticsMapper.countAIQueries(startTime, endTime));
        
        // 成功次数
        stats.put("successQueries", statisticsMapper.countSuccessfulAIQueries(startTime, endTime));
        
        // 平均响应时间
        Double avgResponseTime = statisticsMapper.getAvgResponseTime(startTime, endTime);
        stats.put("avgResponseTime", avgResponseTime != null ? Math.round(avgResponseTime) : 0);
        
        // 每日趋势
        stats.put("dailyTrend", statisticsMapper.getDailyTrend(startTime, endTime));
        
        return stats;
    }

    /**
     * 获取文档统计
     */
    @Cacheable(value = "statistics", key = "'documents'")
    public Map<String, Object> getDocumentStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // 按类型统计
        stats.put("byType", statisticsMapper.countDocumentsByType());
        
        // 按知识库统计
        stats.put("bySpace", statisticsMapper.countDocumentsBySpace());
        
        // 文档增长趋势（最近30天）
        stats.put("growthTrend", statisticsMapper.getDocumentGrowthTrend());
        
        return stats;
    }

    /**
     * 获取用户活跃度统计
     */
    public Map<String, Object> getUserActivityStatistics(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> stats = new HashMap<>();
        LocalDateTime startTime = startDate.atStartOfDay();
        LocalDateTime endTime = endDate.plusDays(1).atStartOfDay();
        
        // 活跃用户（有查询记录的用户）
        stats.put("activeUsers", statisticsMapper.countActiveUsers(startTime, endTime));
        
        // 用户查询排行
        stats.put("topUsers", statisticsMapper.getUserQueryRanking(startTime, endTime));
        
        // 每日活跃用户
        stats.put("dailyActiveUsers", statisticsMapper.getDailyActiveUsers(startTime, endTime));
        
        return stats;
    }

    /**
     * 获取Badcase统计
     */
    @Cacheable(value = "statistics", key = "'badcases'")
    public Map<String, Object> getBadcaseStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // 按类型统计
        stats.put("byType", statisticsMapper.countBadcasesByType());
        
        // 按严重程度统计
        stats.put("bySeverity", statisticsMapper.countBadcasesBySeverity());
        
        // 解决率
        Integer total = statisticsMapper.countTotalBadcases();
        Integer resolved = statisticsMapper.countResolvedBadcases();
        stats.put("resolutionRate", total > 0 ? Math.round((double) resolved / total * 100) : 0);
        
        return stats;
    }

    /**
     * 获取热门查询
     */
    @Cacheable(value = "statistics", key = "'hot-queries:' + #limit")
    public List<Map<String, Object>> getHotQueries(int limit) {
        return statisticsMapper.getHotQueries(limit);
    }

    /**
     * 获取热门文档
     */
    @Cacheable(value = "statistics", key = "'hot-documents:' + #limit")
    public List<Map<String, Object>> getHotDocuments(int limit) {
        return statisticsMapper.getHotDocuments(limit);
    }
}