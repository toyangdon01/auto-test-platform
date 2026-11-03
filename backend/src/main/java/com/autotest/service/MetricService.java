package com.autotest.service;

import com.autotest.entity.Metric;
import com.autotest.mapper.MetricMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 指标数据服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricService {

    private final MetricMapper metricMapper;

    public List<Metric> list(Wrapper<Metric> wrapper) {
        return metricMapper.selectList(wrapper);
    }

    /**
     * 批量保存指标
     */
    public void saveBatch(List<Metric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return;
        }

        try {
            // 逐个插入（避免 batch insert 的 typeHandler 问题）
            for (Metric metric : metrics) {
                metricMapper.insert(metric);
            }
            log.debug("保存 {} 条指标数据", metrics.size());
        } catch (Exception e) {
            log.error("批量保存指标失败", e);
            // 不抛出异常，避免影响任务执行
        }
    }
}
