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
 * 缁撴灉瀵规瘮鏈嶅姟瀹炵幇
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

        // 鑾峰彇瑕佸姣旂殑缁撴灉
        List<TestResult> results = getResultsForCompare(request);
        if (results.isEmpty()) {
            return response;
        }

        // 鎵归噺鏌ヨ task 鍜?server 淇℃伅锛岄伩鍏?N+1
        Map<Long, Task> taskMap = new HashMap<>();
        Map<Long, Server> serverMap = new HashMap<>();
        Set<Long> taskIds = results.stream().map(TestResult::getTaskId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> serverIds = results.stream().map(TestResult::getServerId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!taskIds.isEmpty()) {
            taskMapper.selectBatchIds(taskIds).forEach(t -> taskMap.put(t.getId(), t));
        }
        if (!serverIds.isEmpty()) {
            serverMapper.selectBatchIds(serverIds).forEach(s -> serverMap.put(s.getId(), s));
        }

        // 鏋勫缓缁撴灉椤瑰垪琛紙浣跨敤鎵归噺鏌ヨ鐨?Map锛?
        List<ResultCompareResponse.ResultItem> resultItems = results.stream()
                .map(r -> buildResultItem(r, taskMap, serverMap))
                .collect(Collectors.toList());
        response.setResults(resultItems);

        // 鎸囨爣瀵规瘮
        List<ResultCompareResponse.MetricCompare> metricCompares = compareMetrics(results, request.getMetricNames());
        response.setMetrics(metricCompares);

        // 宸紓鍒嗘瀽
        List<ResultCompareResponse.DiffItem> differences = analyzeDifferences(results);
        response.setDifferences(differences);

        // 缁熻淇℃伅
        response.setStatistics(calculateStatistics(results));

        return response;
    }

    @Override
    public Map<String, Object> getTrendData(Long scriptId, Long serverId, String metricName, Integer days) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 鏋勫缓鏌ヨ鏉′欢
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(days != null ? days : 7);

        LambdaQueryWrapper<TestResult> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(TestResult::getCreatedAt, startTime)
                .le(TestResult::getCreatedAt, endTime)
                .orderByAsc(TestResult::getCreatedAt);

        // 濡傛灉鎸囧畾浜嗚剼鏈琁D锛岄渶瑕侀€氳繃浠诲姟鍏宠仈
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

        // 鎻愬彇瓒嬪娍鏁版嵁
        List<Map<String, Object>> dataPoints = new ArrayList<>();
        for (TestResult r : results) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("time", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
            point.put("resultId", r.getId());
            point.put("taskId", r.getTaskId());
            point.put("serverId", r.getServerId());
            point.put("result", r.getResult());
            point.put("score", r.getOverallScore());

            if (metricName != null && r.getParsedData() != null) {
                Object metricValue = extractMetricValue(r.getParsedData(), metricName);
                point.put("value", metricValue);
            }

            dataPoints.add(point);
        }

        result.put("dataPoints", dataPoints);
        result.put("total", results.size());

        // 璁＄畻缁熻
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

        // 鑾峰彇鍚岃剼鏈殑鍏朵粬浠诲姟缁撴灉
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
     * 鑾峰彇瑕佸姣旂殑缁撴灉鍒楄〃
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
     * 鏋勫缓缁撴灉椤?
     * @param result 娴嬭瘯缁撴灉
     * @param taskMap 浠诲姟Map锛堟壒閲忔煡璇紝閬垮厤N+1锛?
     * @param serverMap 鏈嶅姟鍣∕ap锛堟壒閲忔煡璇紝閬垮厤N+1锛?
     */
    private ResultCompareResponse.ResultItem buildResultItem(TestResult result, Map<Long, Task> taskMap, Map<Long, Server> serverMap) {
        ResultCompareResponse.ResultItem item = new ResultCompareResponse.ResultItem();
        item.setId(result.getId());
        item.setTaskId(result.getTaskId());
        item.setServerId(result.getServerId());
        item.setResult(result.getResult());
        item.setOverallScore(result.getOverallScore());
        item.setDurationMs(result.getDurationMs());
        item.setExecutedAt(result.getCreatedAt() != null ? result.getCreatedAt().toString() : null);

        // 浠嶮ap涓幏鍙栵紝閬垮厤N+1鏌ヨ
        Task task = taskMap.get(result.getTaskId());
        if (task != null) {
            item.setTaskName(task.getName());
        }

        Server server = serverMap.get(result.getServerId());
        if (server != null) {
            item.setServerName(server.getName());
        }

        return item;
    }

    /**
     * 瀵规瘮鎸囨爣
     */
    private List<ResultCompareResponse.MetricCompare> compareMetrics(List<TestResult> results, List<String> metricNames) {
        List<ResultCompareResponse.MetricCompare> compares = new ArrayList<>();

        // 鏀堕泦鎵€鏈夋寚鏍囧悕绉?
        Set<String> allMetrics = new LinkedHashSet<>();
        for (TestResult r : results) {
            if (r.getParsedData() != null) {
                allMetrics.addAll(extractMetricNames(r.getParsedData()));
            }
        }

        // 濡傛灉鎸囧畾浜嗘寚鏍囧悕绉帮紝鍙姣旀寚瀹氱殑
        if (metricNames != null && !metricNames.isEmpty()) {
            allMetrics.retainAll(metricNames);
        }

        // 瀵规瘡涓寚鏍囪繘琛屽姣?
        for (String metricName : allMetrics) {
            ResultCompareResponse.MetricCompare compare = new ResultCompareResponse.MetricCompare();
            compare.setMetricName(metricName);

            List<ResultCompareResponse.MetricValue> values = new ArrayList<>();
            List<Double> numericValues = new ArrayList<>();

            for (TestResult r : results) {
                Object value = r.getParsedData() != null ? extractMetricValue(r.getParsedData(), metricName) : null;

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

            // 璁＄畻鍙樺寲鐜囷紙濡傛灉鏈夊涓粨鏋滐級
                        if (numericValues.size() >= 2) {
                // 环比变化率：计算每对相邻结果的变化率，取绝对值的最大值
                double maxChangeRate = 0;
                for (int i = 1; i < numericValues.size(); i++) {
                    Double prev = numericValues.get(i - 1);
                    Double curr = numericValues.get(i);
                    if (prev != null && curr != null && prev != 0) {
                        double changeRate = Math.abs((curr - prev) / prev * 100);
                        if (changeRate > maxChangeRate) {
                            maxChangeRate = changeRate;
                        }
                    }
                }
                compare.setChangeRate(Math.round(maxChangeRate * 100) / 100.0);
                compare.setTrend(maxChangeRate > 5 ? "unstable" : "stable");

                // 计算最大值和最小值
                double min = numericValues.stream().filter(Objects::nonNull).min(Double::compareTo).orElse(0.0);
                double max = numericValues.stream().filter(Objects::nonNull).max(Double::compareTo).orElse(0.0);
                compare.setMinValue(min);
                compare.setMaxValue(max);
            }

            compares.add(compare);
        }

        return compares;
    }

    /**
     * 鍒嗘瀽宸紓
     */
    private List<ResultCompareResponse.DiffItem> analyzeDifferences(List<TestResult> results) {
        List<ResultCompareResponse.DiffItem> differences = new ArrayList<>();

        if (results.size() < 2) {
            return differences;
        }

        TestResult base = results.get(0);

        // 缁撴灉鐘舵€佸樊寮?
        ResultCompareResponse.DiffItem resultDiff = new ResultCompareResponse.DiffItem();
        resultDiff.setCategory("result");
        resultDiff.setName("娴嬭瘯缁撴灉");
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

        // 鍒嗘暟宸紓
        ResultCompareResponse.DiffItem scoreDiff = new ResultCompareResponse.DiffItem();
        scoreDiff.setCategory("score");
        scoreDiff.setName("娴嬭瘯鍒嗘暟");
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
     * 璁＄畻缁熻淇℃伅
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
     * 鎻愬彇鎸囨爣鍚嶇О鍒楄〃
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
                    // 妫€鏌ユ槸鍚︽槸鎸囨爣鍊煎璞★紙鍖呭惈 value 瀛楁锛?
                    Map<?, ?> valueMap = (Map<?, ?>) entry.getValue();
                    if (valueMap.containsKey("value")) {
                        // 鍙坊鍔犳暟鍊肩被鍨嬬殑瀛楁
                        Object val = valueMap.get("value");
                        if (val instanceof Number) {
                            names.add(key);
                        }
                    } else {
                        extractNamesRecursive(entry.getValue(), key, names);
                    }
                } else if (entry.getValue() instanceof Number) {
                    // 鍙坊鍔犳暟鍊肩被鍨嬬殑瀛楁
                    names.add(key);
                }
            }
        }
    }

    /**
     * 鎻愬彇鎸囨爣鍊?
     */
    private Object extractMetricValue(Map<String, Object> metrics, String metricName) {
        // 棣栧厛灏濊瘯鐩存帴鐢ㄥ畬鏁?key 鏌ユ壘锛堟敮鎸佹墎骞冲拰宓屽涓ょ缁撴瀯锛?
        if (metrics.containsKey(metricName)) {
            return metrics.get(metricName);
        }
        
        // 濡傛灉鎵句笉鍒帮紝灏濊瘯宓屽鏌ユ壘
        String[] parts = metricName.split("\\.");
        Object current = metrics;

        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
        }

        // 濡傛灉鏄寚鏍囧€煎璞★紝鎻愬彇 value 瀛楁
        if (current instanceof Map) {
            Map<?, ?> valueMap = (Map<?, ?>) current;
            if (valueMap.containsKey("value")) {
                return valueMap.get("value");
            }
        }

        return current;
    }

    /**
     * 鏍煎紡鍖栧€?
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

