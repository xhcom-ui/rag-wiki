package com.zhiwiki.document.controller;

import com.zhiwiki.common.model.Result;
import com.zhiwiki.document.feign.AiServiceClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * AI服务代理控制器
 * 
 * 所有前端AI请求都通过此控制器转发到Python服务，
 * 确保前端不能直接访问Python服务，符合安全架构要求：
 * 前端 -> Java -> Python
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Tag(name = "AI服务代理", description = "代理所有AI请求到Python服务")
public class AiProxyController {

    private final AiServiceClient aiServiceClient;

    // ==================== 文档解析 ====================

    @PostMapping("/document/parse")
    @Operation(summary = "提交文档解析任务")
    public Result<Map<String, Object>> submitParseTask(@RequestBody Map<String, Object> request) {
        log.debug("代理文档解析任务: {}", request.get("document_id"));
        return aiServiceClient.submitParseTask(request);
    }

    @PostMapping("/document/parse/status")
    @Operation(summary = "查询解析任务状态")
    public Result<Map<String, Object>> getParseStatus(@RequestParam("task_id") String taskId) {
        log.debug("查询解析状态: {}", taskId);
        return aiServiceClient.getParseStatus(taskId);
    }

    @PostMapping(value = "/document/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传文件并解析")
    public Result<Map<String, Object>> uploadAndParse(
            @RequestPart("file") MultipartFile file,
            @RequestParam("space_id") String spaceId,
            @RequestParam(value = "security_level", defaultValue = "1") Integer securityLevel
    ) {
        log.debug("上传解析: spaceId={}, fileName={}", spaceId, file.getOriginalFilename());
        return aiServiceClient.uploadAndParse(file, spaceId, securityLevel);
    }

    @PostMapping("/document/reparse")
    @Operation(summary = "重新解析文档")
    public Result<Map<String, Object>> reparseDocument(
            @RequestParam("task_id") String taskId,
            @RequestParam(value = "parser_type", required = false) String parserType,
            @RequestParam(value = "force_ocr", required = false, defaultValue = "false") Boolean forceOcr
    ) {
        log.debug("重新解析: taskId={}", taskId);
        return aiServiceClient.reparseDocument(taskId, parserType, forceOcr);
    }

    // ==================== 向量检索 ====================

    @PostMapping("/vector/embed")
    @Operation(summary = "向量化入库")
    public Result<Map<String, Object>> embedAndStore(@RequestBody Map<String, Object> request) {
        log.debug("向量化入库: spaceId={}", request.get("space_id"));
        return aiServiceClient.embedAndStore(request);
    }

    @PostMapping("/vector/search")
    @Operation(summary = "向量相似度检索")
    public Result<Map<String, Object>> vectorSearch(@RequestBody Map<String, Object> request) {
        log.debug("向量检索: query={}", request.get("query"));
        return aiServiceClient.vectorSearch(request);
    }

    @PostMapping("/vector/hybrid-search")
    @Operation(summary = "混合检索")
    public Result<Map<String, Object>> hybridSearch(@RequestBody Map<String, Object> request) {
        log.debug("混合检索: query={}", request.get("query"));
        return aiServiceClient.hybridSearch(request);
    }

    @PostMapping("/vector/rerank")
    @Operation(summary = "重排序")
    public Result<Map<String, Object>> rerank(@RequestBody Map<String, Object> request) {
        log.debug("重排序: query={}", request.get("query"));
        return aiServiceClient.rerank(request);
    }

    @DeleteMapping("/vector/chunks/{space_id}")
    @Operation(summary = "删除空间向量数据")
    public Result<Map<String, Object>> deleteSpaceChunks(@PathVariable("space_id") String spaceId) {
        log.debug("删除向量: spaceId={}", spaceId);
        return aiServiceClient.deleteSpaceChunks(spaceId);
    }

    // ==================== RAG问答 ====================

    @PostMapping("/rag/query")
    @Operation(summary = "智能问答（RAG）")
    public Result<Map<String, Object>> ragQuery(@RequestBody Map<String, Object> request) {
        log.debug("RAG问答: question={}", request.get("question"));
        return aiServiceClient.ragQuery(request);
    }

    @GetMapping("/rag/sessions/{session_id}")
    @Operation(summary = "获取对话历史")
    public Result<Map<String, Object>> getSession(@PathVariable("session_id") String sessionId) {
        log.debug("获取会话: {}", sessionId);
        return aiServiceClient.getSession(sessionId);
    }

    @GetMapping("/rag/sessions")
    @Operation(summary = "获取会话历史列表")
    public Result<Map<String, Object>> getSessionHistory(@RequestParam Map<String, Object> params) {
        log.debug("获取会话列表");
        return aiServiceClient.getSessionHistory(params);
    }

    @DeleteMapping("/rag/sessions/{session_id}")
    @Operation(summary = "清除对话历史")
    public Result<Map<String, Object>> clearSession(@PathVariable("session_id") String sessionId) {
        log.debug("清除会话: {}", sessionId);
        return aiServiceClient.clearSession(sessionId);
    }

    @PostMapping("/rag/feedback")
    @Operation(summary = "提交答案反馈")
    public Result<Map<String, Object>> submitFeedback(@RequestBody Map<String, Object> request) {
        log.debug("提交反馈: session={}", request.get("session_id"));
        return aiServiceClient.submitFeedback(request);
    }

    @GetMapping("/rag/stats/daily")
    @Operation(summary = "获取每日统计")
    public Result<Map<String, Object>> getDailyStats() {
        log.debug("获取每日统计");
        return aiServiceClient.getDailyStats();
    }

    // ==================== 记忆管理 ====================

    @PostMapping("/memory/create")
    @Operation(summary = "创建记忆")
    public Result<Map<String, Object>> createMemory(@RequestBody Map<String, Object> request) {
        log.debug("创建记忆: userId={}", request.get("user_id"));
        return aiServiceClient.createMemory(request);
    }

    @PostMapping("/memory/search")
    @Operation(summary = "搜索记忆")
    public Result<Map<String, Object>> searchMemory(@RequestBody Map<String, Object> request) {
        log.debug("搜索记忆: userId={}", request.get("user_id"));
        return aiServiceClient.searchMemory(request);
    }

    @PostMapping("/memory/extract")
    @Operation(summary = "从对话提取记忆")
    public Result<Map<String, Object>> extractMemory(
            @RequestParam("session_id") String sessionId,
            @RequestParam("user_id") String userId
    ) {
        log.debug("提取记忆: session={}, user={}", sessionId, userId);
        return aiServiceClient.extractMemory(sessionId, userId);
    }

    @GetMapping("/memory/list")
    @Operation(summary = "获取记忆列表")
    public Result<Map<String, Object>> getMemoryList(@RequestParam Map<String, Object> params) {
        log.debug("获取记忆列表: userId={}", params.get("user_id"));
        return aiServiceClient.getMemoryList(params);
    }

    @DeleteMapping("/memory/{memory_id}")
    @Operation(summary = "删除记忆")
    public Result<Map<String, Object>> deleteMemory(@PathVariable("memory_id") String memoryId) {
        log.debug("删除记忆: {}", memoryId);
        return aiServiceClient.deleteMemory(memoryId);
    }

    // ==================== Agent ====================

    @PostMapping("/agent/submit")
    @Operation(summary = "提交Agent任务")
    public Result<Map<String, Object>> submitAgentTask(@RequestBody Map<String, Object> request) {
        log.debug("提交Agent任务: {}", request.get("task_type"));
        return aiServiceClient.submitAgentTask(request);
    }

    @GetMapping("/agent/task/{task_id}")
    @Operation(summary = "获取Agent任务")
    public Result<Map<String, Object>> getAgentTask(@PathVariable("task_id") String taskId) {
        log.debug("获取Agent任务: {}", taskId);
        return aiServiceClient.getAgentTask(taskId);
    }

    @PostMapping("/agent/task/{task_id}/cancel")
    @Operation(summary = "取消Agent任务")
    public Result<Map<String, Object>> cancelAgentTask(@PathVariable("task_id") String taskId) {
        log.debug("取消Agent任务: {}", taskId);
        return aiServiceClient.cancelAgentTask(taskId);
    }

    @GetMapping("/agent/tools")
    @Operation(summary = "获取Agent工具列表")
    public Result<Map<String, Object>> getAgentTools() {
        log.debug("获取Agent工具列表");
        return aiServiceClient.getAgentTools();
    }

    // ==================== 代码沙箱 ====================

    @PostMapping("/sandbox/execute")
    @Operation(summary = "执行代码")
    public Result<Map<String, Object>> executeCode(@RequestBody Map<String, Object> request) {
        log.debug("执行代码: userId={}", request.get("user_id"));
        return aiServiceClient.executeCode(request);
    }

    @GetMapping("/sandbox/result/{task_id}")
    @Operation(summary = "获取执行结果")
    public Result<Map<String, Object>> getSandboxResult(@PathVariable("task_id") String taskId) {
        log.debug("获取沙箱结果: {}", taskId);
        return aiServiceClient.getSandboxResult(taskId);
    }

    @PostMapping("/sandbox/security-check")
    @Operation(summary = "代码安全检查")
    public Result<Map<String, Object>> securityCheck(@RequestParam("code") String code) {
        log.debug("代码安全检查");
        return aiServiceClient.securityCheck(code);
    }

    // ==================== 内容创作辅助 ====================

    @PostMapping("/content/outline")
    @Operation(summary = "生成大纲")
    public Result<Map<String, Object>> generateOutline(@RequestBody Map<String, Object> request) {
        log.debug("生成大纲: topic={}", request.get("topic"));
        return aiServiceClient.generateOutline(request);
    }

    @PostMapping("/content/polish")
    @Operation(summary = "内容润色")
    public Result<Map<String, Object>> polishContent(@RequestBody Map<String, Object> request) {
        log.debug("内容润色");
        return aiServiceClient.polishContent(request);
    }

    @PostMapping("/content/expand")
    @Operation(summary = "内容扩写")
    public Result<Map<String, Object>> expandContent(@RequestBody Map<String, Object> request) {
        log.debug("内容扩写");
        return aiServiceClient.expandContent(request);
    }

    @PostMapping("/content/summarize")
    @Operation(summary = "内容摘要")
    public Result<Map<String, Object>> summarizeContent(@RequestBody Map<String, Object> request) {
        log.debug("内容摘要");
        return aiServiceClient.summarizeContent(request);
    }

    @PostMapping("/content/generate")
    @Operation(summary = "基于知识库生成内容")
    public Result<Map<String, Object>> generateFromKnowledge(@RequestBody Map<String, Object> request) {
        log.debug("知识库生成: topic={}", request.get("topic"));
        return aiServiceClient.generateFromKnowledge(request);
    }

    // ==================== 多Agent协作 ====================

    @GetMapping("/enterprise-agent/scenarios")
    @Operation(summary = "获取协作场景列表")
    public Result<Map<String, Object>> getEnterpriseAgentScenarios() {
        log.debug("获取Agent协作场景列表");
        return aiServiceClient.getEnterpriseAgentScenarios();
    }

    @PostMapping("/enterprise-agent/execute")
    @Operation(summary = "执行协作场景")
    public Result<Map<String, Object>> executeEnterpriseAgentScenario(@RequestBody Map<String, Object> request) {
        log.debug("执行Agent协作: scenarioId={}", request.get("scenarioId"));
        return aiServiceClient.executeEnterpriseAgentScenario(request);
    }

    @GetMapping("/enterprise-agent/session/{session_id}")
    @Operation(summary = "获取协作会话")
    public Result<Map<String, Object>> getEnterpriseAgentSession(@PathVariable("session_id") String sessionId) {
        log.debug("获取Agent协作会话: {}", sessionId);
        return aiServiceClient.getEnterpriseAgentSession(sessionId);
    }

    // ==================== Badcase优化闭环 ====================

    @PostMapping("/badcase-optimizer/experiment")
    @Operation(summary = "创建优化实验")
    public Result<Map<String, Object>> createBadcaseExperiment(@RequestBody Map<String, Object> request) {
        log.debug("创建Badcase优化实验");
        return aiServiceClient.createBadcaseExperiment(request);
    }

    @PostMapping("/badcase-optimizer/experiment/{experiment_id}/run")
    @Operation(summary = "运行优化实验")
    public Result<Map<String, Object>> runBadcaseExperiment(@PathVariable("experiment_id") String experimentId) {
        log.debug("运行优化实验: {}", experimentId);
        return aiServiceClient.runBadcaseExperiment(experimentId);
    }

    @GetMapping("/badcase-optimizer/experiment/{experiment_id}")
    @Operation(summary = "获取优化实验详情")
    public Result<Map<String, Object>> getBadcaseExperiment(@PathVariable("experiment_id") String experimentId) {
        log.debug("获取优化实验: {}", experimentId);
        return aiServiceClient.getBadcaseExperiment(experimentId);
    }

    @PostMapping("/badcase-optimizer/attribute")
    @Operation(summary = "Badcase归因分析")
    public Result<Map<String, Object>> attributeBadcase(@RequestBody Map<String, Object> request) {
        log.debug("Badcase归因分析");
        return aiServiceClient.attributeBadcase(request);
    }

    @PostMapping("/badcase-optimizer/optimization-loop")
    @Operation(summary = "优化闭环执行")
    public Result<Map<String, Object>> optimizationLoop(@RequestBody Map<String, Object> request) {
        log.debug("优化闭环执行");
        return aiServiceClient.optimizationLoop(request);
    }

    // ==================== 领域模型微调 ====================

    @PostMapping("/domain-tuning/dataset/build")
    @Operation(summary = "构建微调数据集")
    public Result<Map<String, Object>> buildTuningDataset(@RequestBody Map<String, Object> request) {
        log.debug("构建微调数据集: name={}", request.get("name"));
        return aiServiceClient.buildTuningDataset(request);
    }

    @PostMapping("/domain-tuning/job")
    @Operation(summary = "创建微调任务")
    public Result<Map<String, Object>> createTuningJob(@RequestBody Map<String, Object> request) {
        log.debug("创建微调任务: name={}", request.get("name"));
        return aiServiceClient.createTuningJob(request);
    }

    @PostMapping("/domain-tuning/job/{job_id}/start")
    @Operation(summary = "启动微调任务")
    public Result<Map<String, Object>> startTuningJob(@PathVariable("job_id") String jobId) {
        log.debug("启动微调任务: {}", jobId);
        return aiServiceClient.startTuningJob(jobId);
    }

    @GetMapping("/domain-tuning/job/{job_id}")
    @Operation(summary = "获取微调任务状态")
    public Result<Map<String, Object>> getTuningJobStatus(@PathVariable("job_id") String jobId) {
        log.debug("获取微调任务: {}", jobId);
        return aiServiceClient.getTuningJobStatus(jobId);
    }

    @GetMapping("/domain-tuning/jobs")
    @Operation(summary = "获取微调任务列表")
    public Result<Map<String, Object>> listTuningJobs() {
        log.debug("获取微调任务列表");
        return aiServiceClient.listTuningJobs();
    }

    // ==================== 智能工作流引擎 ====================

    @GetMapping("/workflow/templates")
    @Operation(summary = "获取工作流模板")
    public Result<Map<String, Object>> getWorkflowTemplates() {
        log.debug("获取工作流模板");
        return aiServiceClient.getWorkflowTemplates();
    }

    @PostMapping("/workflow/create")
    @Operation(summary = "创建工作流")
    public Result<Map<String, Object>> createWorkflow(@RequestBody Map<String, Object> request) {
        log.debug("创建工作流: name={}", request.get("name"));
        return aiServiceClient.createWorkflow(request);
    }

    @GetMapping("/workflow/{workflow_id}")
    @Operation(summary = "获取工作流详情")
    public Result<Map<String, Object>> getWorkflow(@PathVariable("workflow_id") String workflowId) {
        log.debug("获取工作流: {}", workflowId);
        return aiServiceClient.getWorkflow(workflowId);
    }

    @PostMapping("/workflow/{workflow_id}/execute")
    @Operation(summary = "执行工作流")
    public Result<Map<String, Object>> executeWorkflow(
            @PathVariable("workflow_id") String workflowId,
            @RequestBody Map<String, Object> request) {
        log.debug("执行工作流: {}", workflowId);
        return aiServiceClient.executeWorkflow(workflowId, request);
    }

    @GetMapping("/workflow/execution/{execution_id}")
    @Operation(summary = "获取工作流执行详情")
    public Result<Map<String, Object>> getWorkflowExecution(@PathVariable("execution_id") String executionId) {
        log.debug("获取工作流执行: {}", executionId);
        return aiServiceClient.getWorkflowExecution(executionId);
    }

    // ==================== 第三方系统集成 ====================

    @PostMapping("/third-party/wecom/message")
    @Operation(summary = "发送企微消息")
    public Result<Map<String, Object>> sendWecomMessage(@RequestBody Map<String, Object> request) {
        log.debug("发送企微消息");
        return aiServiceClient.sendWecomMessage(request);
    }

    @PostMapping("/third-party/dingtalk/message")
    @Operation(summary = "发送钉钉消息")
    public Result<Map<String, Object>> sendDingtalkMessage(@RequestBody Map<String, Object> request) {
        log.debug("发送钉钉消息");
        return aiServiceClient.sendDingtalkMessage(request);
    }

    @PostMapping("/third-party/webhook")
    @Operation(summary = "发送Webhook")
    public Result<Map<String, Object>> sendWebhook(@RequestBody Map<String, Object> request) {
        log.debug("发送Webhook");
        return aiServiceClient.sendWebhook(request);
    }

    @PostMapping("/third-party/oa/process")
    @Operation(summary = "创建OA流程")
    public Result<Map<String, Object>> createOAProcess(@RequestBody Map<String, Object> request) {
        log.debug("创建OA流程");
        return aiServiceClient.createOAProcess(request);
    }

    @GetMapping("/third-party/webhook/logs")
    @Operation(summary = "获取Webhook日志")
    public Result<Map<String, Object>> getWebhookLogs(@RequestParam(value = "limit", defaultValue = "50") Integer limit) {
        log.debug("获取Webhook日志: limit={}", limit);
        return aiServiceClient.getWebhookLogs(limit);
    }

    // ==================== 质量工具 ====================

    @PostMapping("/quality/faithfulness/validate")
    @Operation(summary = "忠实度校验")
    public Result<Map<String, Object>> validateFaithfulness(@RequestBody Map<String, Object> request) {
        log.debug("忠实度校验");
        return aiServiceClient.validateFaithfulness(request);
    }

    @PostMapping("/quality/faithfulness/quick")
    @Operation(summary = "快速忠实度校验")
    public Result<Map<String, Object>> quickValidateFaithfulness(@RequestBody Map<String, Object> request) {
        log.debug("快速忠实度校验");
        return aiServiceClient.quickValidateFaithfulness(request);
    }

    @PostMapping("/quality/memory/conflict-detect")
    @Operation(summary = "记忆冲突检测")
    public Result<Map<String, Object>> detectMemoryConflicts(@RequestBody Map<String, Object> request) {
        log.debug("记忆冲突检测");
        return aiServiceClient.detectMemoryConflicts(request);
    }

    @PostMapping("/quality/memory/semantic-dedup")
    @Operation(summary = "语义去重")
    public Result<Map<String, Object>> semanticDedup(@RequestBody Map<String, Object> request) {
        log.debug("语义去重");
        return aiServiceClient.semanticDedup(request);
    }
}
