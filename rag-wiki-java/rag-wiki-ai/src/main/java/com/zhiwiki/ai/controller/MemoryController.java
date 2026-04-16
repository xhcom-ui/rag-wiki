package com.zhiwiki.ai.controller;

import com.zhiwiki.ai.entity.UserMemory;
import com.zhiwiki.ai.service.MemoryService;
import com.zhiwiki.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 记忆管理控制器
 */
@RestController
@RequestMapping("/api/ai/memory")
@RequiredArgsConstructor
@Tag(name = "记忆管理", description = "用户长期记忆与会话短期记忆管理")
public class MemoryController {

    private final MemoryService memoryService;

    /**
     * 获取用户长期记忆列表
     */
    @GetMapping("/long-term/{userId}")
    @Operation(summary = "获取长期记忆", description = "获取用户的长期记忆列表")
    public Result<List<UserMemory>> getLongTermMemories(
            @PathVariable @Parameter(description = "用户ID") String userId,
            @RequestParam(required = false) @Parameter(description = "记忆类型") String memoryType) {
        
        if (memoryType != null && !memoryType.isEmpty()) {
            return Result.success(memoryService.getMemoriesByType(userId, memoryType));
        }
        return Result.success(memoryService.retrieveMemories(userId, null, 50));
    }

    /**
     * 保存长期记忆
     */
    @PostMapping("/long-term")
    @Operation(summary = "保存长期记忆", description = "保存用户长期记忆")
    public Result<UserMemory> saveLongTermMemory(@RequestBody Map<String, Object> request) {
        String userId = (String) request.get("userId");
        String content = (String) request.get("content");
        String memoryType = (String) request.get("memoryType");
        String sourceSessionId = (String) request.get("sourceSessionId");
        Float confidenceScore = request.get("confidenceScore") != null ? 
                ((Number) request.get("confidenceScore")).floatValue() : 1.0f;
        
        UserMemory memory = memoryService.saveLongTermMemory(
                userId, content, memoryType, sourceSessionId, confidenceScore);
        return Result.success(memory);
    }

    /**
     * 删除长期记忆
     */
    @DeleteMapping("/long-term/{memoryId}")
    @Operation(summary = "删除长期记忆", description = "删除指定长期记忆")
    public Result<Boolean> deleteLongTermMemory(
            @PathVariable @Parameter(description = "记忆ID") String memoryId) {
        return Result.success(memoryService.deleteMemory(memoryId));
    }

    /**
     * 获取会话历史
     */
    @GetMapping("/session/{sessionId}")
    @Operation(summary = "获取会话历史", description = "获取会话的短期记忆(消息历史)")
    public Result<List<Map<String, Object>>> getSessionHistory(
            @PathVariable @Parameter(description = "会话ID") String sessionId) {
        return Result.success(memoryService.getSessionHistory(sessionId).stream()
                .map(m -> Map.of(
                        "id", m.getId(),
                        "sessionId", m.getSessionId(),
                        "role", m.getMessageRole(),
                        "content", m.getContent(),
                        "index", m.getMessageIndex(),
                        "createdAt", m.getCreatedAt()
                ))
                .toList());
    }

    /**
     * 添加会话消息
     */
    @PostMapping("/session/{sessionId}/message")
    @Operation(summary = "添加会话消息", description = "向会话添加一条消息")
    public Result<Map<String, Object>> addSessionMessage(
            @PathVariable @Parameter(description = "会话ID") String sessionId,
            @RequestBody Map<String, String> request) {
        
        String userId = request.get("userId");
        String role = request.get("role");
        String content = request.get("content");
        
        var message = memoryService.addSessionMessage(sessionId, userId, role, content);
        return Result.success(Map.of(
                "id", message.getId(),
                "sessionId", message.getSessionId(),
                "index", message.getMessageIndex(),
                "createdAt", message.getCreatedAt()
        ));
    }

    /**
     * 清空会话
     */
    @DeleteMapping("/session/{sessionId}")
    @Operation(summary = "清空会话", description = "清空会话的所有消息")
    public Result<Boolean> clearSession(
            @PathVariable @Parameter(description = "会话ID") String sessionId) {
        memoryService.clearSession(sessionId);
        return Result.success(true);
    }

    /**
     * 从会话提取长期记忆
     */
    @PostMapping("/session/{sessionId}/extract")
    @Operation(summary = "提取长期记忆", description = "从会话中提取并保存长期记忆")
    public Result<Boolean> extractMemory(
            @PathVariable @Parameter(description = "会话ID") String sessionId,
            @RequestParam @Parameter(description = "用户ID") String userId) {
        
        memoryService.extractAndStoreLongTermMemory(sessionId, userId);
        return Result.success(true);
    }

    /**
     * 获取用户最近会话列表
     */
    @GetMapping("/sessions/{userId}")
    @Operation(summary = "获取最近会话", description = "获取用户最近的会话ID列表")
    public Result<List<String>> getRecentSessions(
            @PathVariable @Parameter(description = "用户ID") String userId,
            @RequestParam(defaultValue = "10") @Parameter(description = "数量限制") int limit) {
        return Result.success(memoryService.getUserRecentSessions(userId, limit));
    }

    /**
     * 手动清理过期记忆
     */
    @PostMapping("/cleanup")
    @Operation(summary = "清理过期记忆", description = "手动触发过期记忆清理任务")
    public Result<Integer> cleanupExpiredMemories() {
        return Result.success(memoryService.cleanupExpiredMemories());
    }
}