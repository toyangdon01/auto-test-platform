package com.autotest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.autotest.common.PageResult;
import com.autotest.dto.TestResultDetailResponse;
import com.autotest.entity.Server;
import com.autotest.entity.Task;
import com.autotest.entity.TestResult;
import com.autotest.mapper.ServerMapper;
import com.autotest.mapper.TaskMapper;
import com.autotest.mapper.TestResultMapper;
import com.autotest.service.TestResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 测试结果服务实现
 *
 * @author auto-test-platform
 */
@Service
@RequiredArgsConstructor
public class TestResultServiceImpl implements TestResultService {

    private final TestResultMapper testResultMapper;
    private final TaskMapper taskMapper;
    private final ServerMapper serverMapper;

    @Override
    public PageResult<TestResult> getPage(int page, int pageSize, Long taskId, Long serverId, String result) {
        LambdaQueryWrapper<TestResult> wrapper = new LambdaQueryWrapper<>();

        if (taskId != null) {
            wrapper.eq(TestResult::getTaskId, taskId);
        }
        if (serverId != null) {
            wrapper.eq(TestResult::getServerId, serverId);
        }
        if (result != null && !result.isEmpty()) {
            wrapper.eq(TestResult::getResult, result);
        }

        wrapper.orderByDesc(TestResult::getCreatedAt);

        IPage<TestResult> pageResult = testResultMapper.selectPage(
                new Page<>(page, pageSize), wrapper);

        return PageResult.of(pageResult);
    }

    @Override
    public TestResult getById(Long id) {
        return testResultMapper.selectById(id);
    }

    @Override
    public TestResult create(TestResult testResult) {
        testResult.setCreatedAt(LocalDateTime.now());
        testResultMapper.insert(testResult);
        return testResult;
    }

    @Override
    public TestResult update(Long id, TestResult testResult) {
        testResult.setId(id);
        testResultMapper.updateById(testResult);
        return testResultMapper.selectById(id);
    }

    @Override
    public void delete(Long id) {
        testResultMapper.deleteById(id);
    }

    @Override
    public void deleteBatch(List<Long> ids) {
        testResultMapper.deleteBatchIds(ids);
    }

    @Override
    public List<TestResult> getByTaskId(Long taskId) {
        LambdaQueryWrapper<TestResult> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TestResult::getTaskId, taskId)
                .orderByDesc(TestResult::getCreatedAt);
        return testResultMapper.selectList(wrapper);
    }

    @Override
    public Map<String, Object> getStatistics(Long taskId) {
        LambdaQueryWrapper<TestResult> wrapper = new LambdaQueryWrapper<>();
        if (taskId != null) {
            wrapper.eq(TestResult::getTaskId, taskId);
        }

        List<TestResult> results = testResultMapper.selectList(wrapper);

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", results.size());

        long passCount = results.stream().filter(r -> "pass".equals(r.getResult())).count();
        long failCount = results.stream().filter(r -> "fail".equals(r.getResult())).count();
        long warningCount = results.stream().filter(r -> "warning".equals(r.getResult())).count();
        long errorCount = results.stream().filter(r -> "error".equals(r.getResult())).count();

        stats.put("pass", passCount);
        stats.put("fail", failCount);
        stats.put("warning", warningCount);
        stats.put("error", errorCount);

        double passRate = results.isEmpty() ? 0 : (passCount * 100.0 / results.size());
        stats.put("passRate", Math.round(passRate * 100) / 100.0);

        Double avgScore = results.stream()
                .filter(r -> r.getOverallScore() != null)
                .mapToInt(TestResult::getOverallScore)
                .average()
                .orElse(0);
        stats.put("avgScore", Math.round(avgScore * 100) / 100.0);

        return stats;
    }

    @Override
    public List<Map<String, Object>> getTrend(Long taskId, int days) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<TestResult> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TestResult::getTaskId, taskId)
                .ge(TestResult::getCreatedAt, startDate)
                .orderByAsc(TestResult::getCreatedAt);

        List<TestResult> results = testResultMapper.selectList(wrapper);

        // 按日期分组
        Map<String, List<TestResult>> groupedByDate = new LinkedHashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (TestResult result : results) {
            String dateKey = result.getCreatedAt().format(formatter);
            groupedByDate.computeIfAbsent(dateKey, k -> new ArrayList<>()).add(result);
        }

        // 计算每日统计
        List<Map<String, Object>> trend = new ArrayList<>();
        for (Map.Entry<String, List<TestResult>> entry : groupedByDate.entrySet()) {
            Map<String, Object> dayStats = new HashMap<>();
            dayStats.put("date", entry.getKey());
            dayStats.put("total", entry.getValue().size());

            long passCount = entry.getValue().stream().filter(r -> "pass".equals(r.getResult())).count();
            dayStats.put("pass", passCount);

            double avgScore = entry.getValue().stream()
                    .filter(r -> r.getOverallScore() != null)
                    .mapToInt(TestResult::getOverallScore)
                    .average()
                    .orElse(0);
            dayStats.put("avgScore", Math.round(avgScore * 100) / 100.0);

            trend.add(dayStats);
        }

        return trend;
    }

    @Override
    public TestResultDetailResponse getDetail(Long id) {
        TestResult result = testResultMapper.selectById(id);
        if (result == null) {
            return null;
        }

        TestResultDetailResponse response = new TestResultDetailResponse();

        // 基本信息
        response.setId(result.getId());
        response.setTaskId(result.getTaskId());
        response.setServerId(result.getServerId());
        response.setResult(result.getResult());
        response.setResultReason(result.getResultReason());
        response.setOverallScore(result.getOverallScore());
        response.setMetrics(result.getMetrics());
        response.setRawOutput(result.getRawOutput());
        response.setRawError(result.getRawError());
        response.setOutputFiles(result.getOutputFiles());
        response.setExitCode(result.getExitCode());
        response.setDurationMs(result.getDurationMs());
        response.setStartedAt(result.getStartedAt());
        response.setFinishedAt(result.getFinishedAt());
        response.setCreatedAt(result.getCreatedAt());

        // 获取任务名称
        if (result.getTaskId() != null) {
            Task task = taskMapper.selectById(result.getTaskId());
            if (task != null) {
                response.setTaskName(task.getName());
            }
        }

        // 获取服务器信息
        if (result.getServerId() != null) {
            Server server = serverMapper.selectById(result.getServerId());
            if (server != null) {
                response.setServerName(server.getName());
                response.setServerIp(server.getHost());
            }
        }

        // 解析指标列表
        if (result.getMetrics() != null) {
            List<TestResultDetailResponse.MetricItem> metricList = new ArrayList<>();
            Map<String, Object> metrics = result.getMetrics();

            for (Map.Entry<String, Object> entry : metrics.entrySet()) {
                TestResultDetailResponse.MetricItem item = new TestResultDetailResponse.MetricItem();
                item.setKey(entry.getKey());
                item.setName(formatMetricName(entry.getKey()));
                item.setValue(entry.getValue());

                // 判断状态（简单逻辑：数值型判断是否在合理范围）
                if (entry.getValue() instanceof Number) {
                    double value = ((Number) entry.getValue()).doubleValue();
                    // 简单判断：CPU、内存、磁盘使用率 > 90 为 warning
                    String key = entry.getKey().toLowerCase();
                    if (key.contains("cpu") || key.contains("mem") || key.contains("disk")) {
                        if (key.contains("usage") || key.contains("load") || key.contains("percent")) {
                            item.setStatus(value > 90 ? "warning" : "normal");
                        }
                    }
                }
                item.setStatus(item.getStatus() != null ? item.getStatus() : "normal");

                metricList.add(item);
            }

            response.setMetricList(metricList);
        }

        return response;
    }

    /**
     * 格式化指标名称
     */
    private String formatMetricName(String key) {
        if (key == null) return "";
        // 将下划线或驼峰转换为可读名称
        String name = key.replace("_", " ").replace("-", " ");
        // 首字母大写
        if (name.length() > 0) {
            name = name.substring(0, 1).toUpperCase() + name.substring(1);
        }
        return name;
    }
}
