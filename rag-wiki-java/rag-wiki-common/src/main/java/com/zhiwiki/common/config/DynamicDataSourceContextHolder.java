package com.zhiwiki.common.config;

import lombok.extern.slf4j.Slf4j;

/**
 * 动态数据源上下文持有器
 * 
 * 基于ThreadLocal实现读写分离路由：
 * - master: 写操作路由到主库
 * - slave: 读操作路由到从库
 * 
 * 使用方式：
 *   DynamicDataSourceContextHolder.setSlave();  // 切换到从库
 *   DynamicDataSourceContextHolder.setMaster(); // 切换到主库
 *   DynamicDataSourceContextHolder.clear();     // 清除（恢复默认）
 */
@Slf4j
public class DynamicDataSourceContextHolder {

    /**
     * 数据源类型枚举
     */
    public enum DataSourceType {
        MASTER,  // 主库 - 写操作
        SLAVE    // 从库 - 读操作
    }

    private static final ThreadLocal<DataSourceType> CONTEXT_HOLDER = new ThreadLocal<>();

    /**
     * 设置当前数据源为主库
     */
    public static void setMaster() {
        CONTEXT_HOLDER.set(DataSourceType.MASTER);
        log.debug("切换数据源: MASTER");
    }

    /**
     * 设置当前数据源为从库
     */
    public static void setSlave() {
        CONTEXT_HOLDER.set(DataSourceType.SLAVE);
        log.debug("切换数据源: SLAVE");
    }

    /**
     * 获取当前数据源类型
     */
    public static DataSourceType getDataSourceType() {
        DataSourceType type = CONTEXT_HOLDER.get();
        return type != null ? type : DataSourceType.MASTER;
    }

    /**
     * 清除当前数据源设置
     */
    public static void clear() {
        CONTEXT_HOLDER.remove();
    }

    /**
     * 是否为从库
     */
    public static boolean isSlave() {
        return getDataSourceType() == DataSourceType.SLAVE;
    }
}
