package com.zhiwiki.document.feign;

import com.zhiwiki.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Map;

/**
 * Python AI服务 Feign客户端
 * 
 * 所有AI请求都通过此客户端转发到Python服务，
 * 确保前端不能直接访问Python服务，符合安全架构要求。
 */
@FeignClient(name = "rag-wiki-python", path = "/api/ai")
public interface AiServiceClient {

    // ==================== 文档解析 ====================

    /**
     * 提交文档解析任务
     */
    @PostMapping("/document/parse")
    Result<Map<String, Object>> submitParseTask(@RequestBody Map<String, Object> request);

    /**
     * 查询解析任务状态
     */
    @PostMapping("/document/parse/status")
    Result<Map<String, Object>> getParseStatus(@RequestParam("task_id") String taskId);

    /**
     * 上传文件并解析
     */
    @PostMapping(value = "/document/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Result<Map<String, Object>> uploadAndParse(
            @RequestPart("file") MultipartFile file,
            @RequestParam("space_id") String spaceId,
            @RequestParam("security_level") Integer securityLevel
    );

    /**
     * 重新解析文档
     */
    @PostMapping("/document/reparse")
    Result<Map<String, Object>> reparseDocument(
            @RequestParam("task_id") String taskId,
            @RequestParam(value = "parser_type", required = false) String parserType,
            @RequestParam(value = "force_ocr", required = false, defaultValue = "false") Boolean forceOcr
    );

    // ==================== 向量检索 ====================

    /**
     * 向量化入库
     */
    @PostMapping("/vector/embed")
    Result<Map<String, Object>> embedAndStore(@RequestBody Map<String, Object> request);

    /**
     * 向量相似度检索
     */
    @PostMapping("/vector/search")
    Result<Map<String, Object>> vectorSearch(@RequestBody Map<String, Object> request);

    /**
     * 混合检索
     */
    @PostMapping("/vector/hybrid-search")
    Result<Map<String, Object>> hybridSearch(@RequestBody Map<String, Object> request);

    /**
     * 重排序
     */
    @PostMapping("/vector/rerank")
    Result<Map<String, Object>> rerank(@RequestBody Map<String, Object> request);

    /**
     * 删除空间向量数据
     */
    @DeleteMapping("/vector/chunks/{space_id}")
    Result<Map<String, Object>> deleteSpaceChunks(@PathVariable("space_id") String spaceId);

    // ==================== RAG问答 ====================

    /**
     * 智能问答
     */
    @PostMapping("/rag/query")
    Result<Map<String, Object>> ragQuery(@RequestBody Map<String, Object> request);

    /**
     * 获取对话历史
     */
    @GetMapping("/rag/sessions/{session_id}")
    Result<Map<String, Object>> getSession(@PathVariable("session_id") String sessionId);

    /**
     * 获取会话历史列表
     */
    @GetMapping("/rag/sessions")
    Result<Map<String, Object>> getSessionHistory(@RequestParam Map<String, Object> params);

    /**
     * 清除对话历史
     */
    @DeleteMapping("/rag/sessions/{session_id}")
    Result<Map<String, Object>> clearSession(@PathVariable("session_id") String sessionId);

    /**
     * 提交答案反馈
     */
    @PostMapping("/rag/feedback")
    Result<Map<String, Object>> submitFeedback(@RequestBody Map<String, Object> request);

    /**
     * 获取每日统计
     */
    @GetMapping("/rag/stats/daily")
    Result<Map<String, Object>> getDailyStats();

    // ==================== 记忆管理 ====================

    /**
     * 创建记忆
     */
    @PostMapping("/memory/create")
    Result<Map<String, Object>> createMemory(@RequestBody Map<String, Object> request);

    /**
     * 搜索记忆
     */
    @PostMapping("/memory/search")
    Result<Map<String, Object>> searchMemory(@RequestBody Map<String, Object> request);

    /**
     * 从对话提取记忆
     */
    @PostMapping("/memory/extract")
    Result<Map<String, Object>> extractMemory(
            @RequestParam("session_id") String sessionId,
            @RequestParam("user_id") String userId
    );

    /**
     * 获取记忆列表
     */
    @GetMapping("/memory/list")
    Result<Map<String, Object>> getMemoryList(@RequestParam Map<String, Object> params);

    /**
     * 删除记忆
     */
    @DeleteMapping("/memory/{memory_id}")
    Result<Map<String, Object>> deleteMemory(@PathVariable("memory_id") String memoryId);

    // ==================== Agent ====================

    /**
     * 提交Agent任务
     */
    @PostMapping("/agent/submit")
    Result<Map<String, Object>> submitAgentTask(@RequestBody Map<String, Object> request);

    /**
     * 获取Agent任务
     */
    @GetMapping("/agent/task/{task_id}")
    Result<Map<String, Object>> getAgentTask(@PathVariable("task_id") String taskId);

    /**
     * 取消Agent任务
     */
    @PostMapping("/agent/task/{task_id}/cancel")
    Result<Map<String, Object>> cancelAgentTask(@PathVariable("task_id") String taskId);

    /**
     * 获取Agent工具列表
     */
    @GetMapping("/agent/tools")
    Result<Map<String, Object>> getAgentTools();

    // ==================== 代码沙箱 ====================

    /**
     * 执行代码
     */
    @PostMapping("/sandbox/execute")
    Result<Map<String, Object>> executeCode(@RequestBody Map<String, Object> request);

    /**
     * 获取执行结果
     */
    @GetMapping("/sandbox/result/{task_id}")
    Result<Map<String, Object>> getSandboxResult(@PathVariable("task_id") String taskId);

    /**
     * 代码安全检查
     */
    @PostMapping("/sandbox/security-check")
    Result<Map<String, Object>> securityCheck(@RequestParam("code") String code);

    // ==================== 向量库管理 ====================

    /**
     * 获取向量库统计
     */
    @GetMapping("/vector/admin/stats")
    Result<Map<String, Object>> getVectorStats(@RequestParam(value = "collection", required = false) String collection);

    /**
     * 分页查询向量数据
     */
    @GetMapping("/vector/admin/page")
    Result<Map<String, Object>> pageVectors(@RequestParam Map<String, Object> params);

    /**
     * 删除向量
     */
    @DeleteMapping("/vector/admin/{vector_id}")
    Result<Map<String, Object>> deleteVector(@PathVariable("vector_id") String vectorId,
                                              @RequestParam(value = "collection", required = false) String collection);

    /**
     * 重建索引
     */
    @PostMapping("/vector/admin/rebuild-index")
    Result<Map<String, Object>> rebuildIndex(@RequestBody Map<String, Object> request);

    /**
     * 获取集合列表
     */
    @GetMapping("/vector/admin/collections")
    Result<Map<String, Object>> getCollections();

    // ==================== 内容创作辅助 ====================

    /**
     * 生成大纲
     */
    @PostMapping("/content/outline")
    Result<Map<String, Object>> generateOutline(@RequestBody Map<String, Object> request);

    /**
     * 内容润色
     */
    @PostMapping("/content/polish")
    Result<Map<String, Object>> polishContent(@RequestBody Map<String, Object> request);

    /**
     * 内容扩写
     */
    @PostMapping("/content/expand")
    Result<Map<String, Object>> expandContent(@RequestBody Map<String, Object> request);

    /**
     * 内容摘要
     */
    @PostMapping("/content/summarize")
    Result<Map<String, Object>> summarizeContent(@RequestBody Map<String, Object> request);

    /**
     * 基于知识库生成内容
     */
    @PostMapping("/content/generate")
    Result<Map<String, Object>> generateFromKnowledge(@RequestBody Map<String, Object> request);

    // ==================== 多Agent协作 ====================

    /**
     * 获取协作场景列表
     */
    @GetMapping("/enterprise-agent/scenarios")
    Result<Map<String, Object>> getEnterpriseAgentScenarios();

    /**
     * 执行协作场景
     */
    @PostMapping("/enterprise-agent/execute")
    Result<Map<String, Object>> executeEnterpriseAgentScenario(@RequestBody Map<String, Object> request);

    /**
     * 获取协作会话
     */
    @GetMapping("/enterprise-agent/session/{session_id}")
    Result<Map<String, Object>> getEnterpriseAgentSession(@PathVariable("session_id") String sessionId);

    // ==================== Badcase优化闭环 ====================

    /**
     * 创建优化实验
     */
    @PostMapping("/badcase-optimizer/experiment")
    Result<Map<String, Object>> createBadcaseExperiment(@RequestBody Map<String, Object> request);

    /**
     * 运行优化实验
     */
    @PostMapping("/badcase-optimizer/experiment/{experiment_id}/run")
    Result<Map<String, Object>> runBadcaseExperiment(@PathVariable("experiment_id") String experimentId);

    /**
     * 获取优化实验详情
     */
    @GetMapping("/badcase-optimizer/experiment/{experiment_id}")
    Result<Map<String, Object>> getBadcaseExperiment(@PathVariable("experiment_id") String experimentId);

    /**
     * Badcase归因分析
     */
    @PostMapping("/badcase-optimizer/attribute")
    Result<Map<String, Object>> attributeBadcase(@RequestBody Map<String, Object> request);

    /**
     * 优化闭环执行
     */
    @PostMapping("/badcase-optimizer/optimization-loop")
    Result<Map<String, Object>> optimizationLoop(@RequestBody Map<String, Object> request);

    // ==================== 领域模型微调 ====================

    /**
     * 构建微调数据集
     */
    @PostMapping("/domain-tuning/dataset/build")
    Result<Map<String, Object>> buildTuningDataset(@RequestBody Map<String, Object> request);

    /**
     * 创建微调任务
     */
    @PostMapping("/domain-tuning/job")
    Result<Map<String, Object>> createTuningJob(@RequestBody Map<String, Object> request);

    /**
     * 启动微调任务
     */
    @PostMapping("/domain-tuning/job/{job_id}/start")
    Result<Map<String, Object>> startTuningJob(@PathVariable("job_id") String jobId);

    /**
     * 获取微调任务状态
     */
    @GetMapping("/domain-tuning/job/{job_id}")
    Result<Map<String, Object>> getTuningJobStatus(@PathVariable("job_id") String jobId);

    /**
     * 获取微调任务列表
     */
    @GetMapping("/domain-tuning/jobs")
    Result<Map<String, Object>> listTuningJobs();

    // ==================== 智能工作流引擎 ====================

    /**
     * 获取工作流模板
     */
    @GetMapping("/workflow/templates")
    Result<Map<String, Object>> getWorkflowTemplates();

    /**
     * 创建工作流
     */
    @PostMapping("/workflow/create")
    Result<Map<String, Object>> createWorkflow(@RequestBody Map<String, Object> request);

    /**
     * 获取工作流详情
     */
    @GetMapping("/workflow/{workflow_id}")
    Result<Map<String, Object>> getWorkflow(@PathVariable("workflow_id") String workflowId);

    /**
     * 执行工作流
     */
    @PostMapping("/workflow/{workflow_id}/execute")
    Result<Map<String, Object>> executeWorkflow(@PathVariable("workflow_id") String workflowId,
                                                 @RequestBody Map<String, Object> request);

    /**
     * 获取工作流执行详情
     */
    @GetMapping("/workflow/execution/{execution_id}")
    Result<Map<String, Object>> getWorkflowExecution(@PathVariable("execution_id") String executionId);

    // ==================== 第三方系统集成 ====================

    /**
     * 发送企微消息
     */
    @PostMapping("/third-party/wecom/message")
    Result<Map<String, Object>> sendWecomMessage(@RequestBody Map<String, Object> request);

    /**
     * 发送钉钉消息
     */
    @PostMapping("/third-party/dingtalk/message")
    Result<Map<String, Object>> sendDingtalkMessage(@RequestBody Map<String, Object> request);

    /**
     * 发送Webhook
     */
    @PostMapping("/third-party/webhook")
    Result<Map<String, Object>> sendWebhook(@RequestBody Map<String, Object> request);

    /**
     * 创建OA流程
     */
    @PostMapping("/third-party/oa/process")
    Result<Map<String, Object>> createOAProcess(@RequestBody Map<String, Object> request);

    /**
     * 获取Webhook日志
     */
    @GetMapping("/third-party/webhook/logs")
    Result<Map<String, Object>> getWebhookLogs(@RequestParam("limit") Integer limit);

    // ==================== 质量工具 ====================

    /**
     * 忠实度校验
     */
    @PostMapping("/quality/faithfulness/validate")
    Result<Map<String, Object>> validateFaithfulness(@RequestBody Map<String, Object> request);

    /**
     * 快速忠实度校验
     */
    @PostMapping("/quality/faithfulness/quick")
    Result<Map<String, Object>> quickValidateFaithfulness(@RequestBody Map<String, Object> request);

    /**
     * 记忆冲突检测
     */
    @PostMapping("/quality/memory/conflict-detect")
    Result<Map<String, Object>> detectMemoryConflicts(@RequestBody Map<String, Object> request);

    /**
     * 语义去重
     */
    @PostMapping("/quality/memory/semantic-dedup")
    Result<Map<String, Object>> semanticDedup(@RequestBody Map<String, Object> request);
}
