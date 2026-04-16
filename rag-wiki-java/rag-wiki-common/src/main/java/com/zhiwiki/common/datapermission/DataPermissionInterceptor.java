package com.zhiwiki.common.datapermission;

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.zhiwiki.common.tenant.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MyBatis Plus 增强型数据权限拦截器
 * 
 * 三层RLS（Row Level Security）自动注入：
 * 1. 租户层：tenant_id = ? （强制，从TenantContextHolder获取）
 * 2. 安全等级层：security_level <= ? （从DataPermissionContext获取）
 * 3. 部门/角色层：dept_id IN (...) 或 user_id = ?（从DataPermissionContext获取）
 */
@Slf4j
public class DataPermissionInterceptor implements InnerInterceptor {

    /**
     * 缓存方法上的数据权限注解
     */
    private final Map<String, DataPermission> dataPermissionCache = new ConcurrentHashMap<>();

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, 
                           RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) 
            throws SQLException {
        
        DataPermissionContext context = DataPermissionContext.getContext();
        if (context == null) {
            return;
        }

        // 获取当前方法的 DataPermission 注解
        DataPermission dataPermission = getDataPermission(ms.getId());
        if (dataPermission == null || !dataPermission.enabled()) {
            return;
        }

        // 构建三层RLS过滤SQL
        StringBuilder filterBuilder = new StringBuilder();

        // 第一层：租户隔离（强制，始终生效）
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null && !tenantId.isEmpty()) {
            String prefix = dataPermission.tableAlias().isEmpty() ? "" : dataPermission.tableAlias() + ".";
            filterBuilder.append(prefix).append("tenant_id = '").append(tenantId).append("'");
        }

        // 第二层+第三层：业务数据权限
        String businessFilter = context.buildFilterSql(
                dataPermission.tableAlias(),
                dataPermission.deptField(),
                dataPermission.userField(),
                dataPermission.securityLevelField()
        );

        if (businessFilter != null && !businessFilter.isEmpty()) {
            if (filterBuilder.length() > 0) {
                filterBuilder.append(" AND ");
            }
            filterBuilder.append(businessFilter);
        }

        String filterSql = filterBuilder.toString();
        if (filterSql.isEmpty()) {
            return;
        }

        // 解析并修改SQL
        try {
            String sql = boundSql.getSql();
            Select select = (Select) CCJSqlParserUtil.parse(sql);
            PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

            Expression where = plainSelect.getWhere();
            Expression filterExpression = CCJSqlParserUtil.parseCondExpression(filterSql);

            if (where == null) {
                plainSelect.setWhere(filterExpression);
            } else {
                plainSelect.setWhere(new AndExpression(where, filterExpression));
            }

            // 更新SQL
            PluginUtils.MPBoundSql mpBoundSql = PluginUtils.mpBoundSql(boundSql);
            mpBoundSql.sql(select.toString());
            
            log.debug("数据权限过滤(RLS): msId={}, filterSql={}", ms.getId(), filterSql);
            
        } catch (Exception e) {
            log.error("数据权限拦截器处理失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取方法上的 DataPermission 注解
     */
    private DataPermission getDataPermission(String msId) {
        return dataPermissionCache.computeIfAbsent(msId, id -> {
            try {
                String className = id.substring(0, id.lastIndexOf("."));
                String methodName = id.substring(id.lastIndexOf(".") + 1);
                Class<?> clazz = Class.forName(className);
                
                // 检查类上的注解
                DataPermission classAnnotation = clazz.getAnnotation(DataPermission.class);
                
                // 检查方法上的注解
                for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
                    if (method.getName().equals(methodName)) {
                        DataPermission methodAnnotation = method.getAnnotation(DataPermission.class);
                        return methodAnnotation != null ? methodAnnotation : classAnnotation;
                    }
                }
                
                return classAnnotation;
            } catch (Exception e) {
                log.debug("获取DataPermission注解失败: {}", e.getMessage());
                return null;
            }
        });
    }
}
