package com.zhiwiki.common.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Elasticsearch配置类
 * 
 * 支持两种模式：
 * 1. 生产模式（spring.elasticsearch.uris已配置）：自动创建ElasticsearchClient
 * 2. 开发/降级模式（未配置或es.enabled=false）：不创建Client，审计日志仅写Redis
 * 
 * 优雅降级策略：
 * - ES连接失败不影响业务，审计日志自动降级为仅Redis存储
 * - 通过@ConditionalOnProperty控制Client Bean的创建
 */
@Slf4j
@Configuration
public class ElasticsearchConfig {

    @Value("${spring.elasticsearch.uris:}")
    private String esUris;

    @Value("${spring.elasticsearch.username:}")
    private String username;

    @Value("${spring.elasticsearch.password:}")
    private String password;

    @Value("${spring.elasticsearch.connection-timeout:5s}")
    private String connectionTimeout;

    @Value("${spring.elasticsearch.socket-timeout:30s}")
    private String socketTimeout;

    /**
     * 创建ElasticsearchClient
     * 仅在es.enabled=true或spring.elasticsearch.uris非空时创建
     */
    @Bean
    @ConditionalOnProperty(name = "rag-wiki.elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
    @ConditionalOnMissingBean(ElasticsearchClient.class)
    public ElasticsearchClient elasticsearchClient() {
        log.info("初始化Elasticsearch客户端: uris={}", esUris);

        try {
            // 解析ES地址
            String[] uriArray = esUris.split(",");
            HttpHost[] hosts = new HttpHost[uriArray.length];
            for (int i = 0; i < uriArray.length; i++) {
                String uri = uriArray[i].trim();
                String scheme = uri.startsWith("https") ? "https" : "http";
                String hostPort = uri.replaceFirst("^https?://", "");
                String[] parts = hostPort.split(":");
                String host = parts[0];
                int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9200;
                hosts[i] = new HttpHost(scheme, host, port);
            }

            RestClientBuilder builder = RestClient.builder(hosts);

            // 认证配置
            if (username != null && !username.isEmpty()) {
                BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(
                        AuthScope.ANY,
                        new UsernamePasswordCredentials(username, password != null ? password : "")
                );
                builder.setHttpClientConfigCallback(httpClientBuilder -> 
                        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
                );
            }

            // 超时配置
            builder.setRequestConfigCallback(requestConfigBuilder -> {
                requestConfigBuilder.setConnectTimeout(parseTimeoutMillis(connectionTimeout, 5000));
                requestConfigBuilder.setSocketTimeout(parseTimeoutMillis(socketTimeout, 30000));
                requestConfigBuilder.setConnectionRequestTimeout(5000);
                return requestConfigBuilder;
            });

            // Jackson映射器（支持Java 8时间类型）
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            RestClientTransport transport = new RestClientTransport(
                    builder.build(), new JacksonJsonpMapper(mapper));

            ElasticsearchClient client = new ElasticsearchClient(transport);
            log.info("Elasticsearch客户端初始化成功");
            return client;

        } catch (Exception e) {
            log.error("Elasticsearch客户端初始化失败，审计日志将降级为仅Redis存储: {}", e.getMessage());
            return null;
        }
    }

    private int parseTimeoutMillis(String timeout, int defaultMillis) {
        try {
            if (timeout == null || timeout.isEmpty()) return defaultMillis;
            String num = timeout.replaceAll("[^0-9]", "");
            return Integer.parseInt(num);
        } catch (Exception e) {
            return defaultMillis;
        }
    }
}
