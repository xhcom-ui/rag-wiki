package com.zhiwiki.common.feign;

import com.zhiwiki.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 认证服务远程调用客户端
 */
@FeignClient(name = "rag-wiki-auth", contextId = "authClient")
public interface AuthClient {

    /**
     * 根据用户ID获取用户信息
     */
    @GetMapping("/api/user/{userId}")
    Result<Map<String, Object>> getUserByUserId(@PathVariable("userId") String userId);

    /**
     * 根据用户ID列表批量获取用户信息
     */
    @GetMapping("/api/user/list")
    Result<List<Map<String, Object>>> listUsers(@RequestParam("userIds") List<String> userIds);

    /**
     * 校验用户是否有指定权限
     */
    @GetMapping("/api/auth/permissions")
    Result<List<String>> getPermissions();
}
