package com.zhiwiki.common.feign;

import com.zhiwiki.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 文档服务远程调用客户端
 */
@FeignClient(name = "rag-wiki-document", contextId = "documentClient")
public interface DocumentClient {

    /**
     * 获取文档详情
     */
    @GetMapping("/api/document/{documentId}")
    Result<Map<String, Object>> getDocument(@PathVariable("documentId") String documentId);

    /**
     * 获取知识库空间详情
     */
    @GetMapping("/api/space/{spaceId}")
    Result<Map<String, Object>> getSpace(@PathVariable("spaceId") String spaceId);
}
