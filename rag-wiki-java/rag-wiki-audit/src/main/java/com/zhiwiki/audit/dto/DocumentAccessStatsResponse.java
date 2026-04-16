package com.zhiwiki.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 文档访问统计响应
 */
@Data
@Schema(description = "文档访问统计")
public class DocumentAccessStatsResponse {

    @Schema(description = "总访问量")
    private Long totalViews;

    @Schema(description = "今日访问量")
    private Long todayViews;

    @Schema(description = "独立访客数")
    private Long uniqueVisitors;

    @Schema(description = "访问趋势（近7天）")
    private List<SystemOverviewResponse.TrendItem> viewTrend;

    @Schema(description = "热门文档Top10")
    private List<HotDocument> hotDocuments;

    @Schema(description = "按安全等级分布")
    private List<LevelDistribution> levelDistribution;

    @Data
    @Schema(description = "热门文档")
    public static class HotDocument {
        @Schema(description = "文档ID")
        private String documentId;

        @Schema(description = "文档名称")
        private String documentName;

        @Schema(description = "访问次数")
        private Long viewCount;

        @Schema(description = "安全等级")
        private Integer securityLevel;
    }

    @Data
    @Schema(description = "安全等级分布")
    public static class LevelDistribution {
        @Schema(description = "安全等级")
        private Integer level;

        @Schema(description = "等级名称")
        private String levelName;

        @Schema(description = "访问次数")
        private Long count;
    }
}
