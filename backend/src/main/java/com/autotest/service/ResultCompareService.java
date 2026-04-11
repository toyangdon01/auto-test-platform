package com.autotest.service;

import com.autotest.dto.ResultCompareRequest;
import com.autotest.dto.ResultCompareResponse;

import java.util.List;
import java.util.Map;

/**
 * 结果对比服务接口
 *
 * @author auto-test-platform
 */
public interface ResultCompareService {

    /**
     * 对比测试结果
     */
    ResultCompareResponse compareResults(ResultCompareRequest request);

    /**
     * 获取任务的趋势数据
     */
    Map<String, Object> getTrendData(Long scriptId, Long serverId, String metricName, Integer days);

    /**
     * 获取可对比的结果列表
     */
    List<Map<String, Object>> getComparableResults(Long taskId);
}
