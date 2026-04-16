package com.zhiwiki.audit.aspect;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import cn.hutool.json.JSONUtil;
import com.zhiwiki.audit.annotation.OpLog;
import com.zhiwiki.audit.entity.OperationLog;
import com.zhiwiki.audit.mapper.OperationLogMapper;
import com.zhiwiki.common.context.SecurityContextHolder;
import com.zhiwiki.common.context.UserPermissionContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 操作日志切面
 * 
 * 自动拦截带有 @OperationLog 注解的方法，记录操作日志
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogMapper operationLogMapper;

    /**
     * 切点：所有带有 @OpLog 注解的方法
     */
    @Pointcut("@annotation(com.zhiwiki.audit.annotation.OpLog)")
    public void operationLogPointcut() {
    }

    /**
     * 方法执行后记录日志
     */
    @AfterReturning(pointcut = "operationLogPointcut()", returning = "result")
    public void doAfterReturning(JoinPoint joinPoint, Object result) {
        handleLog(joinPoint, null, result);
    }

    /**
     * 方法异常后记录日志
     */
    @AfterThrowing(pointcut = "operationLogPointcut()", throwing = "e")
    public void doAfterThrowing(JoinPoint joinPoint, Exception e) {
        handleLog(joinPoint, e, null);
    }

    /**
     * 处理日志记录
     */
    private void handleLog(JoinPoint joinPoint, Exception e, Object result) {
        try {
            // 获取注解
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            OpLog opLogAnnotation = signature.getMethod().getAnnotation(OpLog.class);
            if (opLogAnnotation == null) {
                return;
            }

            // 构建日志对象
            OperationLog operationLog = new OperationLog();
            operationLog.setLogId(IdUtil.fastSimpleUUID());
            operationLog.setModule(opLogAnnotation.module());
            operationLog.setOperationType(opLogAnnotation.type().name());
            operationLog.setDescription(opLogAnnotation.description());
            operationLog.setStatus(e == null ? 1 : 0);
            operationLog.setCreatedAt(LocalDateTime.now());

            // 获取请求信息
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                operationLog.setRequestUrl(request.getRequestURI());
                operationLog.setRequestMethod(request.getMethod());
                operationLog.setClientIp(getClientIp(request));
                operationLog.setBrowser(getBrowser(request));
                operationLog.setOs(getOs(request));

                // 设置请求方法名
                operationLog.setMethod(joinPoint.getTarget().getClass().getName() + "." + signature.getName());

                // 设置请求参数
                if (opLogAnnotation.saveParams()) {
                    String params = getRequestParams(joinPoint, opLogAnnotation.excludeParams());
                    operationLog.setRequestParams(params);
                }
            }

            // 设置用户信息
            UserPermissionContext userContext = SecurityContextHolder.get();
            if (userContext != null) {
                operationLog.setUserId(userContext.getUserId());
                operationLog.setUsername(userContext.getUsername());
                operationLog.setRealName(userContext.getRealName());
                operationLog.setDeptId(userContext.getDeptId());
            }

            // 设置返回结果
            if (opLogAnnotation.saveResult() && result != null) {
                String resultStr = JSONUtil.toJsonStr(result);
                // 限制结果长度
                if (resultStr.length() > 2000) {
                    resultStr = resultStr.substring(0, 2000) + "...";
                }
                operationLog.setResponseResult(resultStr);
            }

            // 设置错误信息
            if (e != null) {
                String errorMsg = e.getMessage();
                if (errorMsg != null && errorMsg.length() > 500) {
                    errorMsg = errorMsg.substring(0, 500);
                }
                operationLog.setErrorMsg(errorMsg);
            }

            // 异步保存日志
            saveLogAsync(operationLog);

        } catch (Exception ex) {
            log.error("记录操作日志异常", ex);
        }
    }

    /**
     * 异步保存日志
     */
    @Async
    public void saveLogAsync(OperationLog operationLog) {
        try {
            operationLogMapper.insert(operationLog);
        } catch (Exception e) {
            log.error("保存操作日志失败", e);
        }
    }

    /**
     * 获取请求参数
     */
    private String getRequestParams(JoinPoint joinPoint, String[] excludeParams) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        if (paramNames == null || args == null) {
            return "";
        }

        Map<String, Object> params = new HashMap<>();
        Set<String> excludeSet = new HashSet<>(Arrays.asList(excludeParams));

        for (int i = 0; i < paramNames.length; i++) {
            String paramName = paramNames[i];
            Object paramValue = args[i];

            // 排除敏感参数
            if (excludeSet.contains(paramName)) {
                params.put(paramName, "******");
                continue;
            }

            // 排除特殊类型
            if (paramValue instanceof HttpServletRequest
                    || paramValue instanceof HttpServletResponse
                    || paramValue instanceof MultipartFile) {
                continue;
            }

            params.put(paramName, paramValue);
        }

        String json = JSONUtil.toJsonStr(params);
        if (json.length() > 2000) {
            json = json.substring(0, 2000) + "...";
        }
        return json;
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StrUtil.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (StrUtil.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (StrUtil.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (StrUtil.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (StrUtil.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理时取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 获取浏览器类型
     */
    private String getBrowser(HttpServletRequest request) {
        String userAgentStr = request.getHeader("User-Agent");
        if (StrUtil.isEmpty(userAgentStr)) {
            return "Unknown";
        }
        UserAgent userAgent = UserAgentUtil.parse(userAgentStr);
        return userAgent.getBrowser().getName() + " " + userAgent.getVersion();
    }

    /**
     * 获取操作系统
     */
    private String getOs(HttpServletRequest request) {
        String userAgentStr = request.getHeader("User-Agent");
        if (StrUtil.isEmpty(userAgentStr)) {
            return "Unknown";
        }
        UserAgent userAgent = UserAgentUtil.parse(userAgentStr);
        return userAgent.getOs().getName();
    }
}
