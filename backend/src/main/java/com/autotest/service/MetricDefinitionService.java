package com.autotest.service;

import com.autotest.common.PageResult;
import com.autotest.entity.MetricDefinition;

import java.util.List;
import java.util.Map;

/**
 * 指标定义服务接口
 *
 * @author auto-test-platform
 */
public interface MetricDefinitionService {

    /**
     * 分页查询指标定义
     */
    PageResult<MetricDefinition> getPage(Integer page, Integer size, String category, Boolean enabled);

    /**
     * 获取所有启用的指标定义
     */
    List<MetricDefinition> getAllEnabled();

    /**
     * 获取指标定义详情
     */
    MetricDefinition getById(Long id);

    /**
     * 创建指标定义
     */
    MetricDefinition create(MetricDefinition definition);

    /**
     * 更新指标定义
     */
    MetricDefinition update(Long id, MetricDefinition definition);

    /**
     * 删除指标定义
     */
    void delete(Long id);

    /**
     * 根据脚本输出提取指标值
     */
    Map<String, Object> extractMetrics(Long scriptId, String output);
}
