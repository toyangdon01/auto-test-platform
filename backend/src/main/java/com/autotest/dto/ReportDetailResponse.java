package com.autotest.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 报告详情响应
 *
 * @author auto-test-platform
 */
@Data
public class ReportDetailResponse {

    private Long id;

    private Long taskId;
    private String taskName;

    private String title;
    private String summary;
    private String conclusion;

    // 执行概览
    private ExecutionOverview overview;

    // 测试结果列表
    private List<TestResultSummary> results;

    // 指标统计
    private Map<String, MetricStats> metricStats;

    // 对比分析
    private List<ComparisonItem> comparisons;

    // 报告内容（HTML）
    private String content;

    private String fileFormat;
    private String filePath;
    private LocalDateTime createdAt;

    /**
     * 执行概览
     */
    @Data
    public static class ExecutionOverview {
        private int totalServers;
        private int successCount;
        private int failCount;
        private int totalTimeMs;
        private String avgScore;
    }

    /**
     * 测试结果摘要
     */
    @Data
    public static class TestResultSummary {
        private Long id;
        private String serverName;
        private String serverIp;
        private String result;
        private Integer score;
        private Integer durationMs;
        private Map<String, Object> keyMetrics;
    }

    /**
     * 指标统计
     */
    @Data
    public static class MetricStats {
        private String name;
        private Object min;
        private Object max;
        private Object avg;
        private String unit;
    }

    /**
     * 对比项
     */
    @Data
    public static class ComparisonItem {
        private String metric;
        private Object current;
        private Object previous;
        private Double change;
        private String trend; // up/down/stable
    }
}
