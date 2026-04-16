package com.zhiwiki.common.audit;

import com.zhiwiki.common.context.SecurityContextHolder;
import com.zhiwiki.common.tenant.TenantContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.MDC;

/**
 * 全链路审计AOP切面
 * 
 * 自动拦截带有@AuditLog注解的方法，记录完整的操作审计信息：
 * 1. 操作人信息（userId、username、deptId、tenantId）
 * 2. 操作信息（action、module、description）
 * 3. 请求信息（IP、URL、方法名、参数）
 * 4. 执行信息（耗时、是否成功、异常信息）
 * 5. 安全信息（安全等级、是否关键操作）
 * 
 * 日志存储策略：
 * - 默认输出到SLF4J日志（logback）
 * - 可扩展为写入Elasticsearch、Kafka等
 */
@Slf4j
@Aspect
@Component
public class AuditLogAspect {

    private final AuditLogStorageService storageService;

    public AuditLogAspect(AuditLogStorageService storageService) {
        this.storageService = storageService;
    }

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        // 1. 构建审计记录
        AuditRecord record = buildAuditRecord(joinPoint, auditLog);
        long startTime = System.currentTimeMillis();

        try {
            // 2. 执行业务方法
            Object result = joinPoint.proceed();

            // 3. 记录成功信息
            record.setSuccess(true);
            record.setDuration(System.currentTimeMillis() - startTime);

            if (auditLog.logResult() && result != null) {
                String resultStr = truncate(String.valueOf(result), 2000);
                record.setResult(resultStr);
            }

            return result;
        } catch (Throwable e) {
            // 4. 记录失败信息
            record.setSuccess(false);
            record.setDuration(System.currentTimeMillis() - startTime);
            record.setErrorMsg(truncate(e.getMessage(), 1000));

            // 关键操作失败需要额外告警
            if (auditLog.critical()) {
                log.error("[关键操作失败] action={}, user={}, error={}", 
                        auditLog.action(), record.getUserId(), e.getMessage());
            }

            throw e;
        } finally {
            // 5. 输出审计日志
            outputAuditLog(record, auditLog);
        }
    }

    /**
     * 构建审计记录
     */
    private AuditRecord buildAuditRecord(ProceedingJoinPoint joinPoint, AuditLog auditLog) {
        AuditRecord record = new AuditRecord();
        // 优先从MDC获取SkyWalking traceId，降级为UUID
        String traceId = MDC.get("traceId");
        record.setTraceId(traceId != null ? traceId : UUID.randomUUID().toString().replace("-", ""));
        record.setTimestamp(LocalDateTime.now());

        // 操作人信息
        record.setUserId(SecurityContextHolder.getUserId());
        record.setUsername(SecurityContextHolder.getUsername());
        record.setDeptId(SecurityContextHolder.getDeptId());
        record.setTenantId(SecurityContextHolder.getTenantId());
        record.setSecurityLevel(SecurityContextHolder.getSecurityLevel());

        // 操作信息
        record.setAction(auditLog.action());
        record.setModule(auditLog.module());
        record.setDescription(auditLog.description());
        record.setCritical(auditLog.critical());

        // 请求信息
        ServletRequestAttributes attributes = 
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            record.setIp(getClientIp(request));
            record.setRequestUrl(request.getRequestURL().toString());
            record.setHttpMethod(request.getMethod());
        }

        // 方法信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        record.setClassName(signature.getDeclaringTypeName());
        record.setMethodName(signature.getName());

        // 请求参数
        if (auditLog.logParams()) {
            String params = buildParams(joinPoint.getArgs(), signature.getParameterNames());
            record.setParams(truncate(params, 4000));
        }

        return record;
    }

    /**
     * 构建参数字符串
     */
    private String buildParams(Object[] args, String[] paramNames) {
        if (args == null || args.length == 0) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < args.length; i++) {
            // 跳过不可序列化的参数
            if (args[i] instanceof HttpServletRequest || args[i] instanceof HttpServletResponse) {
                continue;
            }
            if (i > 0) {
                sb.append(", ");
            }
            String name = (paramNames != null && i < paramNames.length) ? paramNames[i] : "arg" + i;
            sb.append(name).append("=").append(String.valueOf(args[i]));
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 输出审计日志
     * 默认输出到SLF4J + 持久化到Redis/ES
     */
    private void outputAuditLog(AuditRecord record, AuditLog auditLog) {
        // 输出结构化审计日志
        log.info("[AUDIT] traceId={}, action={}, module={}, user={}, dept={}, tenant={}, " +
                "ip={}, url={}, method={}, duration={}ms, success={}, securityLevel={}, critical={}" +
                "{}{}",
                record.getTraceId(), record.getAction(), record.getModule(),
                record.getUserId(), record.getDeptId(), record.getTenantId(),
                record.getIp(), record.getRequestUrl(), record.getHttpMethod(),
                record.getDuration(), record.isSuccess(), record.getSecurityLevel(),
                record.isCritical(),
                record.getErrorMsg() != null ? ", error=" + record.getErrorMsg() : "",
                record.getResult() != null ? ", result=" + record.getResult() : ""
        );

        // 异步持久化到存储（Redis热数据 + ES冷数据）
        try {
            storageService.store(record);
        } catch (Exception e) {
            log.warn("审计日志持久化失败（不影响业务）: traceId={}, error={}", 
                    record.getTraceId(), e.getMessage());
        }
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多代理时取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 截断字符串
     */
    private String truncate(String str, int maxLen) {
        if (str == null) {
            return null;
        }
        return str.length() > maxLen ? str.substring(0, maxLen) + "..." : str;
    }

    /**
     * 审计记录DTO
     */
    @Data
    public static class AuditRecord implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 链路追踪ID */
        private String traceId;
        /** 操作时间 */
        private LocalDateTime timestamp;
        /** 操作人ID */
        private String userId;
        /** 操作人用户名 */
        private String username;
        /** 操作人部门ID */
        private String deptId;
        /** 租户ID */
        private String tenantId;
        /** 安全等级 */
        private int securityLevel;
        /** 操作类型 */
        private String action;
        /** 功能模块 */
        private String module;
        /** 操作描述 */
        private String description;
        /** 是否关键操作 */
        private boolean critical;
        /** 客户端IP */
        private String ip;
        /** 请求URL */
        private String requestUrl;
        /** HTTP方法 */
        private String httpMethod;
        /** 类名 */
        private String className;
        /** 方法名 */
        private String methodName;
        /** 请求参数 */
        private String params;
        /** 返回结果 */
        private String result;
        /** 执行耗时(ms) */
        private long duration;
        /** 是否成功 */
        private boolean success;
        /** 错误信息 */
        private String errorMsg;
    }
}
