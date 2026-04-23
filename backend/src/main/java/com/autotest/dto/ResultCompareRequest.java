package com.autotest.dto;

import lombok.Data;
import java.util.List;

import jakarta.validation.constraints.Size;

/**
 * 结果对比请求
 */
@Data
public class ResultCompareRequest {
    
    /**
     * 对比类型：task / results / servers
     */
    private String compareType;
    
    /**
     * 要对比的结果ID列表
     */
    @Size(min = 2, message = "至少需要选择2个结果进行对比")
    private List<Long> resultIds;
    
    /**
     * 任务ID（按任务对比时使用）
     */
    private Long taskId;
    
    /**
     * 服务器ID列表（按服务器对比时使用）
     */
    private List<Long> serverIds;
    
    /**
     * 要对比的指标名称列表（可选，不指定则对比所有）
     */
    private List<String> metricNames;
    
    /**
     * 开始时间（ISO格式）
     */
    private String startTime;
    
    /**
     * 结束时间（ISO格式）
     */
    private String endTime;
}
