package com.autotest.service;

import com.autotest.common.PageResult;
import com.autotest.entity.ResultRule;

import java.util.List;
import java.util.Map;

/**
 * 解析规则服务接口
 */
public interface ResultRuleService {

    /**
     * 获取脚本的所有解析规则
     */
    List<ResultRule> listByScriptId(Long scriptId);

    /**
     * 获取所有启用的全局规则
     */
    List<ResultRule> listGlobalRules();

    /**
     * 获取解析规则详情
     */
    ResultRule getById(Long id);

    /**
     * 创建解析规则
     */
    ResultRule create(ResultRule rule);

    /**
     * 更新解析规则
     */
    ResultRule update(Long id, ResultRule rule);

    /**
     * 删除解析规则
     */
    void delete(Long id);

    /**
     * 启用/禁用规则
     */
    void setEnabled(Long id, boolean enabled);

    /**
     * 分页查询规则
     */
    PageResult<ResultRule> getPage(Integer page, Integer size, Long scriptId, Boolean enabled);

    /**
     * 测试规则
     */
    Map<String, Object> testRule(Long id, String sampleOutput, Integer exitCode);

    /**
     * 应用规则判定结果
     */
    String applyRules(Long scriptId, String output, Integer exitCode);
}
