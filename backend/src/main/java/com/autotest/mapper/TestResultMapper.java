package com.autotest.mapper;

import com.autotest.entity.TestResult;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 测试结果 Mapper
 *
 * @author auto-test-platform
 */
@Mapper
public interface TestResultMapper extends BaseMapper<TestResult> {
}
