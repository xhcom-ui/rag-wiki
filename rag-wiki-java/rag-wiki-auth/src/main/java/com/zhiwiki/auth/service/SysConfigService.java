package com.zhiwiki.auth.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiwiki.auth.entity.SysConfig;
import com.zhiwiki.auth.mapper.SysConfigMapper;
import com.zhiwiki.common.exception.BusinessException;
import com.zhiwiki.common.model.ResultCode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 系统配置管理服务
 * 
 * 支持配置分组：BASIC(基础)、SECURITY(安全)、NOTIFICATION(通知)、UPLOAD(上传)、RAG(AI)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysConfigService {

    private final SysConfigMapper sysConfigMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CONFIG_CACHE_PREFIX = "sys:config:";
    private static final long CONFIG_CACHE_TTL = 30; // 分钟

    /**
     * 获取配置值
     */
    public String getConfigValue(String key) {
        // 先查缓存
        String cached = (String) redisTemplate.opsForValue().get(CONFIG_CACHE_PREFIX + key);
        if (cached != null) return cached;

        // 查数据库
        SysConfig config = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        if (config == null) return null;

        // 存缓存
        redisTemplate.opsForValue().set(CONFIG_CACHE_PREFIX + key, config.getConfigValue(),
                CONFIG_CACHE_TTL, TimeUnit.MINUTES);
        return config.getConfigValue();
    }

    /**
     * 获取配置值（带默认值）
     */
    public String getConfigValue(String key, String defaultValue) {
        String value = getConfigValue(key);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取数字配置值
     */
    public int getIntConfig(String key, int defaultValue) {
        String value = getConfigValue(key);
        if (value == null) return defaultValue;
        try { return Integer.parseInt(value); } catch (NumberFormatException e) { return defaultValue; }
    }

    /**
     * 获取布尔配置值
     */
    public boolean getBoolConfig(String key, boolean defaultValue) {
        String value = getConfigValue(key);
        if (value == null) return defaultValue;
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    /**
     * 更新配置值
     */
    public void updateConfigValue(String key, String value) {
        SysConfig config = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        if (config == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "配置项不存在: " + key);
        }
        if (config.getIsReadonly() == 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "只读配置项不可修改: " + key);
        }
        config.setConfigValue(value);
        sysConfigMapper.updateById(config);

        // 更新缓存
        redisTemplate.opsForValue().set(CONFIG_CACHE_PREFIX + key, value,
                CONFIG_CACHE_TTL, TimeUnit.MINUTES);
        log.info("系统配置已更新: key={}", key);
    }

    /**
     * 获取指定分组的所有配置
     */
    public List<SysConfig> getConfigsByGroup(String group) {
        return sysConfigMapper.selectList(
                new LambdaQueryWrapper<SysConfig>()
                        .eq(SysConfig::getConfigGroup, group)
                        .orderByAsc(SysConfig::getSortOrder));
    }

    /**
     * 获取所有配置分组
     */
    public List<ConfigGroupVO> getAllGroups() {
        List<SysConfig> allConfigs = sysConfigMapper.selectList(
                new LambdaQueryWrapper<SysConfig>().orderByAsc(SysConfig::getConfigGroup, SysConfig::getSortOrder));

        Map<String, List<SysConfig>> grouped = allConfigs.stream()
                .collect(Collectors.groupingBy(SysConfig::getConfigGroup, LinkedHashMap::new, Collectors.toList()));

        List<ConfigGroupVO> result = new ArrayList<>();
        for (Map.Entry<String, List<SysConfig>> entry : grouped.entrySet()) {
            ConfigGroupVO vo = new ConfigGroupVO();
            vo.setGroupKey(entry.getKey());
            vo.setGroupLabel(getGroupLabel(entry.getKey()));
            vo.setConfigs(entry.getValue());
            result.add(vo);
        }
        return result;
    }

    /**
     * 新增配置项
     */
    public SysConfig addConfig(SysConfig config) {
        // 检查key是否已存在
        SysConfig existing = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, config.getConfigKey()));
        if (existing != null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "配置键已存在: " + config.getConfigKey());
        }
        sysConfigMapper.insert(config);
        log.info("新增系统配置: key={}", config.getConfigKey());
        return config;
    }

    /**
     * 删除配置项
     */
    public void deleteConfig(String key) {
        SysConfig config = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        if (config == null) return;
        if (config.getIsSystem() == 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "系统内置配置不可删除: " + key);
        }
        sysConfigMapper.deleteById(config.getId());
        redisTemplate.delete(CONFIG_CACHE_PREFIX + key);
        log.info("删除系统配置: key={}", key);
    }

    private String getGroupLabel(String groupKey) {
        return switch (groupKey) {
            case "BASIC" -> "基础配置";
            case "SECURITY" -> "安全策略";
            case "NOTIFICATION" -> "通知模板";
            case "UPLOAD" -> "上传配置";
            case "RAG" -> "AI参数";
            default -> groupKey;
        };
    }

    @Data
    public static class ConfigGroupVO {
        private String groupKey;
        private String groupLabel;
        private List<SysConfig> configs;
    }
}

