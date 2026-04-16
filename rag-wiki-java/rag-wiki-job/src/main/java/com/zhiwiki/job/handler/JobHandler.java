package com.zhiwiki.job.handler;

import com.xxl.job.core.handler.annotation.XxlJob;
import com.zhiwiki.job.mapper.JobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 定时任务处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobHandler {

    private final RedisTemplate<String, Object> redisTemplate;
    private final JobMapper jobMapper;
    private final RestTemplate restTemplate;

    // Python AI 服务地址（通过配置注入）
    @Value("${rag-wiki.ai-service.url:http://localhost:8000}")
    private String aiServiceUrl;

    /**
     * 记忆过期清理任务
     * 每天凌晨 2 点执行
     */
    @XxlJob("memoryCleanupJob")
    public void memoryCleanupJob() {
        log.info("开始执行记忆过期清理任务...");
        try {
            // 调用 AI 服务清理过期记忆
            String url = aiServiceUrl + "/api/v1/memory/cleanup";
            try {
                Map<String, Object> result = restTemplate.postForObject(url, null, Map.class);
                log.info("AI服务记忆清理结果: {}", result);
            } catch (Exception e) {
                log.warn("调用AI服务清理记忆失败，执行本地清理: {}", e.getMessage());
                // 本地清理：清理 Redis 中过期的会话数据
                cleanExpiredSessions();
            }

            log.info("记忆过期清理任务执行完成");
        } catch (Exception e) {
            log.error("记忆过期清理任务执行失败", e);
        }
    }

    /**
     * 清理过期的会话数据
     */
    private void cleanExpiredSessions() {
        Set<String> keys = redisTemplate.keys("user:session:*");
        if (keys != null && !keys.isEmpty()) {
            for (String key : keys) {
                Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                if (ttl != null && ttl < 0) {
                    redisTemplate.delete(key);
                    log.debug("删除过期会话: {}", key);
                }
            }
        }
    }

    /**
     * 文档状态同步任务
     * 每 5 分钟执行一次
     */
    @XxlJob("documentStatusSyncJob")
    public void documentStatusSyncJob() {
        log.info("开始执行文档状态同步任务...");
        try {
            // 1. 同步解析任务状态
            int updatedTasks = syncParseTaskStatus();
            log.info("同步解析任务状态完成，更新 {} 条", updatedTasks);

            // 2. 检查超时的解析任务
            int timeoutTasks = markTimeoutTasks();
            log.info("标记超时任务完成，共 {} 条", timeoutTasks);

            // 3. 统计各状态任务数量
            Map<String, Integer> statusCount = countTaskByStatus();
            log.info("任务状态统计: {}", statusCount);

            log.info("文档状态同步任务执行完成");
        } catch (Exception e) {
            log.error("文档状态同步任务执行失败", e);
        }
    }

    /**
     * 同步解析任务状态
     */
    private int syncParseTaskStatus() {
        return jobMapper.syncParseTaskStatus();
    }

    /**
     * 标记超时的解析任务
     */
    private int markTimeoutTasks() {
        return jobMapper.markTimeoutTasks();
    }

    /**
     * 统计各状态任务数量
     */
    private Map<String, Integer> countTaskByStatus() {
        List<Map<String, Object>> results = jobMapper.countTaskByStatus();
        Map<String, Integer> result = new HashMap<>();
        for (Map<String, Object> row : results) {
            result.put((String) row.get("status"), ((Number) row.get("cnt")).intValue());
        }
        return result;
    }

    /**
     * 统计报表生成任务
     * 每天凌晨 3 点执行
     */
    @XxlJob("statisticsReportJob")
    public void statisticsReportJob() {
        log.info("开始执行统计报表生成任务...");
        try {
            String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);

            // 1. 生成日报数据
            generateDailyReport(today);

            // 2. 更新缓存统计
            updateCacheStatistics();

            // 3. 清理旧的统计数据
            cleanOldStatistics();

            log.info("统计报表生成任务执行完成");
        } catch (Exception e) {
            log.error("统计报表生成任务执行失败", e);
        }
    }

    /**
     * 生成日报数据
     */
    private void generateDailyReport(String date) {
        // 查询统计数据
        Integer qaCount = jobMapper.countTodayQA(date);
        Integer activeUsers = jobMapper.countTodayActiveUsers(date);
        Integer newDocs = jobMapper.countTodayNewDocs(date);
        Integer parseCount = jobMapper.countTodayParseCompleted(date);

        // 插入统计报表
        jobMapper.insertOrUpdateDailyReport(date, qaCount, activeUsers, newDocs, parseCount);

        log.info("日报生成完成: 日期={}, 问答={}, 活跃用户={}, 新增文档={}, 解析文档={}",
                date, qaCount, activeUsers, newDocs, parseCount);
    }

    /**
     * 更新缓存统计
     */
    private void updateCacheStatistics() {
        // 缓存今日统计到 Redis
        String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        String key = "stats:daily:" + today;

        Map<String, Object> stats = new HashMap<>();
        stats.put("date", today);
        stats.put("updatedAt", LocalDateTime.now().toString());

        redisTemplate.opsForHash().putAll(key, stats);
        redisTemplate.expire(key, 7, TimeUnit.DAYS);
    }

    /**
     * 清理旧的统计数据
     */
    private void cleanOldStatistics() {
        // 删除 30 天前的审计日志
        int deleted = jobMapper.cleanOldAuditLogs();
        log.info("清理旧审计日志完成，删除 {} 条", deleted);
    }

    /**
     * 权限缓存清理任务
     * 每小时执行一次
     */
    @XxlJob("permissionCacheCleanJob")
    public void permissionCacheCleanJob() {
        log.info("开始执行权限缓存清理任务...");
        try {
            // 1. 清理过期或无效的权限缓存
            Set<String> keys = redisTemplate.keys("user:permissions:*");
            int cleaned = 0;
            if (keys != null) {
                for (String key : keys) {
                    // 检查用户是否仍然有效
                    String userId = key.replace("user:permissions:", "");
                    if (!isUserValid(userId)) {
                        redisTemplate.delete(key);
                        cleaned++;
                    }
                }
            }
            log.info("清理无效权限缓存 {} 条", cleaned);

            // 2. 清理过期的 JWT Token 会话
            cleanExpiredTokens();

            // 3. 更新权限缓存统计
            updatePermissionCacheStats();

            log.info("权限缓存清理任务执行完成");
        } catch (Exception e) {
            log.error("权限缓存清理任务执行失败", e);
        }
    }

    /**
     * 检查用户是否仍然有效
     */
    private boolean isUserValid(String userId) {
        try {
            Integer status = jobMapper.getUserStatus(userId);
            return status != null && status == 1;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 清理过期的 Token
     */
    private void cleanExpiredTokens() {
        Set<String> keys = redisTemplate.keys("satoken:login:token:*");
        if (keys != null) {
            for (String key : keys) {
                Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                if (ttl != null && ttl < 0) {
                    redisTemplate.delete(key);
                }
            }
        }
    }

    /**
     * 更新权限缓存统计
     */
    private void updatePermissionCacheStats() {
        Set<String> keys = redisTemplate.keys("user:permissions:*");
        int count = keys != null ? keys.size() : 0;
        redisTemplate.opsForValue().set("stats:permission_cache:count", count);
    }
}
