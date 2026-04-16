package com.zhiwiki.job.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 定时任务Mapper
 */
@Mapper
public interface JobMapper {

    /**
     * 同步解析任务状态
     */
    @Update("UPDATE document_parse_task t " +
            "JOIN document_info d ON t.document_id = d.document_id " +
            "SET t.status = CASE " +
            "    WHEN d.status = 1 THEN 'COMPLETED' " +
            "    WHEN d.status = 2 THEN 'FAILED' " +
            "    ELSE t.status " +
            "END " +
            "WHERE t.status IN ('PARSING', 'VECTORIZING') " +
            "AND t.updated_at < DATE_SUB(NOW(), INTERVAL 1 HOUR)")
    int syncParseTaskStatus();

    /**
     * 标记超时的解析任务
     */
    @Update("UPDATE document_parse_task " +
            "SET status = 'FAILED', error_message = '任务执行超时' " +
            "WHERE status IN ('PENDING', 'PARSING', 'VECTORIZING') " +
            "AND updated_at < DATE_SUB(NOW(), INTERVAL 2 HOUR)")
    int markTimeoutTasks();

    /**
     * 统计各状态任务数量
     */
    @Select("SELECT status, COUNT(*) as cnt FROM document_parse_task GROUP BY status")
    List<Map<String, Object>> countTaskByStatus();

    /**
     * 查询今日问答数
     */
    @Select("SELECT COUNT(*) FROM rag_audit_log WHERE DATE(created_at) = #{date}")
    Integer countTodayQA(@Param("date") String date);

    /**
     * 查询今日活跃用户数
     */
    @Select("SELECT COUNT(DISTINCT user_id) FROM rag_audit_log WHERE DATE(created_at) = #{date}")
    Integer countTodayActiveUsers(@Param("date") String date);

    /**
     * 查询今日新增文档数
     */
    @Select("SELECT COUNT(*) FROM document_info WHERE DATE(created_at) = #{date}")
    Integer countTodayNewDocs(@Param("date") String date);

    /**
     * 查询今日文档解析数
     */
    @Select("SELECT COUNT(*) FROM document_parse_task WHERE DATE(created_at) = #{date} AND status = 'COMPLETED'")
    Integer countTodayParseCompleted(@Param("date") String date);

    /**
     * 插入或更新日报统计
     */
    @Update("INSERT INTO statistics_daily (stat_date, qa_count, active_users, new_docs, parse_count, created_at) " +
            "VALUES (#{date}, #{qaCount}, #{activeUsers}, #{newDocs}, #{parseCount}, NOW()) " +
            "ON DUPLICATE KEY UPDATE qa_count = #{qaCount}, active_users = #{activeUsers}, " +
            "new_docs = #{newDocs}, parse_count = #{parseCount}, updated_at = NOW()")
    int insertOrUpdateDailyReport(@Param("date") String date,
                                   @Param("qaCount") Integer qaCount,
                                   @Param("activeUsers") Integer activeUsers,
                                   @Param("newDocs") Integer newDocs,
                                   @Param("parseCount") Integer parseCount);

    /**
     * 清理旧审计日志
     */
    @Update("DELETE FROM rag_audit_log WHERE created_at < DATE_SUB(NOW(), INTERVAL 30 DAY)")
    int cleanOldAuditLogs();

    /**
     * 检查用户是否有效
     */
    @Select("SELECT status FROM sys_user WHERE user_id = #{userId} AND is_deleted = 0")
    Integer getUserStatus(@Param("userId") String userId);
}
