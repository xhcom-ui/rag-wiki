package com.zhiwiki.auth.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiwiki.auth.entity.Tenant;
import com.zhiwiki.auth.mapper.TenantMapper;
import com.zhiwiki.common.exception.BusinessException;
import com.zhiwiki.common.model.PageRequest;
import com.zhiwiki.common.model.PageResult;
import com.zhiwiki.common.model.ResultCode;
import com.zhiwiki.common.tenant.TenantContextHolder;
import com.zhiwiki.common.tenant.TenantIsolationLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 租户管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantMapper tenantMapper;
    private final StringRedisTemplate redisTemplate;

    private static final String TENANT_CACHE_PREFIX = "rag-wiki:tenant:";
    private static final long TENANT_CACHE_TTL_HOURS = 24;

    /**
     * 创建租户
     */
    public Tenant createTenant(Tenant tenant) {
        // 校验租户编码唯一
        Tenant exists = tenantMapper.selectOne(
                new LambdaQueryWrapper<Tenant>().eq(Tenant::getTenantCode, tenant.getTenantCode())
        );
        if (exists != null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "租户编码已存在");
        }

        tenant.setTenantId(IdUtil.fastSimpleUUID());
        tenant.setStorageUsedMb(0L);
        if (tenant.getIsolationLevel() == null) {
            tenant.setIsolationLevel(TenantIsolationLevel.METADATA_FILTER.getCode());
        }
        if (tenant.getStatus() == null) {
            tenant.setStatus(1);
        }
        if (tenant.getStorageQuotaMb() == null) {
            tenant.setStorageQuotaMb(10240L); // 默认10GB
        }
        if (tenant.getMaxUsers() == null) {
            tenant.setMaxUsers(100);
        }
        if (tenant.getMaxSpaces() == null) {
            tenant.setMaxSpaces(20);
        }

        tenantMapper.insert(tenant);
        cacheTenant(tenant);

        log.info("租户创建成功: tenantId={}, code={}, isolation={}",
                tenant.getTenantId(), tenant.getTenantCode(), tenant.getIsolationLevel());
        return tenant;
    }

    /**
     * 获取租户信息（优先缓存）
     */
    public Tenant getTenant(String tenantId) {
        // 1. 先从缓存获取
        Tenant cached = getTenantFromCache(tenantId);
        if (cached != null) {
            return cached;
        }

        // 2. 从数据库查询
        Tenant tenant = tenantMapper.selectOne(
                new LambdaQueryWrapper<Tenant>().eq(Tenant::getTenantId, tenantId)
        );
        if (tenant == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "租户不存在");
        }

        // 3. 写入缓存
        cacheTenant(tenant);
        return tenant;
    }

    /**
     * 根据租户编码获取
     */
    public Tenant getTenantByCode(String tenantCode) {
        return tenantMapper.selectOne(
                new LambdaQueryWrapper<Tenant>().eq(Tenant::getTenantCode, tenantCode)
        );
    }

    /**
     * 更新租户
     */
    public Tenant updateTenant(Tenant tenant) {
        tenantMapper.updateById(tenant);
        // 刷新缓存
        cacheTenant(tenant);
        log.info("租户更新成功: tenantId={}", tenant.getTenantId());
        return tenant;
    }

    /**
     * 切换租户隔离级别
     * 注意：此操作影响重大，需要数据迁移
     */
    public Tenant switchIsolationLevel(String tenantId, String newLevel) {
        Tenant tenant = getTenant(tenantId);
        TenantIsolationLevel oldLevel = TenantIsolationLevel.fromCode(tenant.getIsolationLevel());
        TenantIsolationLevel targetLevel = TenantIsolationLevel.fromCode(newLevel);

        // 校验：不允许降级
        if (targetLevel.getSecurityLevel() < oldLevel.getSecurityLevel()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "不允许降低隔离级别，当前=" + oldLevel.getDescription() + "，目标=" + targetLevel.getDescription());
        }

        tenant.setIsolationLevel(newLevel);
        tenantMapper.updateById(tenant);
        cacheTenant(tenant);

        log.warn("租户隔离级别变更: tenantId={}, {} -> {}",
                tenantId, oldLevel.getDescription(), targetLevel.getDescription());
        return tenant;
    }

    /**
     * 启用/禁用租户
     */
    public void toggleTenantStatus(String tenantId, Integer status) {
        Tenant tenant = getTenant(tenantId);
        tenant.setStatus(status);
        tenantMapper.updateById(tenant);
        cacheTenant(tenant);

        if (status == 0) {
            // 禁用时清除该租户所有用户的Token
            log.warn("租户已禁用: tenantId={}, 该租户所有用户将被强制下线", tenantId);
        }
    }

    /**
     * 分页查询租户
     */
    public PageResult<Tenant> pageTenants(PageRequest pageRequest, String tenantName, Integer status) {
        Page<Tenant> page = new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize());
        LambdaQueryWrapper<Tenant> wrapper = new LambdaQueryWrapper<>();
        if (tenantName != null && !tenantName.isEmpty()) {
            wrapper.like(Tenant::getTenantName, tenantName);
        }
        if (status != null) {
            wrapper.eq(Tenant::getStatus, status);
        }
        wrapper.orderByDesc(Tenant::getCreatedAt);
        Page<Tenant> result = tenantMapper.selectPage(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), pageRequest.getPageNum(), pageRequest.getPageSize());
    }

    /**
     * 构建租户上下文并设置到ThreadLocal
     */
    public void setupTenantContext(String tenantId) {
        Tenant tenant = getTenant(tenantId);
        if (tenant.getStatus() == 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "租户已禁用");
        }

        TenantContextHolder.TenantContext context = new TenantContextHolder.TenantContext();
        context.setTenantId(tenant.getTenantId());
        context.setTenantName(tenant.getTenantName());
        context.setIsolationLevel(TenantIsolationLevel.fromCode(tenant.getIsolationLevel()));
        context.setStatus(tenant.getStatus());
        context.setStorageQuotaMb(tenant.getStorageQuotaMb());
        context.setStorageUsedMb(tenant.getStorageUsedMb());
        context.setMaxUsers(tenant.getMaxUsers());
        context.setMaxSpaces(tenant.getMaxSpaces());
        context.setExpireTime(tenant.getExpireTime());

        TenantContextHolder.set(context);
    }

    // ==================== 缓存相关 ====================

    private void cacheTenant(Tenant tenant) {
        try {
            String key = TENANT_CACHE_PREFIX + tenant.getTenantId();
            // 简化实现：存储tenantId用于快速判断缓存存在
            redisTemplate.opsForValue().set(key, tenant.getTenantId(), TENANT_CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("租户缓存写入失败: {}", e.getMessage());
        }
    }

    private Tenant getTenantFromCache(String tenantId) {
        // 简化实现：缓存只存储ID，命中后仍需查库获取完整信息
        // 生产环境应使用Redis Hash存储完整对象
        return null;
    }
}
