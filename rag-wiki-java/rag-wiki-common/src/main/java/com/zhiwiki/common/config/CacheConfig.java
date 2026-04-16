package com.zhiwiki.common.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring Cache多级缓存配置
 * 使用Redis作为缓存后端，按业务区分TTL
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // JSON序列化器
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        om.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        om.registerModule(new JavaTimeModule());

        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(om);

        // 默认缓存配置：10分钟TTL
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer))
                .disableCachingNullValues();

        // 各业务缓存区的个性化TTL
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();

        // 用户信息缓存 - 30分钟
        cacheConfigs.put("user", defaultConfig.entryTtl(Duration.ofMinutes(30)));

        // 权限缓存 - 15分钟（权限变更需较快感知）
        cacheConfigs.put("permission", defaultConfig.entryTtl(Duration.ofMinutes(15)));

        // 角色缓存 - 30分钟
        cacheConfigs.put("role", defaultConfig.entryTtl(Duration.ofMinutes(30)));

        // 部门树缓存 - 1小时（部门变更少）
        cacheConfigs.put("dept", defaultConfig.entryTtl(Duration.ofHours(1)));

        // 系统配置缓存 - 1小时
        cacheConfigs.put("config", defaultConfig.entryTtl(Duration.ofHours(1)));

        // LLM模型配置缓存 - 30分钟
        cacheConfigs.put("llm-config", defaultConfig.entryTtl(Duration.ofMinutes(30)));

        // 租户缓存 - 1小时
        cacheConfigs.put("tenant", defaultConfig.entryTtl(Duration.ofHours(1)));

        // 知识库空间缓存 - 15分钟
        cacheConfigs.put("space", defaultConfig.entryTtl(Duration.ofMinutes(15)));

        // 统计数据缓存 - 5分钟
        cacheConfigs.put("statistics", defaultConfig.entryTtl(Duration.ofMinutes(5)));

        // 安全告警规则缓存 - 30分钟
        cacheConfigs.put("alert-rule", defaultConfig.entryTtl(Duration.ofMinutes(30)));

        log.info("Redis CacheManager初始化完成, 缓存区: {}", cacheConfigs.keySet());

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .transactionAware()
                .build();
    }
}
