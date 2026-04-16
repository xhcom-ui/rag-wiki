package com.zhiwiki.common.config;

import com.zhiwiki.common.config.DynamicDataSourceContextHolder.DataSourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * 动态路由数据源
 * 
 * 继承Spring的AbstractRoutingDataSource，
 * 根据DynamicDataSourceContextHolder中的ThreadLocal值
 * 决定当前请求使用主库还是从库。
 * 
 * 配置示例（application.yml）：
 * spring:
 *   datasource:
 *     master:
 *       url: jdbc:mysql://master-host:3306/rag_wiki
 *       username: root
 *       password: xxx
 *     slave:
 *       url: jdbc:mysql://slave-host:3306/rag_wiki
 *       username: root
 *       password: xxx
 */
@Slf4j
public class DynamicRoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        DataSourceType type = DynamicDataSourceContextHolder.getDataSourceType();
        log.debug("当前数据源路由: {}", type);
        return type;
    }
}
