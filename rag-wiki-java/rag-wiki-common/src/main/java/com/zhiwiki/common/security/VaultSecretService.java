package com.zhiwiki.common.security;

import com.zhiwiki.common.config.VaultProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vault密钥管理服务
 * 
 * 提供统一的密钥管理能力：
 * 1. 从HashiCorp Vault读取密钥
 * 2. 本地内存缓存减少网络调用
 * 3. 支持密钥轮转通知
 * 4. 优雅降级 - Vault不可用时使用本地配置
 * 
 * 集成Vault后，敏感配置（数据库密码、API密钥等）
 * 不再存储在application.yml中，而是从Vault动态获取。
 * 
 * 使用方式：
 *   @Autowired
 *   private VaultSecretService vaultSecretService;
 *   
 *   String dbPassword = vaultSecretService.getSecret("database.password");
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "vault", name = "enabled", havingValue = "true")
public class VaultSecretService {

    private final VaultProperties properties;
    
    /** 本地缓存: key -> CachedSecret */
    private final Map<String, CachedSecret> cache = new ConcurrentHashMap<>();

    public VaultSecretService(VaultProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        log.info("Vault密钥管理服务初始化: url={}, namespace={}, path={}", 
                properties.getUrl(), properties.getNamespace(), properties.getSecretsPath());
        
        // 健康检查
        try {
            checkConnection();
            log.info("Vault连接成功");
        } catch (Exception e) {
            if (properties.isFailFast()) {
                throw new RuntimeException("Vault连接失败 (fail-fast=true): " + e.getMessage(), e);
            } else {
                log.warn("Vault连接失败，将使用降级模式: {}", e.getMessage());
            }
        }
    }

    /**
     * 获取密钥
     * 
     * @param key 密钥名称（如 database.password, ai.api-key）
     * @return 密钥值，如果获取失败返回null
     */
    public String getSecret(String key) {
        // 1. 检查缓存
        CachedSecret cached = cache.get(key);
        if (cached != null && !cached.isExpired()) {
            log.debug("从缓存获取密钥: {}", key);
            return cached.getValue();
        }

        // 2. 从Vault获取
        try {
            String value = fetchFromVault(key);
            if (value != null) {
                // 更新缓存
                cache.put(key, new CachedSecret(value, Instant.now().plusSeconds(properties.getCacheTtl())));
                log.debug("从Vault获取密钥成功: {}", key);
                return value;
            }
        } catch (Exception e) {
            log.error("从Vault获取密钥失败: key={}, error={}", key, e.getMessage());
            
            // 返回过期缓存（降级）
            if (cached != null) {
                log.warn("使用过期缓存作为降级: key={}", key);
                return cached.getValue();
            }
        }

        return null;
    }

    /**
     * 获取密钥（带默认值）
     */
    public String getSecret(String key, String defaultValue) {
        String value = getSecret(key);
        return value != null ? value : defaultValue;
    }

    /**
     * 刷新指定密钥的缓存
     */
    public void refreshSecret(String key) {
        cache.remove(key);
        log.info("刷新密钥缓存: {}", key);
    }

    /**
     * 刷新所有缓存
     */
    public void refreshAll() {
        cache.clear();
        log.info("刷新所有密钥缓存");
    }

    /**
     * Vault健康检查
     */
    public boolean isHealthy() {
        try {
            checkConnection();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 从Vault HTTP API获取密钥
     * 
     * 调用 GET {vault_url}/v1/{secrets_path}?key={key}
     * 请求头: X-Vault-Token: {token}
     */
    private String fetchFromVault(String key) {
        // 实际生产环境使用 spring-cloud-vault 或 vault-java-driver
        // 这里提供HTTP API调用实现
        try {
            java.net.URL url = new java.net.URL(
                properties.getUrl() + "/v1/" + properties.getSecretsPath());
            
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("X-Vault-Token", properties.getToken());
            conn.setRequestProperty("X-Vault-Namespace", properties.getNamespace());
            conn.setConnectTimeout(properties.getConnectTimeout());
            conn.setReadTimeout(properties.getReadTimeout());
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                // 解析Vault响应
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    // 简化JSON解析 - 生产环境应使用Jackson
                    String responseBody = response.toString();
                    return parseVaultResponse(responseBody, key);
                }
            } else {
                throw new RuntimeException("Vault返回非200状态码: " + responseCode);
            }
        } catch (Exception e) {
            throw new RuntimeException("Vault HTTP调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析Vault KV v2响应
     */
    private String parseVaultResponse(String responseBody, String key) {
        // Vault KV v2响应格式: {"data":{"data":{key: value}}}
        // 简化解析 - 生产环境使用Jackson ObjectMapper
        try {
            // 查找 data.data 段
            int dataIdx = responseBody.indexOf("\"data\"");
            if (dataIdx < 0) return null;
            
            int secondDataIdx = responseBody.indexOf("\"data\"", dataIdx + 5);
            if (secondDataIdx < 0) return null;
            
            // 查找目标key
            String searchKey = "\"" + key + "\"";
            int keyIdx = responseBody.indexOf(searchKey, secondDataIdx);
            if (keyIdx < 0) return null;
            
            // 提取值
            int colonIdx = responseBody.indexOf(":", keyIdx + searchKey.length());
            int valueStart = responseBody.indexOf("\"", colonIdx);
            int valueEnd = responseBody.indexOf("\"", valueStart + 1);
            
            if (valueStart > 0 && valueEnd > valueStart) {
                return responseBody.substring(valueStart + 1, valueEnd);
            }
        } catch (Exception e) {
            log.error("解析Vault响应失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 检查Vault连接
     */
    private void checkConnection() {
        try {
            java.net.URL url = new java.net.URL(properties.getUrl() + "/v1/sys/health");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(properties.getConnectTimeout());
            conn.setReadTimeout(properties.getReadTimeout());
            
            int code = conn.getResponseCode();
            // Vault健康端点: 200=initialized+unsealed+active, 429=standby
            if (code != 200 && code != 429) {
                throw new RuntimeException("Vault健康检查失败: status=" + code);
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException("Vault连接失败: " + e.getMessage(), e);
        }
    }

    // ==================== 内部类 ====================

    /**
     * 缓存的密钥条目
     */
    private static class CachedSecret {
        private final String value;
        private final Instant expiresAt;

        CachedSecret(String value, Instant expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }

        String getValue() { return value; }
        
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
