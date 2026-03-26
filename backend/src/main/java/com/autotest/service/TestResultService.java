package com.autotest.service;

import com.autotest.common.PageResult;
import com.autotest.dto.TestResultDetailResponse;
import com.autotest.entity.TestResult;

import java.util.List;
import java.util.Map;

/**
 * 测试结果服务接口
 *
 * @author auto-test-platform
 */
public interface TestResultService {

    /**
     * 分页查询测试结果
     *
     * @param page     页码
     * @param pageSize 每页数量
     * @param taskId   任务ID（可选）
     * @param serverId 服务器ID（可选）
     * @param result   结果状态（可选）
     * @param scriptId 脚本ID（可选）
     * @return 分页结果
     */
    PageResult<TestResult> getPage(int page, int pageSize, Long taskId, Long serverId, String result, Long scriptId);

    /**
     * 获取测试结果详情
     *
     * @param id 结果ID
     * @return 测试结果
     */
    TestResult getById(Long id);

    /**
     * 获取测试结果详情（包含关联信息）
     *
     * @param id 结果ID
     * @return 详情响应
     */
    TestResultDetailResponse getDetail(Long id);

    /**
     * 创建测试结果
     *
     * @param testResult 测试结果
     * @return 创建后的结果
     */
    TestResult create(TestResult testResult);

    /**
     * 更新测试结果
     *
     * @param id         结果ID
     * @param testResult 更新内容
     * @return 更新后的结果
     */
    TestResult update(Long id, TestResult testResult);

    /**
     * 删除测试结果
     *
     * @param id 结果ID
     */
    void delete(Long id);

    /**
     * 批量删除测试结果
     *
     * @param ids 结果ID列表
     */
    void deleteBatch(List<Long> ids);

    /**
     * 获取任务的测试结果列表
     *
     * @param taskId 任务ID
     * @return 测试结果列表
     */
    List<TestResult> getByTaskId(Long taskId);

    /**
     * 获取结果统计数据
     *
     * @param taskId 任务ID（可选）
     * @return 统计数据
     */
    Map<String, Object> getStatistics(Long taskId);

    /**
     * 获取趋势数据
     *
     * @param taskId 任务ID
     * @param days   天数
     * @return 趋势数据
     */
    List<Map<String, Object>> getTrend(Long taskId, int days);
}
