package com.autotest.dto;

import lombok.Data;

import java.util.List;

/**
 * 趋势分析响应
 *
 * @author auto-test-platform
 */
@Data
public class TrendAnalysisResponse {

    private String metricName;
    private String unit;
    private List<DataPoint> dataPoints;
    private Statistics statistics;
    private List<Anomaly> anomalies;

    @Data
    public static class DataPoint {
        private String date;
        private Object value;
        private Long taskId;
        private String taskName;
    }

    @Data
    public static class Statistics {
        private Double minValue;
        private Double maxValue;
        private Double avgValue;
        private Double latestValue;
        private Double changeRate;      // 相比上次的变化率
        private String trend;           // up/down/stable
        private Double volatility;      // 波动率
    }

    @Data
    public static class Anomaly {
        private String date;
        private Object value;
        private String type;            // spike/drop/趋势变化
        private String severity;        // low/medium/high
        private String description;
    }
}
