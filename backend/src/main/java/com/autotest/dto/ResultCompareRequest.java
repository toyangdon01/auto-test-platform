package com.autotest.dto;

import lombok.Data;

import java.util.List;

/**
 * 结果对比请求
 *
 * @author auto-test-platform
 */
@Data
public class ResultCompareRequest {

    /**
     * 对比类型: task/server/time
     */
    private String compareType;

    /**
     * 结果ID列表
     */
    private List<Long> resultIds;

    /**
     * 任务ID（按任务对比）
     */
    private Long taskId;

    /**
     * 服务器ID列表（按服务器对比）
     */
    private List<Long> serverIds;

    /**
     * 开始时间
     */
    private String startTime;

    /**
     * 结束时间
     */
    private String endTime;

    /**
     * 指标名称列表（指定对比哪些指标）
     */
    private List<String> metricNames;
}
