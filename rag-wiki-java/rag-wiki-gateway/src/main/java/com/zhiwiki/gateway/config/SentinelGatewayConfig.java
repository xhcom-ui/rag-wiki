package com.zhiwiki.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.exception.SentinelGatewayBlockExceptionHandler;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Gateway Sentinel限流配置
 * 
 * 提供：
 * 1. API分组限流规则
 * 2. 路由级别QPS限制
 * 3. 自定义限流降级JSON响应（替代默认HTML）
 */
@Slf4j
@Configuration
public class SentinelGatewayConfig {

    private final List<ViewResolver> viewResolvers;
    private final ServerCodecConfigurer serverCodecConfigurer;

    public SentinelGatewayConfig(
            ObjectProvider<List<ViewResolver>> viewResolversProvider,
            ServerCodecConfigurer serverCodecConfigurer) {
        this.viewResolvers = viewResolversProvider.getIfAvailable(Collections::emptyList);
        this.serverCodecConfigurer = serverCodecConfigurer;
    }

    /**
     * Sentinel网关异常处理器
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SentinelGatewayBlockExceptionHandler sentinelGatewayBlockExceptionHandler() {
        return new SentinelGatewayBlockExceptionHandler(viewResolvers, serverCodecConfigurer);
    }

    /**
     * Sentinel网关过滤器
     */
    @Bean
    @Order(-1)
    public GlobalFilter sentinelGatewayFilter() {
        return new SentinelGatewayFilter();
    }

    /**
     * 初始化限流规则和自定义降级响应
     */
    @PostConstruct
    public void init() {
        initCustomBlockHandler();
        initApiGroups();
        initGatewayRules();
        log.info("Sentinel Gateway限流规则初始化完成");
    }

    /**
     * 自定义限流降级响应 - 返回JSON格式
     */
    private void initCustomBlockHandler() {
        GatewayCallbackManager.setBlockHandler(new BlockRequestHandler() {
            @Override
            public Mono<ServerResponse> handleRequest(ServerWebExchange exchange, Throwable throwable) {
                Map<String, Object> result = new HashMap<>();
                result.put("code", 429);
                result.put("message", "请求过于频繁，请稍后重试");
                result.put("timestamp", System.currentTimeMillis());

                return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue(result));
            }
        });
    }

    /**
     * 定义API分组 - 对不同API路径设置不同限流策略
     */
    private void initApiGroups() {
        Set<ApiDefinition> definitions = new HashSet<>();

        // AI问答接口组（消耗资源最多，限流最严格）
        definitions.add(new ApiDefinition("ai-query-api")
                .setPredicateItems(new HashSet<ApiPredicateItem>() {{
                    add(new ApiPathPredicateItem().setPattern("/api/ai/rag/**")
                            .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX));
                    add(new ApiPathPredicateItem().setPattern("/api/ai/agent/**")
                            .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX));
                }}));

        // 文档上传接口组
        definitions.add(new ApiDefinition("upload-api")
                .setPredicateItems(new HashSet<ApiPredicateItem>() {{
                    add(new ApiPathPredicateItem().setPattern("/api/document/upload")
                            .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_EXACT));
                    add(new ApiPathPredicateItem().setPattern("/api/ai/document/upload")
                            .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_EXACT));
                }}));

        // 认证接口组（防暴力破解）
        definitions.add(new ApiDefinition("auth-api")
                .setPredicateItems(new HashSet<ApiPredicateItem>() {{
                    add(new ApiPathPredicateItem().setPattern("/api/auth/login")
                            .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_EXACT));
                }}));

        GatewayApiDefinitionManager.loadApiDefinitions(definitions);
    }

    /**
     * 配置限流规则
     */
    private void initGatewayRules() {
        Set<GatewayFlowRule> rules = new HashSet<>();

        // === 路由级别限流 ===

        // 认证服务: 200 QPS
        rules.add(new GatewayFlowRule("rag-wiki-auth")
                .setCount(200).setIntervalSec(1));

        // 文档服务: 500 QPS
        rules.add(new GatewayFlowRule("rag-wiki-document")
                .setCount(500).setIntervalSec(1));

        // 审批服务: 100 QPS
        rules.add(new GatewayFlowRule("rag-wiki-approval")
                .setCount(100).setIntervalSec(1));

        // 审计服务: 300 QPS
        rules.add(new GatewayFlowRule("rag-wiki-audit")
                .setCount(300).setIntervalSec(1));

        // AI服务: 50 QPS
        rules.add(new GatewayFlowRule("rag-wiki-ai")
                .setCount(50).setIntervalSec(1));

        // === API分组限流 ===

        // AI问答：30 QPS（最严格，LLM资源昂贵）
        rules.add(new GatewayFlowRule("ai-query-api")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setCount(30).setIntervalSec(1));

        // 文档上传：10 QPS（防刷）
        rules.add(new GatewayFlowRule("upload-api")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setCount(10).setIntervalSec(1));

        // 登录接口：20次/分钟（防暴力破解）
        rules.add(new GatewayFlowRule("auth-api")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setCount(20).setIntervalSec(60));

        GatewayRuleManager.loadRules(rules);
        log.info("Sentinel限流规则加载: {}条", rules.size());
    }
}
