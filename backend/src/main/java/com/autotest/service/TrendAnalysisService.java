package com.autotest.service;

import com.autotest.dto.TrendAnalysisResponse;

import java.util.List;
import java.util.Map;

/**
 * 趋势分析服务接口
 *
 * @author auto-test-platform
 */
public interface TrendAnalysisService {

    /**
     * 获取指标趋势数据
     */
    TrendAnalysisResponse getMetricTrend(Long scriptId, String metricName, Integer days);

    /**
     * 获取任务执行趋势
     */
    Map<String, Object> getTaskExecutionTrend(Long scriptId, Integer days);

    /**
     * 获取服务器性能趋势
     */
    Map<String, Object> getServerPerformanceTrend(Long serverId, Integer days);

    /**
     * 获取整体趋势概览
     */
    Map<String, Object> getOverallTrend(Integer days);

    /**
     * 预测指标趋势
     */
    Map<String, Object> predictMetricTrend(Long scriptId, String metricName, Integer futureDays);

    /**
     * 获取异常检测报告
     */
    List<Map<String, Object>> detectAnomalies(Long scriptId, Integer days);
}
