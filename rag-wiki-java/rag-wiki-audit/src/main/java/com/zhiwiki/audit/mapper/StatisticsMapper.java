package com.zhiwiki.audit.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 统计报表Mapper
 */
@Mapper
public interface StatisticsMapper {

    /**
     * 统计用户数
     */
    @Select("SELECT COUNT(*) FROM sys_user WHERE is_deleted = 0")
    Integer countUsers();

    /**
     * 统计文档数
     */
    @Select("SELECT COUNT(*) FROM document_info WHERE is_deleted = 0")
    Integer countDocuments();

    /**
     * 统计知识库数
     */
    @Select("SELECT COUNT(*) FROM knowledge_space WHERE is_deleted = 0")
    Integer countSpaces();

    /**
     * 统计今日新增文档
     */
    @Select("SELECT COUNT(*) FROM document_info WHERE is_deleted = 0 AND DATE(created_at) = CURDATE()")
    Integer countTodayDocuments();

    /**
     * 统计AI查询次数
     */
    @Select("SELECT COUNT(*) FROM rag_audit_log WHERE created_at BETWEEN #{startTime} AND #{endTime}")
    Integer countAIQueries(@Param("startTime") LocalDateTime startTime, 
                           @Param("endTime") LocalDateTime endTime);

    /**
     * 统计AI成功查询次数
     */
    @Select("SELECT COUNT(*) FROM rag_audit_log WHERE is_success = 1 AND created_at BETWEEN #{startTime} AND #{endTime}")
    Integer countSuccessfulAIQueries(@Param("startTime") LocalDateTime startTime, 
                                     @Param("endTime") LocalDateTime endTime);

    /**
     * 获取平均响应时间
     */
    @Select("SELECT AVG(response_time) FROM rag_audit_log WHERE created_at BETWEEN #{startTime} AND #{endTime}")
    Double getAvgResponseTime(@Param("startTime") LocalDateTime startTime, 
                              @Param("endTime") LocalDateTime endTime);

    /**
     * 获取每日趋势
     */
    @Select("SELECT DATE(created_at) as date, COUNT(*) as count, AVG(response_time) as avgTime " +
            "FROM rag_audit_log WHERE created_at BETWEEN #{startTime} AND #{endTime} " +
            "GROUP BY DATE(created_at) ORDER BY date")
    List<Map<String, Object>> getDailyTrend(@Param("startTime") LocalDateTime startTime, 
                                            @Param("endTime") LocalDateTime endTime);

    /**
     * 按文档类型统计
     */
    @Select("SELECT document_type as type, COUNT(*) as count FROM document_info " +
            "WHERE is_deleted = 0 GROUP BY document_type")
    List<Map<String, Object>> countDocumentsByType();

    /**
     * 按知识库统计文档
     */
    @Select("SELECT s.space_name as spaceName, COUNT(d.id) as count " +
            "FROM knowledge_space s LEFT JOIN document_info d ON s.space_id = d.space_id AND d.is_deleted = 0 " +
            "WHERE s.is_deleted = 0 GROUP BY s.space_id, s.space_name")
    List<Map<String, Object>> countDocumentsBySpace();

    /**
     * 获取文档增长趋势（最近30天）
     */
    @Select("SELECT DATE(created_at) as date, COUNT(*) as count " +
            "FROM document_info WHERE is_deleted = 0 AND created_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) " +
            "GROUP BY DATE(created_at) ORDER BY date")
    List<Map<String, Object>> getDocumentGrowthTrend();

    /**
     * 统计活跃用户
     */
    @Select("SELECT COUNT(DISTINCT user_id) FROM rag_audit_log WHERE created_at BETWEEN #{startTime} AND #{endTime}")
    Integer countActiveUsers(@Param("startTime") LocalDateTime startTime, 
                             @Param("endTime") LocalDateTime endTime);

    /**
     * 获取用户查询排行
     */
    @Select("SELECT u.username as username, COUNT(*) as queryCount " +
            "FROM rag_audit_log a JOIN sys_user u ON a.user_id = u.user_id " +
            "WHERE a.created_at BETWEEN #{startTime} AND #{endTime} " +
            "GROUP BY a.user_id, u.username ORDER BY queryCount DESC LIMIT 10")
    List<Map<String, Object>> getUserQueryRanking(@Param("startTime") LocalDateTime startTime, 
                                                   @Param("endTime") LocalDateTime endTime);

    /**
     * 获取每日活跃用户
     */
    @Select("SELECT DATE(created_at) as date, COUNT(DISTINCT user_id) as activeCount " +
            "FROM rag_audit_log WHERE created_at BETWEEN #{startTime} AND #{endTime} " +
            "GROUP BY DATE(created_at) ORDER BY date")
    List<Map<String, Object>> getDailyActiveUsers(@Param("startTime") LocalDateTime startTime, 
                                                   @Param("endTime") LocalDateTime endTime);

    /**
     * 按Badcase类型统计
     */
    @Select("SELECT badcase_type as type, COUNT(*) as count FROM rag_badcase " +
            "GROUP BY badcase_type")
    List<Map<String, Object>> countBadcasesByType();

    /**
     * 按Badcase来源统计
     */
    @Select("SELECT source, COUNT(*) as count FROM rag_badcase " +
            "GROUP BY source")
    List<Map<String, Object>> countBadcasesBySeverity();

    /**
     * 统计Badcase总数
     */
    @Select("SELECT COUNT(*) FROM rag_badcase")
    Integer countTotalBadcases();

    /**
     * 统计已解决Badcase
     */
    @Select("SELECT COUNT(*) FROM rag_badcase WHERE status = 'FIXED'")
    Integer countResolvedBadcases();

    /**
     * 获取热门查询
     */
    @Select("SELECT query, COUNT(*) as count " +
            "FROM rag_audit_log WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) " +
            "GROUP BY query ORDER BY count DESC LIMIT #{limit}")
    List<Map<String, Object>> getHotQueries(@Param("limit") int limit);

    /**
     * 获取热门文档
     */
    @Select("SELECT document_name as documentName, creator_id as creator, created_at as createdAt " +
            "FROM document_info WHERE is_deleted = 0 " +
            "ORDER BY created_at DESC LIMIT #{limit}")
    List<Map<String, Object>> getHotDocuments(@Param("limit") int limit);
}
