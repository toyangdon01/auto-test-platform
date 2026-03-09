package com.autotest.mapper;

import com.autotest.entity.Metric;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 指标数据 Mapper
 *
 * @author auto-test-platform
 */
@Mapper
public interface MetricMapper extends BaseMapper<Metric> {
}
