package com.autotest.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 结果对比响应
 */
@Data
public class ResultCompareResponse {
    
    /**
     * 对比类型：task / results / servers
     */
    private String compareType;
    
    /**
     * 脚本ID
     */
    private Long scriptId;
    
    /**
     * 脚本名称
     */
    private String scriptName;
    
    /**
     * 对比的结果列表
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
    
    /**
     * 结果项
     */
    @Data
    public static class ResultItem {
        private Long resultId;
        private Long id;
        private Long taskId;
        private Long serverId;
        private String taskName;
        private String serverName;
        private Map<String, Object> parsedData;
        private String result;
        private Integer overallScore;
        private Integer durationMs;
        private String executedAt;
    }
    
    /**
     * 指标对比
     */
    @Data
    public static class MetricCompare {
        private String metricName;
        private List<MetricValue> values;
        private Double changeRate;
        private String trend;  // up / down / stable
    }
    
    /**
     * 指标值
     */
    @Data
    public static class MetricValue {
        private Long resultId;
        private Object value;
        private String displayValue;
    }
    
    /**
     * 差异项
     */
    @Data
    public static class DiffItem {
        private String category;
        private String name;
        private List<ValueChange> changes;
    }
    
    /**
     * 值变化
     */
    @Data
    public static class ValueChange {
        private Long resultId;
        private Object oldValue;
        private Object newValue;
        private Double changePercent;
    }
    
    /**
     * 统计信息
     */
    @Data
    public static class Statistics {
        private Integer totalResults;
        private Integer passCount;
        private Integer failCount;
        private Double avgScore;
        private Double avgDuration;
    }
    
    /**
     * 对比行（用于表格展示）
     */
    @Data
    public static class CompareRow {
        private String field;
        private String displayName;
        private List<Double> values;
        private Double avg;
        private Double min;
        private Double max;
    }
}
