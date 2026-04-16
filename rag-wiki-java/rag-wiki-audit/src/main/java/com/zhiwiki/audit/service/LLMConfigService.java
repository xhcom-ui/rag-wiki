package com.zhiwiki.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiwiki.audit.dto.LLMConfigRequest;
import com.zhiwiki.audit.entity.LLMConfig;
import com.zhiwiki.audit.mapper.LLMConfigMapper;
import com.zhiwiki.common.exception.BusinessException;
import com.zhiwiki.common.model.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * LLM模型配置服务
 * 
 * 支持多LLM提供商配置管理，包含：
 * - 配置CRUD
 * - 设置默认配置
 * - 获取启用的配置列表
 * - 提供商列表
 * - 配置缓存（Redis）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LLMConfigService {

    private final LLMConfigMapper llmConfigMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CONFIG_CACHE_PREFIX = "llm:config:";
    private static final long CONFIG_CACHE_TTL = 30; // 分钟

    /**
     * 分页查询配置
     */
    public Page<LLMConfig> page(int pageNum, int pageSize, String provider, Integer isEnabled) {
        LambdaQueryWrapper<LLMConfig> wrapper = new LambdaQueryWrapper<>();
        if (provider != null && !provider.isEmpty()) {
            wrapper.eq(LLMConfig::getProvider, provider);
        }
        if (isEnabled != null) {
            wrapper.eq(LLMConfig::getIsEnabled, isEnabled);
        }
        wrapper.orderByDesc(LLMConfig::getIsDefault).orderByDesc(LLMConfig::getCreatedAt);
        return llmConfigMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 获取所有启用的配置
     */
    @Cacheable(value = "llm-config", key = "'enabled-list'")
    public List<LLMConfig> listEnabled() {
        return llmConfigMapper.selectList(
            new LambdaQueryWrapper<LLMConfig>()
                .eq(LLMConfig::getIsEnabled, 1)
                .orderByDesc(LLMConfig::getIsDefault)
        );
    }

    /**
     * 获取配置详情
     */
    public LLMConfig getById(String configId) {
        // 先查缓存
        String cacheKey = CONFIG_CACHE_PREFIX + configId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof LLMConfig) {
            return (LLMConfig) cached;
        }

        LLMConfig config = llmConfigMapper.selectById(configId);
        if (config == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "配置不存在: " + configId);
        }
        redisTemplate.opsForValue().set(cacheKey, config, CONFIG_CACHE_TTL, TimeUnit.MINUTES);
        return config;
    }

    /**
     * 创建配置
     */
    @Transactional
    @CacheEvict(value = "llm-config", allEntries = true)
    public LLMConfig create(LLMConfigRequest request) {
        LLMConfig config = new LLMConfig();
        config.setConfigId(cn.hutool.core.util.IdUtil.fastSimpleUUID());
        config.setConfigName(request.getConfigName());
        config.setProvider(request.getProvider());
        config.setModelName(request.getModelName());
        config.setApiBase(request.getApiBase());
        config.setApiKey(request.getApiKey());
        config.setTemperature(request.getTemperature());
        config.setMaxTokens(request.getMaxTokens());
        config.setIsDefault(Boolean.TRUE.equals(request.getIsDefault()) ? 1 : 0);
        config.setIsEnabled(Boolean.TRUE.equals(request.getIsEnabled()) ? 1 : 0);
        config.setPriority(request.getPriority() != null ? request.getPriority() : 0);
        config.setRemark(request.getRemark());
        config.setExtraConfig(request.getExtraConfig());
        config.setTenantId(request.getTenantId() != null ? request.getTenantId() : "default");

        // 如果设置为默认，清除其他默认
        if (config.getIsDefault() == 1) {
            clearDefaultForProvider(config.getProvider());
        }

        llmConfigMapper.insert(config);
        log.info("LLM配置创建: id={}, name={}, provider={}", config.getId(), config.getConfigName(), config.getProvider());
        return config;
    }

    /**
     * 更新配置
     */
    @Transactional
    @CacheEvict(value = "llm-config", allEntries = true)
    public LLMConfig update(String configId, LLMConfigRequest request) {
        LLMConfig config = llmConfigMapper.selectById(configId);
        if (config == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "配置不存在: " + configId);
        }

        config.setConfigName(request.getConfigName());
        config.setProvider(request.getProvider());
        config.setModelName(request.getModelName());
        config.setApiBase(request.getApiBase());
        config.setApiKey(request.getApiKey());
        config.setTemperature(request.getTemperature());
        config.setMaxTokens(request.getMaxTokens());
        config.setIsDefault(Boolean.TRUE.equals(request.getIsDefault()) ? 1 : 0);
        config.setIsEnabled(Boolean.TRUE.equals(request.getIsEnabled()) ? 1 : 0);
        config.setPriority(request.getPriority() != null ? request.getPriority() : 0);
        config.setRemark(request.getRemark());
        config.setExtraConfig(request.getExtraConfig());
        if (request.getTenantId() != null) {
            config.setTenantId(request.getTenantId());
        }

        if (config.getIsDefault() == 1) {
            clearDefaultForProvider(config.getProvider());
        }

        llmConfigMapper.updateById(config);
        // 清除缓存
        redisTemplate.delete(CONFIG_CACHE_PREFIX + configId);
        log.info("LLM配置更新: id={}", configId);
        return config;
    }

    /**
     * 删除配置
     */
    @Transactional
    @CacheEvict(value = "llm-config", allEntries = true)
    public void delete(String configId) {
        LLMConfig config = llmConfigMapper.selectById(configId);
        if (config == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "配置不存在: " + configId);
        }
        if (config.getIsDefault() == 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "默认配置不可删除，请先设置其他配置为默认");
        }
        llmConfigMapper.deleteById(configId);
        redisTemplate.delete(CONFIG_CACHE_PREFIX + configId);
        log.info("LLM配置删除: id={}", configId);
    }

    /**
     * 设置默认配置
     */
    @Transactional
    @CacheEvict(value = "llm-config", allEntries = true)
    public void setDefault(String configId) {
        LLMConfig config = llmConfigMapper.selectById(configId);
        if (config == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "配置不存在: " + configId);
        }
        if (config.getIsEnabled() == 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "禁用的配置不可设为默认");
        }

        // 清除同提供商的其他默认
        clearDefaultForProvider(config.getProvider());

        // 设置当前为默认
        config.setIsDefault(1);
        llmConfigMapper.updateById(config);
        redisTemplate.delete(CONFIG_CACHE_PREFIX + configId);
        log.info("LLM默认配置设置: id={}, provider={}", configId, config.getProvider());
    }

    /**
     * 获取支持的提供商列表
     */
    public List<Map<String, String>> getProviders() {
        return Arrays.asList(
            Map.of("value", "openai", "label", "OpenAI", "models", "gpt-4o,gpt-4,gpt-3.5-turbo"),
            Map.of("value", "deepseek", "label", "DeepSeek", "models", "deepseek-chat,deepseek-coder"),
            Map.of("value", "qwen", "label", "通义千问", "models", "qwen-max,qwen-plus,qwen-turbo"),
            Map.of("value", "glm", "label", "智谱AI", "models", "glm-4,glm-4-flash,glm-3-turbo"),
            Map.of("value", "gemini", "label", "Google Gemini", "models", "gemini-pro,gemini-1.5-pro"),
            Map.of("value", "kimi", "label", "Moonshot Kimi", "models", "moonshot-v1-8k,moonshot-v1-32k"),
            Map.of("value", "local", "label", "本地模型", "models", "自定义")
        );
    }

    /**
     * 清除同一提供商的其他默认配置
     */
    private void clearDefaultForProvider(String provider) {
        llmConfigMapper.update(null,
            new LambdaUpdateWrapper<LLMConfig>()
                .eq(LLMConfig::getProvider, provider)
                .eq(LLMConfig::getIsDefault, 1)
                .set(LLMConfig::getIsDefault, 0)
        );
    }
}

