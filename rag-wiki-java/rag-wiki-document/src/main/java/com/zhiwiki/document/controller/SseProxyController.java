package com.zhiwiki.document.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Map;

/**
 * SSE流式代理控制器
 * 
 * 专门处理AI服务的SSE流式输出请求，
 * 确保流式数据正确转发到前端。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/rag/query")
@RequiredArgsConstructor
public class SseProxyController {

    private static final String PYTHON_SERVICE_URL = "http://rag-wiki-python:8001";
    
    private RestTemplate streamingRestTemplate;

    private RestTemplate getStreamingRestTemplate() {
        if (streamingRestTemplate == null) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setBufferRequestBody(false);
            factory.setConnectTimeout(30000);
            factory.setReadTimeout(60000);
            streamingRestTemplate = new RestTemplate(factory);
        }
        return streamingRestTemplate;
    }

    /**
     * SSE流式问答代理
     * 
     * 前端请求 -> Java网关 -> Python AI服务 -> 流式返回
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public StreamingResponseBody streamQuery(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest
    ) {
        log.debug("SSE流式问答: question={}", request.get("question"));

        return outputStream -> {
            try {
                // 构建转发请求头
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                
                // 传递认证信息
                String auth = httpRequest.getHeader("Authorization");
                if (auth != null) {
                    headers.set("Authorization", auth);
                }

                // 调用 Python 服务
                String pythonUrl = PYTHON_SERVICE_URL + "/api/ai/rag/query/stream";
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

                ResponseEntity<InputStream> response = getStreamingRestTemplate().exchange(
                        pythonUrl,
                        HttpMethod.POST,
                        entity,
                        InputStream.class
                );

                // 流式转发响应
                if (response.getBody() != null) {
                    try (InputStream inputStream = response.getBody()) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                            outputStream.flush();
                        }
                    }
                }
            } catch (Exception e) {
                log.error("SSE流式代理失败", e);
                // 发送错误事件
                String errorEvent = "event: error\ndata: " + e.getMessage() + "\n\n";
                outputStream.write(errorEvent.getBytes());
                outputStream.flush();
            }
        };
    }
}
