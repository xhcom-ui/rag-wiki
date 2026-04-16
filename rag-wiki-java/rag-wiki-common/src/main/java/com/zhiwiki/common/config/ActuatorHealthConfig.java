package com.zhiwiki.common.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 健康检查配置
 * 
 * 自定义健康检查指标：
 * 1. 数据库连接健康检查
 * 2. Redis连接健康检查
 * 3. 磁盘空间健康检查
 * 
 * Actuator端点：
 * - /actuator/health - 整体健康状态
 * - /actuator/health/liveness - 存活探针（K8s用）
 * - /actuator/health/readiness - 就绪探针（K8s用）
 * - /actuator/prometheus - Prometheus指标
 * - /actuator/info - 应用信息
 */
@Configuration
public class ActuatorHealthConfig {

    /**
     * 自定义数据库健康检查
     */
    @Bean
    public HealthIndicator databaseHealthIndicator(DataSource dataSource) {
        return () -> {
            try (Connection conn = dataSource.getConnection()) {
                if (conn.isValid(3)) {
                    return Health.up()
                            .withDetail("database", "MySQL")
                            .withDetail("status", "UP")
                            .build();
                } else {
                    return Health.down()
                            .withDetail("database", "MySQL")
                            .withDetail("status", "CONNECTION_INVALID")
                            .build();
                }
            } catch (Exception e) {
                return Health.down()
                        .withDetail("database", "MySQL")
                        .withDetail("error", e.getMessage())
                        .build();
            }
        };
    }

    /**
     * 自定义Redis健康检查
     */
    @Bean
    public HealthIndicator redisHealthIndicator(RedisConnectionFactory connectionFactory) {
        return () -> {
            try {
                String pong = connectionFactory.getConnection().ping();
                if ("PONG".equalsIgnoreCase(pong)) {
                    return Health.up()
                            .withDetail("redis", "Connected")
                            .build();
                } else {
                    return Health.down()
                            .withDetail("redis", "PING failed: " + pong)
                            .build();
                }
            } catch (Exception e) {
                return Health.down()
                        .withDetail("redis", "Error: " + e.getMessage())
                        .build();
            }
        };
    }
}
