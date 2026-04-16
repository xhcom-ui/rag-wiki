package com.zhiwiki.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 系统统计概览响应
 */
@Data
@Schema(description = "系统统计概览")
public class SystemOverviewResponse {

    @Schema(description = "用户统计")
    private UserStats userStats;

    @Schema(description = "文档统计")
    private DocumentStats documentStats;

    @Schema(description = "AI问答统计")
    private AIStats aiStats;

    @Schema(description = "审批统计")
    private ApprovalStats approvalStats;

    @Data
    @Schema(description = "用户统计")
    public static class UserStats {
        @Schema(description = "总用户数")
        private Long totalUsers;

        @Schema(description = "活跃用户数（近7天）")
        private Long activeUsers;

        @Schema(description = "今日新增用户")
        private Long todayNewUsers;

        @Schema(description = "用户增长趋势（近7天）")
        private List<TrendItem> userTrend;
    }

    @Data
    @Schema(description = "文档统计")
    public static class DocumentStats {
        @Schema(description = "总文档数")
        private Long totalDocuments;

        @Schema(description = "总知识库数")
        private Long totalSpaces;

        @Schema(description = "今日新增文档")
        private Long todayNewDocuments;

        @Schema(description = "待解析文档")
        private Long pendingParse;

        @Schema(description = "解析失败文档")
        private Long failedParse;

        @Schema(description = "文档增长趋势（近7天）")
        private List<TrendItem> documentTrend;

        @Schema(description = "按类型分布")
        private Map<String, Long> typeDistribution;
    }

    @Data
    @Schema(description = "AI问答统计")
    public static class AIStats {
        @Schema(description = "总问答次数")
        private Long totalQueries;

        @Schema(description = "今日问答次数")
        private Long todayQueries;

        @Schema(description = "平均响应时间(ms)")
        private Double avgResponseTime;

        @Schema(description = "成功率")
        private Double successRate;

        @Schema(description = "点赞率")
        private Double likeRate;

        @Schema(description = "问答趋势（近7天）")
        private List<TrendItem> queryTrend;

        @Schema(description = "热门问题Top10")
        private List<HotQuery> hotQueries;
    }

    @Data
    @Schema(description = "审批统计")
    public static class ApprovalStats {
        @Schema(description = "待审批数量")
        private Long pendingCount;

        @Schema(description = "今日审批数量")
        private Long todayProcessed;

        @Schema(description = "平均审批时长(小时)")
        private Double avgProcessingTime;

        @Schema(description = "审批通过率")
        private Double approvalRate;
    }

    @Data
    @Schema(description = "趋势项")
    public static class TrendItem {
        @Schema(description = "日期")
        private String date;

        @Schema(description = "数量")
        private Long count;
    }

    @Data
    @Schema(description = "热门问题")
    public static class HotQuery {
        @Schema(description = "问题内容")
        private String query;

        @Schema(description = "查询次数")
        private Long count;
    }
}
