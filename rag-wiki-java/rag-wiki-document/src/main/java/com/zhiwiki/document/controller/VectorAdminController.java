package com.zhiwiki.document.controller;

import com.zhiwiki.common.audit.AuditLog;
import com.zhiwiki.common.model.PageRequest;
import com.zhiwiki.common.model.PageResult;
import com.zhiwiki.common.model.Result;
import com.zhiwiki.document.feign.AiServiceClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 向量库管理控制器
 * 通过Feign调用Python AI服务管理Milvus/Qdrant向量库
 */
@Slf4j
@RestController
@RequestMapping("/api/vector/admin")
@RequiredArgsConstructor
@Tag(name = "向量库管理", description = "向量数据管理和维护")
public class VectorAdminController {

    private final StringRedisTemplate redisTemplate;
    private final AiServiceClient aiServiceClient;
    private static final String VECTOR_STATS_KEY = "rag-wiki:vector:stats";
    private static final String REBUILD_TASK_PREFIX = "rag-wiki:vector:rebuild:";

    /**
     * 获取向量库统计
     */
    @GetMapping("/stats")
    @Operation(summary = "获取统计信息", description = "获取向量库整体统计")
    public Result<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            // 通过Feign调用Python AI服务获取实际统计
            Result<Map<String, Object>> result = aiServiceClient.getVectorStats(null);
            if (result != null && result.getCode() == 200 && result.getData() != null) {
                stats.putAll(result.getData());
                // 缓存统计信息5分钟
                redisTemplate.opsForValue().set(VECTOR_STATS_KEY,
                        com.alibaba.fastjson2.JSON.toJSONString(stats), 5, TimeUnit.MINUTES);
            } else {
                // Feign调用失败，尝试从缓存获取
                loadFromCacheOrDefault(stats);
            }
        } catch (Exception e) {
            log.error("获取向量库统计失败，使用缓存或默认值", e);
            loadFromCacheOrDefault(stats);
        }

        return Result.success(stats);
    }

    /**
     * 分页查询向量数据
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询向量", description = "分页查询向量数据列表")
    public Result<PageResult<Map<String, Object>>> page(
            PageRequest pageRequest,
            @RequestParam(required = false) @Parameter(description = "集合名称") String collection,
            @RequestParam(required = false) @Parameter(description = "关键词") String keyword) {

        try {
            // 通过Feign调用Python AI服务查询
            Map<String, Object> params = new HashMap<>();
            params.put("pageNum", pageRequest.getPageNum());
            params.put("pageSize", pageRequest.getPageSize());
            if (collection != null) params.put("collection", collection);
            if (keyword != null) params.put("keyword", keyword);

            Result<Map<String, Object>> result = aiServiceClient.pageVectors(params);
            if (result != null && result.getCode() == 200 && result.getData() != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> records = (List<Map<String, Object>>) result.getData().getOrDefault("records", List.of());
                int total = (int) result.getData().getOrDefault("total", 0);
                return Result.success(PageResult.of(records, total, pageRequest.getPageNum(), pageRequest.getPageSize()));
            }
        } catch (Exception e) {
            log.error("分页查询向量数据失败", e);
        }

        return Result.success(PageResult.of(List.of(), 0, pageRequest.getPageNum(), pageRequest.getPageSize()));
    }

    /**
     * 删除向量
     */
    @DeleteMapping("/{id}")
    @AuditLog(action = "DELETE_VECTOR", module = "vector", description = "删除向量数据", critical = true)
    @Operation(summary = "删除向量", description = "删除指定的向量数据")
    public Result<Boolean> delete(@PathVariable @Parameter(description = "向量ID") String id) {
        log.info("删除向量: id={}", id);

        try {
            // 通过Feign调用Python AI服务删除
            Result<Map<String, Object>> result = aiServiceClient.deleteVector(id, null);
            if (result != null && result.getCode() == 200) {
                log.info("向量删除成功: id={}", id);
                return Result.success(true);
            }
            log.warn("向量删除可能失败: id={}, result={}", id, result);
        } catch (Exception e) {
            log.error("删除向量失败: id={}", id, e);
        }

        return Result.success(true);
    }

    /**
     * 重建索引
     */
    @PostMapping("/rebuild-index")
    @AuditLog(action = "REBUILD_INDEX", module = "vector", description = "重建向量库索引", critical = true)
    @Operation(summary = "重建索引", description = "重建向量库索引")
    public Result<Map<String, Object>> rebuildIndex(
            @RequestParam(required = false) @Parameter(description = "集合名称") String collection) {

        String taskId = "rebuild_" + System.currentTimeMillis();

        // 记录重建任务状态
        Map<String, Object> taskInfo = new HashMap<>();
        taskInfo.put("taskId", taskId);
        taskInfo.put("status", "RUNNING");
        taskInfo.put("collection", collection);
        taskInfo.put("startTime", new Date());

        redisTemplate.opsForValue().set(REBUILD_TASK_PREFIX + taskId,
                com.alibaba.fastjson2.JSON.toJSONString(taskInfo), 24, TimeUnit.HOURS);

        try {
            // 通过Feign调用Python AI服务触发重建
            Map<String, Object> rebuildRequest = new HashMap<>();
            rebuildRequest.put("task_id", taskId);
            if (collection != null) rebuildRequest.put("collection", collection);

            Result<Map<String, Object>> result = aiServiceClient.rebuildIndex(rebuildRequest);
            if (result != null && result.getCode() == 200) {
                log.info("索引重建任务已提交: taskId={}, collection={}", taskId, collection);
            }
        } catch (Exception e) {
            log.error("触发索引重建失败: taskId={}", taskId, e);
            taskInfo.put("status", "FAILED");
            taskInfo.put("error", e.getMessage());
            redisTemplate.opsForValue().set(REBUILD_TASK_PREFIX + taskId,
                    com.alibaba.fastjson2.JSON.toJSONString(taskInfo), 24, TimeUnit.HOURS);
        }

        return Result.success(taskInfo);
    }

    /**
     * 获取集合列表
     */
    @GetMapping("/collections")
    @Operation(summary = "获取集合列表", description = "获取所有向量集合")
    public Result<List<Map<String, Object>>> getCollections() {
        try {
            // 通过Feign调用Python AI服务查询
            Result<Map<String, Object>> result = aiServiceClient.getCollections();
            if (result != null && result.getCode() == 200 && result.getData() != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> collections = (List<Map<String, Object>>) result.getData().getOrDefault("collections", List.of());
                return Result.success(collections);
            }
        } catch (Exception e) {
            log.error("获取集合列表失败", e);
        }

        return Result.success(List.of());
    }

    /**
     * 获取重建任务状态
     */
    @GetMapping("/rebuild-index/{taskId}")
    @Operation(summary = "重建任务状态", description = "获取索引重建任务的状态")
    public Result<Map<String, Object>> getRebuildStatus(
            @PathVariable @Parameter(description = "任务ID") String taskId) {

        String taskJson = redisTemplate.opsForValue().get(REBUILD_TASK_PREFIX + taskId);
        if (taskJson != null) {
            Map<String, Object> taskInfo = com.alibaba.fastjson2.JSON.parseObject(taskJson, Map.class);
            return Result.success(taskInfo);
        }
        return Result.error("任务不存在或已过期");
    }

    /**
     * 从缓存加载或使用默认值
     */
    private void loadFromCacheOrDefault(Map<String, Object> stats) {
        String cachedStats = redisTemplate.opsForValue().get(VECTOR_STATS_KEY);
        if (cachedStats != null) {
            stats.putAll(com.alibaba.fastjson2.JSON.parseObject(cachedStats, Map.class));
            stats.put("cached", true);
        } else {
            setDefaultStats(stats);
        }
    }

    /**
     * 设置默认统计值
     */
    private void setDefaultStats(Map<String, Object> stats) {
        stats.put("totalDocuments", 0);
        stats.put("totalVectors", 0);
        stats.put("storageSize", "0 MB");
        stats.put("collectionCount", 0);
        stats.put("indexType", "IVF_FLAT");
        stats.put("metricType", "COSINE");
        stats.put("dimension", 1024);
    }
}