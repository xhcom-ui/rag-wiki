package com.zhiwiki.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * AI调用统计响应
 */
@Data
@Schema(description = "AI调用统计")
public class AIStatisticsResponse {

    @Schema(description = "总调用次数")
    private Long totalCalls;

    @Schema(description = "成功次数")
    private Long successCalls;

    @Schema(description = "失败次数")
    private Long failedCalls;

    @Schema(description = "成功率")
    private Double successRate;

    @Schema(description = "平均响应时间(ms)")
    private Double avgResponseTime;

    @Schema(description = "P50响应时间(ms)")
    private Long p50ResponseTime;

    @Schema(description = "P95响应时间(ms)")
    private Long p95ResponseTime;

    @Schema(description = "P99响应时间(ms)")
    private Long p99ResponseTime;

    @Schema(description = "按时间段分布")
    private List<TimeDistribution> hourlyDistribution;

    @Schema(description = "按用户分布Top10")
    private List<UserUsage> topUsers;

    @Data
    @Schema(description = "时间分布")
    public static class TimeDistribution {
        @Schema(description = "小时")
        private Integer hour;

        @Schema(description = "调用次数")
        private Long count;
    }

    @Data
    @Schema(description = "用户使用量")
    public static class UserUsage {
        @Schema(description = "用户ID")
        private String userId;

        @Schema(description = "调用次数")
        private Long count;
    }
}
