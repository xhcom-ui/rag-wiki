package com.zhiwiki.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Vault密钥管理配置
 * 
 * 配置示例（application.yml）：
 * vault:
 *   enabled: true
 *   url: http://vault:8200
 *   token: s.xxxx
 *   namespace: rag-wiki
 *   secrets-path: secret/data/rag-wiki
 *   cache-ttl: 300
 *   fail-fast: true
 */
@Data
@Component
@ConfigurationProperties(prefix = "vault")
public class VaultProperties {

    /** 是否启用Vault */
    private boolean enabled = false;

    /** Vault服务器地址 */
    private String url = "http://localhost:8200";

    /** Vault访问令牌 */
    private String token;

    /** Vault命名空间 */
    private String namespace = "rag-wiki";

    /** 密钥存储路径 */
    private String secretsPath = "secret/data/rag-wiki";

    /** 本地缓存TTL（秒） */
    private long cacheTtl = 300;

    /** 连接失败时是否阻止启动 */
    private boolean failFast = false;

    /** 连接超时（毫秒） */
    private int connectTimeout = 5000;

    /** 读取超时（毫秒） */
    private int readTimeout = 10000;
}
