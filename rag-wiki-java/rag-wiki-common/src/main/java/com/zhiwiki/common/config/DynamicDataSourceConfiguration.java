package com.zhiwiki.common.config;

import com.zhiwiki.common.config.DynamicDataSourceContextHolder.DataSourceType;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 动态数据源自动配置
 * 
 * 仅在配置了 spring.datasource.slave.url 时生效，
 * 未配置从库时使用默认单数据源，不影响现有部署。
 * 
 * 启用方式：在application.yml中配置：
 *   spring:
 *     datasource:
 *       master:
 *         url: jdbc:mysql://master:3306/rag_wiki
 *         username: root
 *         password: xxx
 *       slave:
 *         url: jdbc:mysql://slave:3306/rag_wiki
 *         username: root
 *         password: xxx
 *       read-write-separation:
 *         enabled: true
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "spring.datasource.read-write-separation", name = "enabled", havingValue = "true")
public class DynamicDataSourceConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.master.hikari")
    public DataSource masterDataSource() {
        log.info("初始化主库数据源 (MASTER)");
        return new HikariDataSource();
    }

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.slave.hikari")
    public DataSource slaveDataSource() {
        log.info("初始化从库数据源 (SLAVE)");
        return new HikariDataSource();
    }

    @Bean
    public DynamicRoutingDataSource dynamicDataSource(DataSource masterDataSource, DataSource slaveDataSource) {
        DynamicRoutingDataSource routingDataSource = new DynamicRoutingDataSource();
        
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DataSourceType.MASTER, masterDataSource);
        targetDataSources.put(DataSourceType.SLAVE, slaveDataSource);
        
        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(masterDataSource);
        
        log.info("动态数据源配置完成: MASTER={}, SLAVE={}", 
                masterDataSource, slaveDataSource);
        return routingDataSource;
    }
}
