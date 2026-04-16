package com.zhiwiki.common.audit;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 审计日志存储服务
 * 
 * 存储策略：
 * 1. 实时写入Redis（最近7天热数据，用于快速查询和统计）
 * 2. 异步写入Elasticsearch（全量冷热数据，用于长期检索分析）
 * 3. 定期归档到对象存储（超过90天的历史数据）
 * 
 * ES索引策略：
 * - 按月分索引：rag-wiki-audit-2026-04
 * - 支持按时间范围、操作类型、用户ID等多维度检索
 * 
 * 降级策略：
 * - ES不可用时自动降级为仅Redis存储
 * - ES写入失败不影响业务和Redis写入
 * - 通过 esAvailable 标志避免持续尝试不可用的ES
 */
@Slf4j
@Service
public class AuditLogStorageService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ElasticsearchClient esClient;

    private static final String AUDIT_RECENT_PREFIX = "rag-wiki:audit:recent:";
    private static final String AUDIT_LIST_PREFIX = "rag-wiki:audit:list:";
    private static final String AUDIT_STATS_PREFIX = "rag-wiki:audit:stats:";
    private static final String ES_INDEX_PREFIX = "rag-wiki-audit-";

    /** ES可用标志，连续失败3次后标记为不可用，每60秒重试一次 */
    private volatile boolean esAvailable = true;
    private volatile int esFailCount = 0;
    private volatile long lastEsRetryTime = 0;
    private static final int MAX_ES_FAIL_COUNT = 3;
    private static final long ES_RETRY_INTERVAL_MS = 60_000;

    @Autowired
    public AuditLogStorageService(StringRedisTemplate redisTemplate, 
                                   ObjectMapper objectMapper,
                                   @Autowired(required = false) ElasticsearchClient esClient) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.esClient = esClient;
        if (esClient != null) {
            log.info("AuditLogStorageService初始化：Redis + Elasticsearch双写模式");
        } else {
            log.info("AuditLogStorageService初始化：仅Redis模式（ES未启用）");
        }
    }

    /**
     * 存储审计日志
     * 双写：Redis热数据 + ES冷热数据
     */
    @Async
    public void store(AuditLogAspect.AuditRecord record) {
        try {
            // 1. 写入Redis热数据（7天过期）
            String json = objectMapper.writeValueAsString(record);
            String redisKey = AUDIT_RECENT_PREFIX + record.getTraceId();
            redisTemplate.opsForValue().set(redisKey, json, 7, TimeUnit.DAYS);

            // 2. 追加到用户最近日志列表（用于快速查询最近操作）
            String userListKey = AUDIT_LIST_PREFIX + record.getUserId();
            redisTemplate.opsForList().leftPush(userListKey, json);
            redisTemplate.opsForList().trim(userListKey, 0, 199); // 保留最近200条
            redisTemplate.expire(userListKey, 7, TimeUnit.DAYS);

            // 3. 更新统计计数
            updateStats(record);

            // 4. 异步写入ES
            saveToElasticsearch(record);

        } catch (Exception e) {
            log.error("审计日志存储失败: traceId={}, error={}", record.getTraceId(), e.getMessage());
        }
    }

    /**
     * 写入Elasticsearch
     * 按月分索引，索引命名：rag-wiki-audit-YYYY-MM
     * 
     * 降级策略：
     * - ES Client为null：直接跳过（开发模式）
     * - ES不可用：跳过并定期重试恢复
     * - 单次写入失败：计数，超过阈值后标记ES不可用
     */
    private void saveToElasticsearch(AuditLogAspect.AuditRecord record) {
        // ES未启用，直接跳过
        if (esClient == null) {
            return;
        }

        // ES标记为不可用，检查是否到了重试时间
        if (!esAvailable) {
            long now = System.currentTimeMillis();
            if (now - lastEsRetryTime < ES_RETRY_INTERVAL_MS) {
                return; // 未到重试时间，跳过
            }
            lastEsRetryTime = now;
            log.info("ES降级中，尝试重新写入...");
        }

        String indexName = ES_INDEX_PREFIX + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));

        try {
            // 确保索引存在（自动创建，ES默认开启自动创建索引）
            String json = objectMapper.writeValueAsString(record);

            IndexRequest<Void> request = IndexRequest.of(i -> i
                    .index(indexName)
                    .id(record.getTraceId())
                    .withJson(new StringReader(json))
            );

            esClient.index(request);

            // 写入成功，重置失败计数
            if (!esAvailable) {
                log.info("ES恢复正常写入");
            }
            esAvailable = true;
            esFailCount = 0;

            log.debug("审计日志写入ES成功: index={}, traceId={}", indexName, record.getTraceId());

        } catch (Exception e) {
            esFailCount++;
            log.warn("审计日志写入ES失败({}/{}): index={}, traceId={}, error={}", 
                    esFailCount, MAX_ES_FAIL_COUNT, indexName, record.getTraceId(), e.getMessage());

            if (esFailCount >= MAX_ES_FAIL_COUNT) {
                esAvailable = false;
                lastEsRetryTime = System.currentTimeMillis();
                log.error("ES连续写入失败{}次，降级为仅Redis存储，将在{}秒后重试", 
                        MAX_ES_FAIL_COUNT, ES_RETRY_INTERVAL_MS / 1000);
            }
        }
    }

    /**
     * 更新审计统计
     */
    private void updateStats(AuditLogAspect.AuditRecord record) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // 按操作类型统计
        String typeKey = AUDIT_STATS_PREFIX + "type:" + today;
        redisTemplate.opsForHash().increment(typeKey, record.getAction(), 1);

        // 按用户统计
        String userKey = AUDIT_STATS_PREFIX + "user:" + today;
        redisTemplate.opsForHash().increment(userKey, record.getUserId(), 1);

        // 按模块统计
        String moduleKey = AUDIT_STATS_PREFIX + "module:" + today;
        redisTemplate.opsForHash().increment(moduleKey, record.getModule(), 1);

        // 按成功/失败统计
        String statusKey = AUDIT_STATS_PREFIX + "status:" + today;
        String statusField = record.isSuccess() ? "success" : "failure";
        redisTemplate.opsForHash().increment(statusKey, statusField, 1);

        // 设置过期时间30天
        redisTemplate.expire(typeKey, 30, TimeUnit.DAYS);
        redisTemplate.expire(userKey, 30, TimeUnit.DAYS);
        redisTemplate.expire(moduleKey, 30, TimeUnit.DAYS);
        redisTemplate.expire(statusKey, 30, TimeUnit.DAYS);
    }

    /**
     * 查询最近的审计日志
     * 优先从Redis热数据查询，不足时从ES补充
     */
    public List<Map<String, Object>> queryRecentLogs(String userId, int limit) {
        List<Map<String, Object>> results = new ArrayList<>();

        // 1. 从Redis列表获取最近日志
        try {
            String userListKey = AUDIT_LIST_PREFIX + userId;
            List<String> logs = redisTemplate.opsForList().range(userListKey, 0, limit - 1);
            if (logs != null) {
                for (String logJson : logs) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> logMap = objectMapper.readValue(logJson, Map.class);
                    results.add(logMap);
                }
            }
        } catch (Exception e) {
            log.warn("从Redis查询最近日志失败: userId={}, error={}", userId, e.getMessage());
        }

        // 2. Redis数据不足时，从ES补充
        if (results.size() < limit && esClient != null && esAvailable) {
            try {
                List<Map<String, Object>> esResults = searchFromElasticsearch(userId, limit - results.size());
                results.addAll(esResults);
            } catch (Exception e) {
                log.warn("从ES补充查询日志失败: userId={}, error={}", userId, e.getMessage());
            }
        }

        return results;
    }

    /**
     * 从Elasticsearch查询审计日志
     */
    private List<Map<String, Object>> searchFromElasticsearch(String userId, int limit) {
        try {
            // 搜索最近3个月的索引
            String indexPattern = ES_INDEX_PREFIX + "*";
            
            SearchResponse<Map> response = esClient.search(s -> s
                    .index(indexPattern)
                    .size(limit)
                    .query(q -> q
                            .bool(b -> b
                                    .must(m -> m
                                            .term(t -> t
                                                    .field("userId")
                                                    .value(userId)
                                            )
                                    )
                            )
                    )
                    .sort(so -> so
                            .field(f -> f
                                    .field("timestamp")
                                    .order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)
                            )
                    ),
                    Map.class
            );

            List<Map<String, Object>> results = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                if (hit.source() != null) {
                    results.add(hit.source());
                }
            }
            return results;

        } catch (Exception e) {
            log.warn("ES查询审计日志失败: userId={}, error={}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取审计统计
     */
    public Map<Object, Object> getActionStats(String date) {
        return redisTemplate.opsForHash().entries(AUDIT_STATS_PREFIX + "type:" + date);
    }

    /**
     * 检查ES是否可用
     */
    public boolean isEsAvailable() {
        return esClient != null && esAvailable;
    }
}
