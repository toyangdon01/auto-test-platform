package com.autotest.service.impl;

import com.autotest.dto.ResultCompareRequest;
import com.autotest.dto.ResultCompareResponse;
import com.autotest.entity.Server;
import com.autotest.entity.Task;
import com.autotest.entity.TestResult;
import com.autotest.mapper.ServerMapper;
import com.autotest.mapper.TaskMapper;
import com.autotest.mapper.TestResultMapper;
import com.autotest.service.ResultCompareService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 结果对比服务实现
 *
 * @author auto-test-platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResultCompareServiceImpl implements ResultCompareService {

    private final TestResultMapper testResultMapper;
    private final TaskMapper taskMapper;
    private final ServerMapper serverMapper;

    @Override
    public ResultCompareResponse compareResults(ResultCompareRequest request) {
        ResultCompareResponse response = new ResultCompareResponse();
        response.setCompareType(request.getCompareType());

        // 获取要对比的结果
        List<TestResult> results = getResultsForCompare(request);
        if (results.isEmpty()) {
            return response;
        }

        // 构建结果项列表
        List<ResultCompareResponse.ResultItem> resultItems = results.stream()
                .map(this::buildResultItem)
                .collect(Collectors.toList());
        response.setResults(resultItems);

        // 指标对比
        List<ResultCompareResponse.MetricCompare> metricCompares = compareMetrics(results, request.getMetricNames());
        response.setMetrics(metricCompares);

        // 差异分析
        List<ResultCompareResponse.DiffItem> differences = analyzeDifferences(results);
        response.setDifferences(differences);

        // 统计信息
        response.setStatistics(calculateStatistics(results));

        return response;
    }

    @Override
    public Map<String, Object> getTrendData(Long scriptId, Long serverId, String metricName, Integer days) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 构建查询条件
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(days != null ? days : 7);

        LambdaQueryWrapper<TestResult> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(TestResult::getCreatedAt, startTime)
                .le(TestResult::getCreatedAt, endTime)
                .orderByAsc(TestResult::getCreatedAt);

        // 如果指定了脚本ID，需要通过任务关联
        if (scriptId != null) {
            List<Long> taskIds = taskMapper.selectList(
                    new LambdaQueryWrapper<Task>().eq(Task::getScriptId, scriptId)
            ).stream().map(Task::getId).collect(Collectors.toList());

            if (taskIds.isEmpty()) {
                return result;
            }
            wrapper.in(TestResult::getTaskId, taskIds);
        }

        if (serverId != null) {
            wrapper.eq(TestResult::getServerId, serverId);
        }

        List<TestResult> results = testResultMapper.selectList(wrapper);

        // 提取趋势数据
        List<Map<String, Object>> dataPoints = new ArrayList<>();
        for (TestResult r : results) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("time", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
            point.put("resultId", r.getId());
            point.put("taskId", r.getTaskId());
            point.put("serverId", r.getServerId());
            point.put("result", r.getResult());
            point.put("score", r.getOverallScore());

            if (metricName != null && r.getMetrics() != null) {
                Object metricValue = extractMetricValue(r.getMetrics(), metricName);
                point.put("value", metricValue);
            }

            dataPoints.add(point);
        }

        result.put("dataPoints", dataPoints);
        result.put("total", results.size());

        // 计算统计
        if (!dataPoints.isEmpty()) {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("passCount", results.stream().filter(r -> "pass".equals(r.getResult())).count());
            stats.put("failCount", results.stream().filter(r -> "fail".equals(r.getResult())).count());
            stats.put("avgScore", results.stream()
                    .filter(r -> r.getOverallScore() != null)
                    .mapToInt(TestResult::getOverallScore)
                    .average().orElse(0));
            result.put("statistics", stats);
        }

        return result;
    }

    @Override
    public List<Map<String, Object>> getComparableResults(Long taskId) {
        List<Map<String, Object>> result = new ArrayList<>();

        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            return result;
        }

        // 获取同脚本的其他任务结果
        List<Task> sameScriptTasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getScriptId, task.getScriptId())
                        .ne(Task::getId, taskId)
                        .orderByDesc(Task::getCreatedAt)
                        .last("LIMIT 20")
        );

        for (Task t : sameScriptTasks) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("taskId", t.getId());
            item.put("taskName", t.getName());
            item.put("scriptVersion", t.getScriptVersion());
            item.put("status", t.getStatus());
            item.put("createdAt", t.getCreatedAt());
            result.add(item);
        }

        return result;
    }

    /**
     * 获取要对比的结果列表
     */
    private List<TestResult> getResultsForCompare(ResultCompareRequest request) {
        if (request.getResultIds() != null && !request.getResultIds().isEmpty()) {
            return testResultMapper.selectBatchIds(request.getResultIds());
        }

        if (request.getTaskId() != null) {
            return testResultMapper.selectList(
                    new LambdaQueryWrapper<TestResult>()
                            .eq(TestResult::getTaskId, request.getTaskId())
                            .orderByAsc(TestResult::getServerId)
            );
        }

        if (request.getServerIds() != null && !request.getServerIds().isEmpty()) {
            LambdaQueryWrapper<TestResult> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(TestResult::getServerId, request.getServerIds());

            if (request.getStartTime() != null) {
                wrapper.ge(TestResult::getCreatedAt, LocalDateTime.parse(request.getStartTime()));
            }
            if (request.getEndTime() != null) {
                wrapper.le(TestResult::getCreatedAt, LocalDateTime.parse(request.getEndTime()));
            }

            wrapper.orderByDesc(TestResult::getCreatedAt).last("LIMIT 50");
            return testResultMapper.selectList(wrapper);
        }

        return new ArrayList<>();
    }

    /**
     * 构建结果项
     */
    private ResultCompareResponse.ResultItem buildResultItem(TestResult result) {
        ResultCompareResponse.ResultItem item = new ResultCompareResponse.ResultItem();
        item.setResultId(result.getId());
        item.setTaskId(result.getTaskId());
        item.setServerId(result.getServerId());
        item.setResult(result.getResult());
        item.setOverallScore(result.getOverallScore());
        item.setDurationMs(result.getDurationMs());
        item.setExecutedAt(result.getCreatedAt() != null ? result.getCreatedAt().toString() : null);

        // 获取任务名称
        Task task = taskMapper.selectById(result.getTaskId());
        if (task != null) {
            item.setTaskName(task.getName());
        }

        // 获取服务器名称
        Server server = serverMapper.selectById(result.getServerId());
        if (server != null) {
            item.setServerName(server.getName());
        }

        return item;
    }

    /**
     * 对比指标
     */
    private List<ResultCompareResponse.MetricCompare> compareMetrics(List<TestResult> results, List<String> metricNames) {
        List<ResultCompareResponse.MetricCompare> compares = new ArrayList<>();

        // 收集所有指标名称
        Set<String> allMetrics = new LinkedHashSet<>();
        for (TestResult r : results) {
            if (r.getMetrics() != null) {
                allMetrics.addAll(extractMetricNames(r.getMetrics()));
            }
        }

        // 如果指定了指标名称，只对比指定的
        if (metricNames != null && !metricNames.isEmpty()) {
            allMetrics.retainAll(metricNames);
        }

        // 对每个指标进行对比
        for (String metricName : allMetrics) {
            ResultCompareResponse.MetricCompare compare = new ResultCompareResponse.MetricCompare();
            compare.setMetricName(metricName);

            List<ResultCompareResponse.MetricValue> values = new ArrayList<>();
            List<Double> numericValues = new ArrayList<>();

            for (TestResult r : results) {
                Object value = r.getMetrics() != null ? extractMetricValue(r.getMetrics(), metricName) : null;

                ResultCompareResponse.MetricValue mv = new ResultCompareResponse.MetricValue();
                mv.setResultId(r.getId());
                mv.setValue(value);
                mv.setDisplayValue(formatValue(value));
                values.add(mv);

                if (value instanceof Number) {
                    numericValues.add(((Number) value).doubleValue());
                }
            }

            compare.setValues(values);

            // 计算变化率（如果有多个结果）
            if (numericValues.size() >= 2) {
                Double first = numericValues.get(0);
                Double last = numericValues.get(numericValues.size() - 1);
                if (first != null && last != null && first != 0) {
                    double changeRate = ((last - first) / first) * 100;
                    compare.setChangeRate(changeRate);
                    compare.setTrend(changeRate > 5 ? "up" : (changeRate < -5 ? "down" : "stable"));
                }
            }

            compares.add(compare);
        }

        return compares;
    }

    /**
     * 分析差异
     */
    private List<ResultCompareResponse.DiffItem> analyzeDifferences(List<TestResult> results) {
        List<ResultCompareResponse.DiffItem> differences = new ArrayList<>();

        if (results.size() < 2) {
            return differences;
        }

        TestResult base = results.get(0);

        // 结果状态差异
        ResultCompareResponse.DiffItem resultDiff = new ResultCompareResponse.DiffItem();
        resultDiff.setCategory("result");
        resultDiff.setName("测试结果");
        List<ResultCompareResponse.ValueChange> resultChanges = new ArrayList<>();

        for (int i = 1; i < results.size(); i++) {
            TestResult r = results.get(i);
            if (!Objects.equals(base.getResult(), r.getResult())) {
                ResultCompareResponse.ValueChange change = new ResultCompareResponse.ValueChange();
                change.setResultId(r.getId());
                change.setOldValue(base.getResult());
                change.setNewValue(r.getResult());
                resultChanges.add(change);
            }
        }

        if (!resultChanges.isEmpty()) {
            resultDiff.setChanges(resultChanges);
            differences.add(resultDiff);
        }

        // 分数差异
        ResultCompareResponse.DiffItem scoreDiff = new ResultCompareResponse.DiffItem();
        scoreDiff.setCategory("score");
        scoreDiff.setName("测试分数");
        List<ResultCompareResponse.ValueChange> scoreChanges = new ArrayList<>();

        for (int i = 1; i < results.size(); i++) {
            TestResult r = results.get(i);
            if (!Objects.equals(base.getOverallScore(), r.getOverallScore())) {
                ResultCompareResponse.ValueChange change = new ResultCompareResponse.ValueChange();
                change.setResultId(r.getId());
                change.setOldValue(base.getOverallScore());
                change.setNewValue(r.getOverallScore());

                if (base.getOverallScore() != null && base.getOverallScore() > 0 && r.getOverallScore() != null) {
                    change.setChangePercent(((double) (r.getOverallScore() - base.getOverallScore()) / base.getOverallScore()) * 100);
                }

                scoreChanges.add(change);
            }
        }

        if (!scoreChanges.isEmpty()) {
            scoreDiff.setChanges(scoreChanges);
            differences.add(scoreDiff);
        }

        return differences;
    }

    /**
     * 计算统计信息
     */
    private ResultCompareResponse.Statistics calculateStatistics(List<TestResult> results) {
        ResultCompareResponse.Statistics stats = new ResultCompareResponse.Statistics();
        stats.setTotalResults(results.size());
        stats.setPassCount((int) results.stream().filter(r -> "pass".equals(r.getResult())).count());
        stats.setFailCount((int) results.stream().filter(r -> "fail".equals(r.getResult())).count());

        stats.setAvgScore(results.stream()
                .filter(r -> r.getOverallScore() != null)
                .mapToInt(TestResult::getOverallScore)
                .average().orElse(0));

        stats.setAvgDuration(results.stream()
                .filter(r -> r.getDurationMs() != null)
                .mapToInt(TestResult::getDurationMs)
                .average().orElse(0));

        return stats;
    }

    /**
     * 提取指标名称列表
     */
    private Set<String> extractMetricNames(Map<String, Object> metrics) {
        Set<String> names = new LinkedHashSet<>();
        extractNamesRecursive(metrics, "", names);
        return names;
    }

    private void extractNamesRecursive(Object obj, String prefix, Set<String> names) {
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = prefix.isEmpty() ? String.valueOf(entry.getKey()) : prefix + "." + entry.getKey();

                if (entry.getValue() instanceof Map) {
                    // 检查是否是指标值对象（包含 value 字段）
                    Map<?, ?> valueMap = (Map<?, ?>) entry.getValue();
                    if (valueMap.containsKey("value")) {
                        names.add(key);
                    } else {
                        extractNamesRecursive(entry.getValue(), key, names);
                    }
                } else if (!(entry.getValue() instanceof List)) {
                    names.add(key);
                }
            }
        }
    }

    /**
     * 提取指标值
     */
    private Object extractMetricValue(Map<String, Object> metrics, String metricName) {
        String[] parts = metricName.split("\\.");
        Object current = metrics;

        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
        }

        // 如果是指标值对象，提取 value 字段
        if (current instanceof Map) {
            Map<?, ?> valueMap = (Map<?, ?>) current;
            if (valueMap.containsKey("value")) {
                return valueMap.get("value");
            }
        }

        return current;
    }

    /**
     * 格式化值
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "-";
        }
        if (value instanceof Double) {
            return String.format("%.2f", value);
        }
        return String.valueOf(value);
    }
}
