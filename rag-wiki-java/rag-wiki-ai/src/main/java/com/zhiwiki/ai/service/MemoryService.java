package com.zhiwiki.ai.service;

import com.zhiwiki.ai.entity.SessionMemory;
import com.zhiwiki.ai.entity.UserMemory;
import com.zhiwiki.ai.mapper.SessionMemoryMapper;
import com.zhiwiki.ai.mapper.UserMemoryMapper;
import com.zhiwiki.common.model.PageRequest;
import com.zhiwiki.common.model.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 智能记忆管理服务
 * 支持短期记忆(会话上下文)和长期记忆(用户知识)的管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryService {

    private final UserMemoryMapper userMemoryMapper;
    private final SessionMemoryMapper sessionMemoryMapper;

    // ==================== 长期记忆管理 ====================

    /**
     * 保存长期记忆
     */
    @Transactional
    public UserMemory saveLongTermMemory(String userId, String content, String memoryType, 
                                          String sourceSessionId, Float confidenceScore) {
        UserMemory memory = new UserMemory();
        memory.setMemoryId(UUID.randomUUID().toString().replace("-", ""));
        memory.setUserId(userId);
        memory.setContent(content);
        memory.setMemoryType(memoryType); // SEMANTIC/EPISODIC/PROCEDURAL
        memory.setSourceSessionId(sourceSessionId);
        memory.setConfidenceScore(confidenceScore != null ? confidenceScore : 1.0f);
        memory.setTtl(-1L); // 默认永久
        memory.setOperationType("ADD");
        memory.setIsDeleted(0);
        memory.setCreatedAt(LocalDateTime.now());
        memory.setUpdatedAt(LocalDateTime.now());
        
        userMemoryMapper.insert(memory);
        log.info("长期记忆已保存: userId={}, memoryType={}", userId, memoryType);
        return memory;
    }

    /**
     * 检索用户相关记忆
     */
    public List<UserMemory> retrieveMemories(String userId, String query, int limit) {
        // 简化实现：返回用户最近的长期记忆
        // 实际应使用向量相似度检索
        List<UserMemory> memories = userMemoryMapper.selectByUserId(userId);
        return memories.stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 根据类型获取记忆
     */
    public List<UserMemory> getMemoriesByType(String userId, String memoryType) {
        return userMemoryMapper.selectByUserIdAndType(userId, memoryType);
    }

    /**
     * 删除记忆
     */
    @Transactional
    public boolean deleteMemory(String memoryId) {
        UserMemory memory = userMemoryMapper.selectById(memoryId);
        if (memory == null) {
            return false;
        }
        memory.setIsDeleted(1);
        memory.setOperationType("DELETE");
        memory.setUpdatedAt(LocalDateTime.now());
        userMemoryMapper.updateById(memory);
        return true;
    }

    /**
     * 清理过期记忆
     */
    @Transactional
    public int cleanupExpiredMemories() {
        List<UserMemory> expiredMemories = userMemoryMapper.selectExpiredMemories();
        int count = 0;
        for (UserMemory memory : expiredMemories) {
            memory.setIsDeleted(1);
            memory.setUpdatedAt(LocalDateTime.now());
            userMemoryMapper.updateById(memory);
            count++;
        }
        log.info("已清理{}条过期记忆", count);
        return count;
    }

    // ==================== 短期记忆管理 ====================

    /**
     * 添加会话消息
     */
    @Transactional
    public SessionMemory addSessionMessage(String sessionId, String userId, 
                                           String messageRole, String content) {
        int nextIndex = sessionMemoryMapper.getMaxMessageIndex(sessionId) + 1;
        
        SessionMemory message = new SessionMemory();
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setMessageRole(messageRole); // user/assistant/system
        message.setContent(content);
        message.setMessageIndex(nextIndex);
        message.setIsDeleted(0);
        message.setCreatedAt(LocalDateTime.now());
        
        sessionMemoryMapper.insert(message);
        return message;
    }

    /**
     * 获取会话历史
     */
    public List<SessionMemory> getSessionHistory(String sessionId) {
        return sessionMemoryMapper.selectBySessionId(sessionId);
    }

    /**
     * 格式化会话历史为Prompt上下文
     */
    public String formatSessionContext(String sessionId, int maxMessages) {
        List<SessionMemory> messages = getSessionHistory(sessionId);
        
        // 只取最近N条消息
        int startIndex = Math.max(0, messages.size() - maxMessages);
        List<SessionMemory> recentMessages = messages.subList(startIndex, messages.size());
        
        StringBuilder context = new StringBuilder();
        for (SessionMemory msg : recentMessages) {
            String roleName = switch (msg.getMessageRole()) {
                case "user" -> "用户";
                case "assistant" -> "助手";
                case "system" -> "系统";
                default -> msg.getMessageRole();
            };
            context.append(String.format("%s: %s\n", roleName, msg.getContent()));
        }
        
        return context.toString();
    }

    /**
     * 清空会话历史
     */
    @Transactional
    public void clearSession(String sessionId) {
        List<SessionMemory> messages = sessionMemoryMapper.selectBySessionId(sessionId);
        for (SessionMemory msg : messages) {
            msg.setIsDeleted(1);
            sessionMemoryMapper.updateById(msg);
        }
        log.info("会话已清空: sessionId={}", sessionId);
    }

    /**
     * 获取用户最近会话列表
     */
    public List<String> getUserRecentSessions(String userId, int limit) {
        return sessionMemoryMapper.selectRecentSessions(userId, limit);
    }

    // ==================== 记忆提取与存储 ====================

    /**
     * 从会话中提取长期记忆
     * 在对话结束后自动调用
     */
    @Transactional
    public void extractAndStoreLongTermMemory(String sessionId, String userId) {
        List<SessionMemory> messages = sessionMemoryMapper.selectBySessionId(sessionId);
        if (messages.isEmpty()) {
            return;
        }

        // 简化实现：提取关键信息保存为情节记忆
        // 实际应使用LLM分析对话内容，提取结构化记忆
        StringBuilder conversationSummary = new StringBuilder();
        conversationSummary.append("会话摘要: ");
        
        for (SessionMemory msg : messages) {
            if ("user".equals(msg.getMessageRole())) {
                conversationSummary.append("用户询问: ").append(msg.getContent()).append("; ");
            }
        }

        // 保存为情节记忆
        saveLongTermMemory(userId, conversationSummary.toString(), 
                          "EPISODIC", sessionId, 0.8f);
        
        log.info("已从会话提取长期记忆: sessionId={}, userId={}", sessionId, userId);
    }

    /**
     * 格式化长期记忆为Prompt上下文
     */
    public String formatLongTermMemoryContext(String userId, int maxMemories) {
        List<UserMemory> memories = userMemoryMapper.selectByUserId(userId);
        
        if (memories.isEmpty()) {
            return "";
        }

        // 只取最近N条记忆
        List<UserMemory> recentMemories = memories.stream()
                .limit(maxMemories)
                .collect(Collectors.toList());

        StringBuilder context = new StringBuilder();
        context.append("=== 用户相关背景知识 ===\n");
        
        for (UserMemory memory : recentMemories) {
            String typeName = switch (memory.getMemoryType()) {
                case "SEMANTIC" -> "知识";
                case "EPISODIC" -> "经历";
                case "PROCEDURAL" -> "技能";
                default -> memory.getMemoryType();
            };
            context.append(String.format("[%s] %s\n", typeName, memory.getContent()));
        }
        
        context.append("===\n\n");
        return context.toString();
    }
}