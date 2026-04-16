package com.zhiwiki.common.config;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.Filter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.UUID;

/**
 * SkyWalking链路追踪集成配置
 * 
 * 集成方式：
 * 1. Java Agent方式（推荐）：启动参数添加 -javaagent:skywalking-agent.jar
 * 2. 自动将traceId写入MDC，供日志和审计系统使用
 * 3. 自动将traceId写入HTTP响应头，便于前端问题排查
 * 
 * Agent配置（agent/config/agent.config）：
 *   agent.service_name=rag-wiki-auth
 *   collector.backend_service=skywalking-oap:11800
 *   agent.sample_n_per_3_secs=3
 *   agent.span_limit=2000
 * 
 * Docker启动参数：
 *   java -javaagent:/skywalking/agent/skywalking-agent.jar -jar app.jar
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = "org.apache.skywalking.apm.agent.core.context.ContextManager")
@ConditionalOnProperty(prefix = "skywalking", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SkyWalkingConfiguration {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String MDC_TRACE_KEY = "traceId";

    /**
     * TraceId MDC注入过滤器
     * 
     * 将SkyWalking的traceId自动注入到SLF4J MDC中，
     * 日志格式可使用 %X{traceId} 输出traceId。
     * 同时将traceId写入响应头，便于前端和API消费者追踪问题。
     */
    @Bean
    public Filter traceIdMdcFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    javax.servlet.FilterChain filterChain
            ) throws java.io.IOException, javax.servlet.ServletException {
                
                String traceId = extractTraceId(request);
                
                try {
                    // 注入MDC供日志使用
                    MDC.put(MDC_TRACE_KEY, traceId);
                    
                    // 写入响应头
                    response.setHeader(TRACE_ID_HEADER, traceId);
                    
                    filterChain.doFilter(request, response);
                } finally {
                    MDC.remove(MDC_TRACE_KEY);
                }
            }
        };
    }

    /**
     * 提取TraceId
     * 
     * 优先从SkyWalking Agent获取，降级从请求头获取，最后生成UUID
     */
    private String extractTraceId(HttpServletRequest request) {
        // 1. 尝试从SkyWalking Agent获取
        try {
            String swTraceId = org.apache.skywalking.apm.agent.core.context.ContextManager
                    .getGlobalTraceId();
            if (swTraceId != null && !swTraceId.isEmpty() && !"Ignored_Trace".equals(swTraceId)) {
                return swTraceId;
            }
        } catch (NoClassDefFoundError | Exception e) {
            // SkyWalking Agent未加载，降级
        }

        // 2. 从请求头获取（微服务间调用传递）
        String headerTraceId = request.getHeader(TRACE_ID_HEADER);
        if (headerTraceId != null && !headerTraceId.isEmpty()) {
            return headerTraceId;
        }

        // 3. 生成新的TraceId
        return "local-" + UUID.randomUUID().toString().replace("-", "");
    }
}
