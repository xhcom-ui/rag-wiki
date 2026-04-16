package com.zhiwiki.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiwiki.audit.entity.SecurityAlert;
import com.zhiwiki.audit.mapper.SecurityAlertMapper;
import com.zhiwiki.common.model.PageRequest;
import com.zhiwiki.common.model.PageResult;
import com.zhiwiki.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 安全告警服务
 * 
 * 检测能力：
 * 1. 高频查询检测（基于Redis滑动窗口）
 * 2. 越权访问检测
 * 3. 非工作时间访问检测
 * 4. SQL注入检测
 * 5. 敏感文档访问检测
 * 6. 跨部门访问检测
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityAlertService {

    private final SecurityAlertMapper alertMapper;
    private final NotificationService notificationService;
    private final StringRedisTemplate redisTemplate;

    private static final String QUERY_COUNT_PREFIX = "rag-wiki:security:query:";
    private static final String ACCESS_COUNT_PREFIX = "rag-wiki:security:access:";
    private static final String ALERT_RATE_PREFIX = "rag-wiki:security:alert-rate:";

    /** 高频查询阈值：1分钟内查询次数 */
    private static final int HIGH_FREQ_THRESHOLD = 30;
    /** 越权尝试阈值：1小时内越权次数 */
    private static final int UNAUTHORIZED_THRESHOLD = 5;
    /** 敏感文档访问阈值 */
    private static final int SENSITIVE_DOC_THRESHOLD = 10;

    /**
     * 创建告警
     */
    @Transactional
    public SecurityAlert createAlert(String ruleId, String alertType, String severity,
                                     String title, String content, String sourceIp,
                                     String userId, String targetId, String evidence) {
        // 告警限流：相同类型+用户1分钟内不重复告警
        String rateKey = ALERT_RATE_PREFIX + alertType + ":" + userId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(rateKey))) {
            log.debug("告警限流，跳过重复告警: type={}, userId={}", alertType, userId);
            return null;
        }

        SecurityAlert alert = new SecurityAlert();
        alert.setAlertId(UUID.randomUUID().toString().replace("-", ""));
        alert.setRuleId(ruleId);
        alert.setAlertType(alertType);
        alert.setSeverity(severity);
        alert.setTitle(title);
        alert.setContent(content);
        alert.setSourceIp(sourceIp);
        alert.setUserId(userId);
        alert.setTargetId(targetId);
        alert.setEvidence(evidence);
        alert.setStatus("NEW");
        alert.setIsDeleted(0);
        alert.setCreatedAt(LocalDateTime.now());
        alert.setUpdatedAt(LocalDateTime.now());
        
        alertMapper.insert(alert);

        // 设置告警限流（1分钟）
        redisTemplate.opsForValue().set(rateKey, "1", 1, TimeUnit.MINUTES);
        
        // 发送通知
        sendAlertNotification(alert);
        
        log.info("安全告警已创建: alertId={}, type={}, severity={}", 
                alert.getAlertId(), alertType, severity);
        
        return alert;
    }

    /**
     * 分页查询告警
     */
    public PageResult<SecurityAlert> page(PageRequest pageRequest, String severity, 
                                          String status, String alertType) {
        Page<SecurityAlert> page = new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize());
        LambdaQueryWrapper<SecurityAlert> wrapper = new LambdaQueryWrapper<>();
        
        wrapper.eq(SecurityAlert::getIsDeleted, 0);
        
        if (severity != null && !severity.isEmpty()) {
            wrapper.eq(SecurityAlert::getSeverity, severity);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(SecurityAlert::getStatus, status);
        }
        if (alertType != null && !alertType.isEmpty()) {
            wrapper.eq(SecurityAlert::getAlertType, alertType);
        }
        
        wrapper.orderByDesc(SecurityAlert::getCreatedAt);
        
        Page<SecurityAlert> result = alertMapper.selectPage(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(),
                pageRequest.getPageNum(), pageRequest.getPageSize());
    }

    /**
     * 处理告警
     */
    @Transactional
    public boolean handleAlert(String alertId, String handlerId, String handleResult, String newStatus) {
        SecurityAlert alert = alertMapper.selectOne(
                new LambdaQueryWrapper<SecurityAlert>()
                        .eq(SecurityAlert::getAlertId, alertId)
                        .eq(SecurityAlert::getIsDeleted, 0)
        );
        
        if (alert == null) {
            return false;
        }
        
        alert.setHandlerId(handlerId);
        alert.setHandleResult(handleResult);
        alert.setStatus(newStatus);
        alert.setHandledAt(LocalDateTime.now());
        alert.setUpdatedAt(LocalDateTime.now());
        
        alertMapper.updateById(alert);
        
        log.info("告警已处理: alertId={}, handler={}, status={}", alertId, handlerId, newStatus);
        return true;
    }

    /**
     * 获取告警统计
     */
    public Map<String, Object> getAlertStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        List<Map<String, Object>> bySeverity = alertMapper.countBySeverity();
        stats.put("bySeverity", bySeverity);
        
        List<Map<String, Object>> byStatus = alertMapper.countByStatus();
        stats.put("byStatus", byStatus);
        
        long unhandledCount = byStatus.stream()
                .filter(m -> "NEW".equals(m.get("status")) || "CONFIRMED".equals(m.get("status")))
                .mapToLong(m -> (Long) m.get("count"))
                .sum();
        stats.put("unhandledCount", unhandledCount);
        
        Long todayNew = alertMapper.selectCount(
                new LambdaQueryWrapper<SecurityAlert>()
                        .eq(SecurityAlert::getIsDeleted, 0)
                        .ge(SecurityAlert::getCreatedAt, LocalDateTime.now().toLocalDate().atStartOfDay())
        );
        stats.put("todayNew", todayNew.intValue());
        
        return stats;
    }

    /**
     * 获取未处理告警
     */
    public List<SecurityAlert> getUnhandledAlerts(int limit) {
        return alertMapper.selectUnhandled(limit);
    }

    /**
     * 发送告警通知
     */
    private void sendAlertNotification(SecurityAlert alert) {
        try {
            String title = String.format("[%s] %s", alert.getSeverity(), alert.getTitle());
            String content = String.format("告警类型: %s\n内容: %s\n时间: %s\n来源IP: %s",
                    alert.getAlertType(), alert.getContent(), 
                    alert.getCreatedAt(), alert.getSourceIp());
            
            notificationService.sendSiteMessage("admin", title, content, 
                    alert.getAlertId(), "SECURITY_ALERT");
            
            if ("CRITICAL".equals(alert.getSeverity()) || "HIGH".equals(alert.getSeverity())) {
                notificationService.sendEmail("admin@company.com", title, content);
            }
        } catch (Exception e) {
            log.error("发送告警通知失败: {}", e.getMessage());
        }
    }

    // ==================== 异常检测方法 ====================

    /**
     * 检测高频查询
     * 基于Redis滑动窗口计数，1分钟内查询超过阈值则触发告警
     */
    public void detectHighFrequencyQuery(String userId, String clientIp) {
        String key = QUERY_COUNT_PREFIX + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        
        if (count != null && count == 1) {
            // 第一次访问，设置1分钟过期
            redisTemplate.expire(key, 1, TimeUnit.MINUTES);
        }
        
        if (count != null && count == HIGH_FREQ_THRESHOLD) {
            createAlert("rule001", "HIGH_FREQUENCY", "HIGH",
                    "高频查询检测",
                    String.format("用户 %s 在1分钟内查询%d次，超过阈值%d", userId, count, HIGH_FREQ_THRESHOLD),
                    clientIp, userId, null,
                    String.format("{\"queryCount\":%d,\"threshold\":%d,\"window\":\"1m\"}", count, HIGH_FREQ_THRESHOLD));
        }
    }

    /**
     * 检测越权访问
     * 用户尝试访问超出其安全等级的资源
     */
    public void detectUnauthorizedAccess(String userId, String clientIp, 
                                          int userSecurityLevel, int requiredLevel) {
        if (userSecurityLevel < requiredLevel) {
            String key = ACCESS_COUNT_PREFIX + "unauth:" + userId;
            Long count = redisTemplate.opsForValue().increment(key);
            
            if (count != null && count == 1) {
                redisTemplate.expire(key, 1, TimeUnit.HOURS);
            }
            
            if (count != null && count >= UNAUTHORIZED_THRESHOLD) {
                createAlert("rule002", "UNAUTHORIZED_ACCESS", "CRITICAL",
                        "越权访问检测",
                        String.format("用户 %s (安全等级%d) 1小时内%d次尝试访问等级%d资源",
                                userId, userSecurityLevel, count, requiredLevel),
                        clientIp, userId, null,
                        String.format("{\"userLevel\":%d,\"requiredLevel\":%d,\"attempts\":%d}", 
                                userSecurityLevel, requiredLevel, count));
            }
        }
    }

    /**
     * 检测非工作时间访问
     * 工作日22:00-06:00、周末全天视为非工作时间
     */
    public void detectOffHoursAccess(String userId, String clientIp) {
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();
        DayOfWeek dayOfWeek = now.getDayOfWeek();
        boolean isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
        boolean isOffHours = hour >= 22 || hour < 6;

        if (isWeekend || isOffHours) {
            String timeDesc = isWeekend 
                    ? "周末(" + dayOfWeek.name() + ")" 
                    : String.format("非工作时间(%02d:00)", hour);
            
            createAlert("rule003", "OFF_HOURS_ACCESS", "LOW",
                    "非工作时间访问检测",
                    String.format("用户 %s 在%s访问系统", userId, timeDesc),
                    clientIp, userId, null,
                    String.format("{\"hour\":%d,\"dayOfWeek\":\"%s\"}", hour, dayOfWeek.name()));
        }
    }

    /**
     * 检测SQL注入
     */
    public void detectSqlInjection(String userId, String clientIp, String query) {
        String[] patterns = {
            "union select", "drop table", "delete from", "insert into", "update ",
            "exec(", "execute(", "1=1", "' or '", "\" or \"", "-- ", ";--",
            "xp_cmdshell", "information_schema", "load_file(", "into outfile"
        };
        String lowerQuery = query.toLowerCase();
        
        for (String pattern : patterns) {
            if (lowerQuery.contains(pattern)) {
                createAlert("rule005", "SQL_INJECTION", "CRITICAL",
                        "疑似SQL注入攻击",
                        String.format("检测到可疑查询: %s", query.substring(0, Math.min(100, query.length()))),
                        clientIp, userId, null, 
                        String.format("{\"pattern\":\"%s\"}", pattern));
                return; // 只告警一次
            }
        }
    }

    /**
     * 检测敏感文档访问
     * 同一用户短时间内频繁访问高安全等级文档
     */
    public void detectSensitiveDocAccess(String userId, String clientIp, 
                                          String documentId, int docSecurityLevel) {
        if (docSecurityLevel >= 3) {
            String key = ACCESS_COUNT_PREFIX + "sensitive:" + userId;
            Long count = redisTemplate.opsForValue().increment(key);
            
            if (count != null && count == 1) {
                redisTemplate.expire(key, 1, TimeUnit.HOURS);
            }
            
            if (count != null && count == SENSITIVE_DOC_THRESHOLD) {
                createAlert("rule004", "SENSITIVE_DOC_ACCESS", "MEDIUM",
                        "敏感文档频繁访问",
                        String.format("用户 %s 1小时内访问%d个敏感/机密文档", userId, count),
                        clientIp, userId, documentId,
                        String.format("{\"docSecurityLevel\":%d,\"accessCount\":%d}", 
                                docSecurityLevel, count));
            }
        }
    }

    /**
     * 检测跨部门访问
     */
    public void detectCrossDeptAccess(String userId, String clientIp, 
                                       String userDeptId, String targetDeptId) {
        if (!userDeptId.equals(targetDeptId)) {
            String key = ACCESS_COUNT_PREFIX + "crossdept:" + userId + ":" + targetDeptId;
            Long count = redisTemplate.opsForValue().increment(key);
            
            if (count != null && count == 1) {
                redisTemplate.expire(key, 1, TimeUnit.HOURS);
            }
            
            if (count != null && count == 5) {
                createAlert("rule006", "CROSS_DEPT_ACCESS", "MEDIUM",
                        "跨部门频繁访问",
                        String.format("用户 %s (部门:%s) 1小时内%d次访问部门 %s 的数据",
                                userId, userDeptId, count, targetDeptId),
                        clientIp, userId, null,
                        String.format("{\"userDept\":\"%s\",\"targetDept\":\"%s\",\"attempts\":%d}", 
                                userDeptId, targetDeptId, count));
            }
        }
    }

    /**
     * 综合实时安全检测
     * 在每次RAG查询时调用，进行全方位安全检测
     */
    public void performRealtimeDetection(String userId, String clientIp, 
                                          String query, int userSecurityLevel,
                                          String userDeptId, String targetDeptId) {
        try {
            // 1. 高频查询检测
            detectHighFrequencyQuery(userId, clientIp);
            
            // 2. SQL注入检测
            detectSqlInjection(userId, clientIp, query);
            
            // 3. 非工作时间检测
            detectOffHoursAccess(userId, clientIp);
            
            // 4. 跨部门访问检测
            if (userDeptId != null && targetDeptId != null) {
                detectCrossDeptAccess(userId, clientIp, userDeptId, targetDeptId);
            }
        } catch (Exception e) {
            log.error("实时安全检测异常: userId={}, error={}", userId, e.getMessage());
        }
    }
}