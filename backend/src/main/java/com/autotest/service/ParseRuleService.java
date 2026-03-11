package com.autotest.service;

import com.autotest.dto.ParseRuleRequest;
import com.autotest.entity.Script;

import java.util.Map;

/**
 * 输出解析服务接口
 *
 * @author auto-test-platform
 */
public interface ParseRuleService {

    /**
     * 保存解析规则
     */
    void saveParseRules(Long scriptId, ParseRuleRequest request);

    /**
     * 获取解析规则
     */
    Map<String, Object> getParseRules(Long scriptId);

    /**
     * 测试解析规则
     */
    Map<String, Object> testParseRule(Long scriptId, String sampleOutput);

    /**
     * 解析输出
     */
    Map<String, Object> parseOutput(Long scriptId, String output);

    /**
     * 判断结果
     */
    String judgeResult(Long scriptId, String output, Integer exitCode);
}
