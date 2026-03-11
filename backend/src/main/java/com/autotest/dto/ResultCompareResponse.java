package com.autotest.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 结果对比响应
 *
 * @author auto-test-platform
 */
@Data
public class ResultCompareResponse {

    /**
     * 对比类型
     */
    private String compareType;

    /**
     * 对比结果列表
     */
    private List<ResultItem> results;

    /**
     * 指标对比数据
     */
    private List<MetricCompare> metrics;

    /**
     * 差异分析
     */
    private List<DiffItem> differences;

    /**
     * 统计信息
     */
    private Statistics statistics;

    @Data
    public static class ResultItem {
        private Long resultId;
        private Long taskId;
        private String taskName;
        private Long serverId;
        private String serverName;
        private String result;
        private Integer overallScore;
        private Integer durationMs;
        private String executedAt;
    }

    @Data
    public static class MetricCompare {
        private String metricName;
        private String unit;
        private List<MetricValue> values;
        private Double changeRate;
        private String trend; // up/down/stable
    }

    @Data
    public static class MetricValue {
        private Long resultId;
        private Object value;
        private String displayValue;
    }

    @Data
    public static class DiffItem {
        private String category;
        private String name;
        private List<ValueChange> changes;
    }

    @Data
    public static class ValueChange {
        private Long resultId;
        private Object oldValue;
        private Object newValue;
        private Double changePercent;
    }

    @Data
    public static class Statistics {
        private Integer totalResults;
        private Integer passCount;
        private Integer failCount;
        private Double avgScore;
        private Double avgDuration;
    }
}
