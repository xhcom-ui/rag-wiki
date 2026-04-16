package com.zhiwiki.document.feign;

import com.zhiwiki.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 文档服务内部Feign客户端
 * 供其他服务调用
 */
@FeignClient(name = "rag-wiki-document", path = "/api/document")
public interface DocumentServiceClient {

    /**
     * 获取文档详情
     */
    @GetMapping("/{documentId}")
    Result<Map<String, Object>> getDocument(@PathVariable("documentId") String documentId);

    /**
     * 更新文档状态
     */
    @PutMapping("/{documentId}/status")
    Result<Boolean> updateStatus(
            @PathVariable("documentId") String documentId,
            @RequestParam("status") String status
    );

    /**
     * 更新文档解析状态
     */
    @PutMapping("/{documentId}/parse-status")
    Result<Boolean> updateParseStatus(
            @PathVariable("documentId") String documentId,
            @RequestParam("parseStatus") String parseStatus,
            @RequestParam(value = "taskId", required = false) String taskId
    );
}