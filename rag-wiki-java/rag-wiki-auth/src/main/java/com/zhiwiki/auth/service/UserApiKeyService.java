package com.zhiwiki.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiwiki.auth.entity.UserApiKey;
import com.zhiwiki.auth.mapper.UserApiKeyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * 用户API密钥管理服务
 * 
 * 功能：
 * 1. 创建API密钥 - 生成sk-xxxx格式密钥
 * 2. 列表查询 - 查看用户所有密钥（脱敏展示）
 * 3. 吊销密钥 - 软删除
 * 4. 验证密钥 - 校验API请求中的密钥
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserApiKeyService {

    private final UserApiKeyMapper apiKeyMapper;

    /**
     * 创建API密钥
     * 
     * @param keyName 密钥名称
     * @param rateLimit QPS限制
     * @param allowedScopes 允许的权限范围
     * @return 包含明文密钥的响应（仅此一次展示完整密钥）
     */
    public String createApiKey(String keyName, Integer rateLimit, String allowedScopes) {
        String userId = StpUtil.getLoginIdAsString();

        // 检查密钥数量限制（每用户最多5个）
        long count = apiKeyMapper.selectCount(
            new LambdaQueryWrapper<UserApiKey>()
                .eq(UserApiKey::getUserId, userId)
                .eq(UserApiKey::getIsDeleted, 0)
        );
        if (count >= 5) {
            throw new RuntimeException("每用户最多创建5个API密钥");
        }

        // 生成密钥: sk-随机Base64字符串
        String rawKey = "sk-" + IdUtil.fastSimpleUUID() + IdUtil.fastSimpleUUID();
        String keyHash = sha256(rawKey);
        String keyPrefix = rawKey.substring(0, 8) + "..." + rawKey.substring(rawKey.length() - 4);

        UserApiKey apiKey = new UserApiKey();
        apiKey.setKeyId("key_" + IdUtil.fastSimpleUUID());
        apiKey.setUserId(userId);
        apiKey.setKeyName(keyName);
        apiKey.setKeyHash(keyHash);
        apiKey.setKeyPrefix(keyPrefix);
        apiKey.setIsEnabled(1);
        apiKey.setExpiresAt(LocalDateTime.now().plusYears(1));
        apiKey.setRateLimit(rateLimit != null ? rateLimit : 30);
        apiKey.setAllowedScopes(allowedScopes != null ? allowedScopes : "[\"read\"]");
        apiKey.setIsDeleted(0);

        apiKeyMapper.insert(apiKey);
        log.info("API密钥创建成功: userId={}, keyId={}, keyName={}", userId, apiKey.getKeyId(), keyName);

        return rawKey; // 返回明文密钥，仅此一次
    }

    /**
     * 查询用户所有API密钥（脱敏展示）
     */
    public List<UserApiKey> listUserKeys() {
        String userId = StpUtil.getLoginIdAsString();
        return apiKeyMapper.selectList(
            new LambdaQueryWrapper<UserApiKey>()
                .eq(UserApiKey::getUserId, userId)
                .eq(UserApiKey::getIsDeleted, 0)
                .orderByDesc(UserApiKey::getCreatedAt)
        );
    }

    /**
     * 吊销API密钥
     */
    public void revokeApiKey(String keyId) {
        String userId = StpUtil.getLoginIdAsString();
        UserApiKey key = apiKeyMapper.selectOne(
            new LambdaQueryWrapper<UserApiKey>()
                .eq(UserApiKey::getKeyId, keyId)
                .eq(UserApiKey::getUserId, userId)
                .eq(UserApiKey::getIsDeleted, 0)
        );
        if (key == null) {
            throw new RuntimeException("密钥不存在或已吊销");
        }
        key.setIsDeleted(1);
        apiKeyMapper.updateById(key);
        log.info("API密钥已吊销: userId={}, keyId={}", userId, keyId);
    }

    /**
     * 切换密钥启用/禁用状态
     */
    public void toggleApiKey(String keyId, boolean enabled) {
        String userId = StpUtil.getLoginIdAsString();
        UserApiKey key = apiKeyMapper.selectOne(
            new LambdaQueryWrapper<UserApiKey>()
                .eq(UserApiKey::getKeyId, keyId)
                .eq(UserApiKey::getUserId, userId)
                .eq(UserApiKey::getIsDeleted, 0)
        );
        if (key == null) {
            throw new RuntimeException("密钥不存在");
        }
        key.setIsEnabled(enabled ? 1 : 0);
        apiKeyMapper.updateById(key);
    }

    /**
     * 验证API密钥
     * 
     * @param rawKey 明文密钥
     * @return 密钥实体（验证通过）或null（验证失败）
     */
    public UserApiKey validateApiKey(String rawKey) {
        if (rawKey == null || !rawKey.startsWith("sk-")) {
            return null;
        }

        String keyHash = sha256(rawKey);
        UserApiKey key = apiKeyMapper.selectOne(
            new LambdaQueryWrapper<UserApiKey>()
                .eq(UserApiKey::getKeyHash, keyHash)
                .eq(UserApiKey::getIsEnabled, 1)
                .eq(UserApiKey::getIsDeleted, 0)
        );

        if (key != null) {
            // 检查是否过期
            if (key.getExpiresAt() != null && key.getExpiresAt().isBefore(LocalDateTime.now())) {
                return null;
            }
            // 更新最后使用时间
            key.setLastUsedAt(LocalDateTime.now());
            apiKeyMapper.updateById(key);
        }

        return key;
    }

    /**
     * SHA-256哈希
     */
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256哈希失败", e);
        }
    }
}
