package com.zhiwiki.common.feign;

import com.zhiwiki.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI记忆服务远程调用客户端
 */
@FeignClient(name = "rag-wiki-ai", contextId = "aiClient")
public interface AiClient {

    /**
     * 清理过期记忆
     */
    @PostMapping("/api/ai/memory/cleanup")
    Result<Map<String, Object>> cleanupMemory();

    /**
     * 搜索用户记忆
     */
    @PostMapping("/api/ai/memory/search")
    Result<Map<String, Object>> searchMemory(@RequestBody Map<String, Object> request);
}
