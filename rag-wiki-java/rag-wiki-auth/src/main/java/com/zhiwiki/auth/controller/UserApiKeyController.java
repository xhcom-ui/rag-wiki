package com.zhiwiki.auth.controller;

import com.zhiwiki.auth.entity.UserApiKey;
import com.zhiwiki.auth.service.UserApiKeyService;
import com.zhiwiki.common.core.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户API密钥管理控制器
 * 
 * 支持用户创建、查看、吊销个人API密钥
 * 用于外部系统集成和API认证
 */
@RestController
@RequestMapping("/api/user/api-key")
@RequiredArgsConstructor
public class UserApiKeyController {

    private final UserApiKeyService apiKeyService;

    /**
     * 创建API密钥
     * POST /api/user/api-key
     */
    @PostMapping
    public Result<?> createApiKey(@RequestBody Map<String, Object> body) {
        String keyName = (String) body.getOrDefault("keyName", "默认密钥");
        Integer rateLimit = body.get("rateLimit") != null ? 
            Integer.valueOf(body.get("rateLimit").toString()) : null;
        String allowedScopes = (String) body.get("allowedScopes");
        
        String rawKey = apiKeyService.createApiKey(keyName, rateLimit, allowedScopes);
        return Result.success(Map.of(
            "message", "密钥创建成功，请妥善保管，此密钥仅展示一次",
            "apiKey", rawKey
        ));
    }

    /**
     * 查询当前用户的API密钥列表（脱敏）
     * GET /api/user/api-key/list
     */
    @GetMapping("/list")
    public Result<List<UserApiKey>> listApiKeys() {
        List<UserApiKey> keys = apiKeyService.listUserKeys();
        // 脱敏：清空hash字段
        keys.forEach(k -> k.setKeyHash(null));
        return Result.success(keys);
    }

    /**
     * 吊销API密钥
     * DELETE /api/user/api-key/{keyId}
     */
    @DeleteMapping("/{keyId}")
    public Result<?> revokeApiKey(@PathVariable String keyId) {
        apiKeyService.revokeApiKey(keyId);
        return Result.success("密钥已吊销");
    }

    /**
     * 启用/禁用API密钥
     * PUT /api/user/api-key/{keyId}/toggle
     */
    @PutMapping("/{keyId}/toggle")
    public Result<?> toggleApiKey(@PathVariable String keyId, 
                                   @RequestBody Map<String, Boolean> body) {
        boolean enabled = body.getOrDefault("enabled", true);
        apiKeyService.toggleApiKey(keyId, enabled);
        return Result.success(enabled ? "密钥已启用" : "密钥已禁用");
    }
}
