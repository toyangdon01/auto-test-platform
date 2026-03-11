package com.autotest.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 测试结果详情响应
 *
 * @author auto-test-platform
 */
@Data
public class TestResultDetailResponse {

    private Long id;

    // 任务信息
    private Long taskId;
    private String taskName;

    // 服务器信息
    private Long serverId;
    private String serverName;
    private String serverIp;

    // 结果信息
    private String result;
    private String resultReason;
    private Integer overallScore;

    // 指标数据
    private Map<String, Object> metrics;
    private List<MetricItem> metricList;

    // 输出
    private String rawOutput;
    private String rawError;
    private Map<String, Object> outputFiles;

    // 执行信息
    private Integer exitCode;
    private Integer durationMs;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;

    /**
     * 指标项
     */
    @Data
    public static class MetricItem {
        private String key;
        private String name;
        private Object value;
        private String unit;
        private Object baseline;
        private String status;
    }
}
