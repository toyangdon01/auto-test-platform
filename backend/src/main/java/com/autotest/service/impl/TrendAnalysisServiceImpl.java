package com.autotest.service.impl;

import com.autotest.dto.TrendAnalysisResponse;
import com.autotest.entity.Task;
import com.autotest.entity.TestResult;
import com.autotest.mapper.TaskMapper;
import com.autotest.mapper.TestResultMapper;
import com.autotest.service.TrendAnalysisService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 趋势分析服务实现
 *
 * @author auto-test-platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrendAnalysisServiceImpl implements TrendAnalysisService {

    private final TestResultMapper testResultMapper;
    private final TaskMapper taskMapper;

    @Override
    public TrendAnalysisResponse getMetricTrend(Long scriptId, String metricName, Integer days) {
        TrendAnalysisResponse response = new TrendAnalysisResponse();
        response.setMetricName(metricName);

        LocalDateTime startTime = LocalDateTime.now().minusDays(days);
        List<TestResult> results = getResultsByScriptAndTime(scriptId, startTime);

        List<TrendAnalysisResponse.DataPoint> dataPoints = new ArrayList<>();
        List<Double> values = new ArrayList<>();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (TestResult result : results) {
            if (result.getMetrics() != null && result.getMetrics().containsKey(metricName)) {
                Object metricData = result.getMetrics().get(metricName);
                Object value = extractValue(metricData);

                if (value != null) {
                    TrendAnalysisResponse.DataPoint dp = new TrendAnalysisResponse.DataPoint();
                    dp.setDate(result.getStartedAt() != null ? result.getStartedAt().format(formatter) : "");
                    dp.setValue(value);
                    dp.setTaskId(result.getTaskId());

                    Task task = taskMapper.selectById(result.getTaskId());
                    dp.setTaskName(task != null ? task.getName() : "");

                    dataPoints.add(dp);

                    if (value instanceof Number) {
                        values.add(((Number) value).doubleValue());
                    }
                }
            }
        }

        response.setDataPoints(dataPoints);

        // 计算统计信息
        TrendAnalysisResponse.Statistics stats = calculateStatistics(values);
        response.setStatistics(stats);

        // 单位
        if (!results.isEmpty() && results.get(0).getMetrics() != null) {
            Object metricData = results.get(0).getMetrics().get(metricName);
            if (metricData instanceof Map) {
                response.setUnit((String) ((Map<?, ?>) metricData).get("unit"));
            }
        }

        // 异常检测
        response.setAnomalies(detectAnomaliesInData(dataPoints, stats));

        return response;
    }

    @Override
    public Map<String, Object> getTaskExecutionTrend(Long scriptId, Integer days) {
        Map<String, Object> result = new LinkedHashMap<>();

        LocalDateTime startTime = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<>();
        if (scriptId != null) {
            wrapper.eq(Task::getScriptId, scriptId);
        }
        wrapper.ge(Task::getCreatedAt, startTime)
                .orderByAsc(Task::getCreatedAt);

        List<Task> tasks = taskMapper.selectList(wrapper);

        // 按日期分组
        Map<String, Long> dailyCount = tasks.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                        Collectors.counting()
                ));

        // 填充缺失的日期
        List<Map<String, Object>> trendData = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            String dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", dateStr);
            item.put("count", dailyCount.getOrDefault(dateStr, 0L));
            trendData.add(item);
        }

        result.put("trendData", trendData);
        result.put("totalTasks", tasks.size());
        result.put("avgPerDay", tasks.size() / (double) days);

        return result;
    }

    @Override
    public Map<String, Object> getServerPerformanceTrend(Long serverId, Integer days) {
        Map<String, Object> result = new LinkedHashMap<>();

        LocalDateTime startTime = LocalDateTime.now().minusDays(days);

        LambdaQueryWrapper<TestResult> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TestResult::getServerId, serverId)
                .ge(TestResult::getStartedAt, startTime)
                .orderByAsc(TestResult::getStartedAt);

        List<TestResult> results = testResultMapper.selectList(wrapper);

        // 按日期统计
        Map<String, List<TestResult>> dailyResults = results.stream()
                .filter(r -> r.getStartedAt() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getStartedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                ));

        List<Map<String, Object>> trendData = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            String dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            List<TestResult> dayResults = dailyResults.get(dateStr);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", dateStr);

            if (dayResults != null && !dayResults.isEmpty()) {
                int passCount = (int) dayResults.stream().filter(r -> "pass".equals(r.getResult())).count();
                int totalCount = dayResults.size();
                double avgScore = dayResults.stream()
                        .filter(r -> r.getOverallScore() != null)
                        .mapToInt(TestResult::getOverallScore)
                        .average().orElse(0);
                double avgDuration = dayResults.stream()
                        .filter(r -> r.getDurationMs() != null)
                        .mapToInt(TestResult::getDurationMs)
                        .average().orElse(0);

                item.put("totalTests", totalCount);
                item.put("passRate", (double) passCount / totalCount * 100);
                item.put("avgScore", avgScore);
                item.put("avgDuration", avgDuration);
            } else {
                item.put("totalTests", 0);
                item.put("passRate", 0);
                item.put("avgScore", 0);
                item.put("avgDuration", 0);
            }

            trendData.add(item);
        }

        result.put("trendData", trendData);
        result.put("totalResults", results.size());

        return result;
    }

    @Override
    public Map<String, Object> getOverallTrend(Integer days) {
        Map<String, Object> result = new LinkedHashMap<>();

        LocalDateTime startTime = LocalDateTime.now().minusDays(days);

        LambdaQueryWrapper<TestResult> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(TestResult::getStartedAt, startTime)
                .orderByAsc(TestResult::getStartedAt);

        List<TestResult> results = testResultMapper.selectList(wrapper);

        // 按日期统计
        Map<String, List<TestResult>> dailyResults = results.stream()
                .filter(r -> r.getStartedAt() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getStartedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                ));

        List<Map<String, Object>> trendData = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            String dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            List<TestResult> dayResults = dailyResults.get(dateStr);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", dateStr);

            if (dayResults != null && !dayResults.isEmpty()) {
                int passCount = (int) dayResults.stream().filter(r -> "pass".equals(r.getResult())).count();
                int failCount = dayResults.size() - passCount;

                item.put("totalTests", dayResults.size());
                item.put("passCount", passCount);
                item.put("failCount", failCount);
                item.put("passRate", (double) passCount / dayResults.size() * 100);
            } else {
                item.put("totalTests", 0);
                item.put("passCount", 0);
                item.put("failCount", 0);
                item.put("passRate", 0);
            }

            trendData.add(item);
        }

        result.put("trendData", trendData);
        result.put("totalResults", results.size());

        // 计算整体通过率
        long totalPass = results.stream().filter(r -> "pass".equals(r.getResult())).count();
        result.put("overallPassRate", results.isEmpty() ? 0 : (double) totalPass / results.size() * 100);

        return result;
    }

    @Override
    public Map<String, Object> predictMetricTrend(Long scriptId, String metricName, Integer futureDays) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 获取历史数据
        TrendAnalysisResponse historical = getMetricTrend(scriptId, metricName, 30);

        if (historical.getDataPoints() == null || historical.getDataPoints().size() < 3) {
            result.put("success", false);
            result.put("message", "历史数据不足，无法预测");
            return result;
        }

        // 简单线性回归预测
        List<TrendAnalysisResponse.DataPoint> points = historical.getDataPoints();
        List<Double> values = points.stream()
                .filter(p -> p.getValue() instanceof Number)
                .map(p -> ((Number) p.getValue()).doubleValue())
                .collect(Collectors.toList());

        if (values.size() < 3) {
            result.put("success", false);
            result.put("message", "有效数据不足");
            return result;
        }

        // 计算线性回归参数
        int n = values.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;

        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += values.get(i);
            sumXY += i * values.get(i);
            sumX2 += i * i;
        }

        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        double intercept = (sumY - slope * sumX) / n;

        // 预测未来数据
        List<Map<String, Object>> predictions = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int i = 0; i < futureDays; i++) {
            LocalDate date = today.plusDays(i + 1);
            double predictedValue = slope * (n + i) + intercept;

            Map<String, Object> pred = new LinkedHashMap<>();
            pred.put("date", date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            pred.put("predictedValue", Math.round(predictedValue * 100.0) / 100.0);
            predictions.add(pred);
        }

        result.put("success", true);
        result.put("predictions", predictions);
        result.put("trend", slope > 0 ? "上升" : (slope < 0 ? "下降" : "稳定"));
        result.put("dailyChangeRate", Math.round(slope * 100.0) / 100.0);

        return result;
    }

    @Override
    public List<Map<String, Object>> detectAnomalies(Long scriptId, Integer days) {
        List<Map<String, Object>> allAnomalies = new ArrayList<>();

        // 获取结果
        LocalDateTime startTime = LocalDateTime.now().minusDays(days);
        List<TestResult> results = getResultsByScriptAndTime(scriptId, startTime);

        if (results.isEmpty()) {
            return allAnomalies;
        }

        // 收集所有指标
        Set<String> metricNames = new HashSet<>();
        for (TestResult r : results) {
            if (r.getMetrics() != null) {
                metricNames.addAll(r.getMetrics().keySet());
            }
        }

        // 对每个指标进行异常检测
        for (String metricName : metricNames) {
            TrendAnalysisResponse trend = getMetricTrend(scriptId, metricName, days);
            if (trend.getAnomalies() != null) {
                for (TrendAnalysisResponse.Anomaly anomaly : trend.getAnomalies()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("metricName", metricName);
                    item.put("date", anomaly.getDate());
                    item.put("value", anomaly.getValue());
                    item.put("type", anomaly.getType());
                    item.put("severity", anomaly.getSeverity());
                    item.put("description", anomaly.getDescription());
                    allAnomalies.add(item);
                }
            }
        }

        // 按严重程度排序
        allAnomalies.sort((a, b) -> {
            int severityOrder = Map.of("high", 0, "medium", 1, "low", 2)
                    .getOrDefault(a.get("severity"), 3);
            int otherOrder = Map.of("high", 0, "medium", 1, "low", 2)
                    .getOrDefault(b.get("severity"), 3);
            return severityOrder - otherOrder;
        });

        return allAnomalies;
    }

    /**
     * 根据脚本和时间获取结果
     */
    private List<TestResult> getResultsByScriptAndTime(Long scriptId, LocalDateTime startTime) {
        LambdaQueryWrapper<TestResult> wrapper = new LambdaQueryWrapper<>();

        if (scriptId != null) {
            // 通过任务关联脚本
            List<Task> tasks = taskMapper.selectList(
                    new LambdaQueryWrapper<Task>().eq(Task::getScriptId, scriptId)
            );
            if (tasks.isEmpty()) {
                return Collections.emptyList();
            }
            List<Long> taskIds = tasks.stream().map(Task::getId).collect(Collectors.toList());
            wrapper.in(TestResult::getTaskId, taskIds);
        }

        wrapper.ge(TestResult::getStartedAt, startTime)
                .orderByAsc(TestResult::getStartedAt);

        return testResultMapper.selectList(wrapper);
    }

    /**
     * 提取值
     */
    private Object extractValue(Object metricData) {
        if (metricData == null) return null;
        if (metricData instanceof Map) {
            Object value = ((Map<?, ?>) metricData).get("value");
            return value;
        }
        return metricData;
    }

    /**
     * 计算统计信息
     */
    private TrendAnalysisResponse.Statistics calculateStatistics(List<Double> values) {
        TrendAnalysisResponse.Statistics stats = new TrendAnalysisResponse.Statistics();

        if (values.isEmpty()) {
            return stats;
        }

        if (!values.isEmpty()) {
            stats.setMinValue(Collections.min(values));
            stats.setMaxValue(Collections.max(values));
            stats.setAvgValue(values.stream().mapToDouble(Double::doubleValue).average().orElse(0));
            stats.setLatestValue(values.get(values.size() - 1));

            // 变化率
            if (values.size() >= 2) {
                double last = values.get(values.size() - 1);
                double prev = values.get(values.size() - 2);
                if (prev != 0) {
                    stats.setChangeRate((last - prev) / prev * 100);
                }
            }

            // 趋势判断
            if (stats.getChangeRate() != null) {
                if (stats.getChangeRate() > 5) {
                    stats.setTrend("up");
                } else if (stats.getChangeRate() < -5) {
                    stats.setTrend("down");
                } else {
                    stats.setTrend("stable");
                }
            }

            // 波动率（标准差/均值）
            double avg = stats.getAvgValue().doubleValue();
            double variance = values.stream()
                    .mapToDouble(v -> Math.pow(v - avg, 2))
                    .average().orElse(0);
            stats.setVolatility(Math.sqrt(variance));
        }

        return stats;
    }

    /**
     * 检测数据中的异常
     */
    private List<TrendAnalysisResponse.Anomaly> detectAnomaliesInData(
            List<TrendAnalysisResponse.DataPoint> dataPoints,
            TrendAnalysisResponse.Statistics stats) {

        List<TrendAnalysisResponse.Anomaly> anomalies = new ArrayList<>();

        if (dataPoints.size() < 3 || stats.getAvgValue() == null) {
            return anomalies;
        }

        double avg = stats.getAvgValue().doubleValue();
        double stdDev = stats.getVolatility() != null ? stats.getVolatility() : 0;

        for (TrendAnalysisResponse.DataPoint dp : dataPoints) {
            if (dp.getValue() instanceof Number) {
                double value = ((Number) dp.getValue()).doubleValue();

                // 超过2个标准差视为异常
                if (stdDev > 0 && Math.abs(value - avg) > 2 * stdDev) {
                    TrendAnalysisResponse.Anomaly anomaly = new TrendAnalysisResponse.Anomaly();
                    anomaly.setDate(dp.getDate());
                    anomaly.setValue(value);

                    if (value > avg) {
                        anomaly.setType("spike");
                        anomaly.setDescription("指标值异常升高，超出正常范围");
                    } else {
                        anomaly.setType("drop");
                        anomaly.setDescription("指标值异常下降，超出正常范围");
                    }

                    // 严重程度
                    double deviation = Math.abs(value - avg) / stdDev;
                    if (deviation > 3) {
                        anomaly.setSeverity("high");
                    } else if (deviation > 2.5) {
                        anomaly.setSeverity("medium");
                    } else {
                        anomaly.setSeverity("low");
                    }

                    anomalies.add(anomaly);
                }
            }
        }

        return anomalies;
    }
}
