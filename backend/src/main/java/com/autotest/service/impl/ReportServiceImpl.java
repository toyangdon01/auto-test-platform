package com.autotest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.autotest.common.PageResult;
import com.autotest.dto.ReportDetailResponse;
import com.autotest.dto.ReportGenerateRequest;
import com.autotest.entity.Report;
import com.autotest.entity.Server;
import com.autotest.entity.Task;
import com.autotest.entity.TestResult;
import com.autotest.mapper.ReportMapper;
import com.autotest.mapper.ServerMapper;
import com.autotest.mapper.TaskMapper;
import com.autotest.mapper.TestResultMapper;
import com.autotest.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 报告服务实现
 *
 * @author auto-test-platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportMapper reportMapper;
    private final TaskMapper taskMapper;
    private final TestResultMapper testResultMapper;
    private final ServerMapper serverMapper;

    @Override
    public PageResult<Report> getList(int page, int pageSize, Long taskId) {
        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<>();
        if (taskId != null) {
            wrapper.eq(Report::getTaskId, taskId);
        }
        wrapper.orderByDesc(Report::getCreatedAt);

        IPage<Report> pageResult = reportMapper.selectPage(
                new Page<>(page, pageSize), wrapper);
        return PageResult.of(pageResult);
    }

    @Override
    public ReportDetailResponse getDetail(Long id) {
        Report report = reportMapper.selectById(id);
        if (report == null) {
            return null;
        }

        ReportDetailResponse response = new ReportDetailResponse();
        response.setId(report.getId());
        response.setTaskId(report.getTaskId());
        response.setTitle(report.getTitle());
        response.setSummary(report.getSummary());
        response.setConclusion(report.getConclusion());
        response.setFileFormat(report.getFileFormat());
        response.setFilePath(report.getFilePath());
        response.setCreatedAt(report.getCreatedAt());

        // 获取任务名称
        if (report.getTaskId() != null) {
            Task task = taskMapper.selectById(report.getTaskId());
            if (task != null) {
                response.setTaskName(task.getName());
            }
        }

        // 解析报告数据
        if (report.getReportData() != null) {
            Map<String, Object> data = report.getReportData();

            // 执行概览
            Object overview = data.get("overview");
            if (overview instanceof Map) {
                ReportDetailResponse.ExecutionOverview ov = new ReportDetailResponse.ExecutionOverview();
                Map<String, Object> ovMap = (Map<String, Object>) overview;
                ov.setTotalServers(getInt(ovMap, "totalServers"));
                ov.setSuccessCount(getInt(ovMap, "successCount"));
                ov.setFailCount(getInt(ovMap, "failCount"));
                ov.setTotalTimeMs(getInt(ovMap, "totalTimeMs"));
                ov.setAvgScore(getString(ovMap, "avgScore"));
                response.setOverview(ov);
            }

            // 测试结果列表
            Object results = data.get("results");
            if (results instanceof List) {
                List<ReportDetailResponse.TestResultSummary> resultList = new ArrayList<>();
                for (Object item : (List<?>) results) {
                    if (item instanceof Map) {
                        Map<String, Object> itemMap = (Map<String, Object>) item;
                        ReportDetailResponse.TestResultSummary summary = new ReportDetailResponse.TestResultSummary();
                        summary.setId(getLong(itemMap, "id"));
                        summary.setServerName(getString(itemMap, "serverName"));
                        summary.setServerIp(getString(itemMap, "serverIp"));
                        summary.setResult(getString(itemMap, "result"));
                        summary.setScore(getInt(itemMap, "score"));
                        summary.setDurationMs(getInt(itemMap, "durationMs"));
                        summary.setKeyMetrics((Map<String, Object>) itemMap.get("keyMetrics"));
                        resultList.add(summary);
                    }
                }
                response.setResults(resultList);
            }

            // 指标统计
            Object metrics = data.get("metricStats");
            if (metrics instanceof Map) {
                Map<String, ReportDetailResponse.MetricStats> statsMap = new HashMap<>();
                Map<String, Object> metricsMap = (Map<String, Object>) metrics;
                for (Map.Entry<String, Object> entry : metricsMap.entrySet()) {
                    if (entry.getValue() instanceof Map) {
                        Map<String, Object> statMap = (Map<String, Object>) entry.getValue();
                        ReportDetailResponse.MetricStats stats = new ReportDetailResponse.MetricStats();
                        stats.setName(getString(statMap, "name"));
                        stats.setMin(statMap.get("min"));
                        stats.setMax(statMap.get("max"));
                        stats.setAvg(statMap.get("avg"));
                        stats.setUnit(getString(statMap, "unit"));
                        statsMap.put(entry.getKey(), stats);
                    }
                }
                response.setMetricStats(statsMap);
            }
        }

        return response;
    }

    @Override
    public Report generate(ReportGenerateRequest request) {
        if (request.getTaskIds() == null || request.getTaskIds().isEmpty()) {
            throw new RuntimeException("任务ID不能为空");
        }

        Long taskId = request.getTaskIds().get(0);
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }

        // 查询测试结果
        LambdaQueryWrapper<TestResult> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TestResult::getTaskId, taskId);
        List<TestResult> results = testResultMapper.selectList(wrapper);

        if (results.isEmpty()) {
            throw new RuntimeException("任务没有测试结果");
        }

        // 构建报告数据
        Map<String, Object> reportData = new HashMap<>();

        // 执行概览
        ReportDetailResponse.ExecutionOverview overview = new ReportDetailResponse.ExecutionOverview();
        int totalServers = results.size();
        int successCount = (int) results.stream().filter(r -> "pass".equals(r.getResult())).count();
        int failCount = totalServers - successCount;
        int totalTimeMs = results.stream().mapToInt(r -> r.getDurationMs() != null ? r.getDurationMs() : 0).sum();
        double avgScore = results.stream()
                .filter(r -> r.getOverallScore() != null)
                .mapToInt(TestResult::getOverallScore)
                .average()
                .orElse(0);

        overview.setTotalServers(totalServers);
        overview.setSuccessCount(successCount);
        overview.setFailCount(failCount);
        overview.setTotalTimeMs(totalTimeMs);
        overview.setAvgScore(String.format("%.1f", avgScore));

        reportData.put("overview", toMap(overview));

        // 测试结果列表
        List<Map<String, Object>> resultList = new ArrayList<>();
        Map<String, Map<String, Object>> metricAgg = new HashMap<>();

        for (TestResult result : results) {
            Map<String, Object> summary = new HashMap<>();
            summary.put("id", result.getId());
            summary.put("result", result.getResult());
            summary.put("score", result.getOverallScore());
            summary.put("durationMs", result.getDurationMs());

            // 服务器信息
            if (result.getServerId() != null) {
                Server server = serverMapper.selectById(result.getServerId());
                if (server != null) {
                    summary.put("serverName", server.getName());
                    summary.put("serverIp", server.getHost());
                }
            }

            // 关键指标
            if (result.getMetrics() != null) {
                Map<String, Object> keyMetrics = new HashMap<>();
                for (Map.Entry<String, Object> entry : result.getMetrics().entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();

                    // 提取关键指标
                    if (key.contains("CPU") || key.contains("MEM") || key.contains("DISK") || 
                        key.contains("SCORE") || key.contains("RESULT")) {
                        keyMetrics.put(key, value);
                    }

                    // 聚合指标统计
                    if (value instanceof Number) {
                        metricAgg.computeIfAbsent(key, k -> {
                            Map<String, Object> stats = new HashMap<>();
                            stats.put("name", key);
                            stats.put("values", new ArrayList<Double>());
                            return stats;
                        });
                        ((List<Double>) metricAgg.get(key).get("values")).add(((Number) value).doubleValue());
                    }
                }
                summary.put("keyMetrics", keyMetrics);
            }

            resultList.add(summary);
        }
        reportData.put("results", resultList);

        // 指标统计
        Map<String, Object> metricStats = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : metricAgg.entrySet()) {
            List<Double> values = (List<Double>) entry.getValue().get("values");
            if (!values.isEmpty()) {
                Map<String, Object> stats = new HashMap<>();
                stats.put("name", entry.getKey());
                stats.put("min", Collections.min(values));
                stats.put("max", Collections.max(values));
                stats.put("avg", values.stream().mapToDouble(Double::doubleValue).average().orElse(0));
                metricStats.put(entry.getKey(), stats);
            }
        }
        reportData.put("metricStats", metricStats);

        // 结论
        String conclusion = failCount == 0 ? "pass" : (successCount > failCount ? "warning" : "fail");

        // 摘要
        String summary = String.format(
                "共测试 %d 台服务器，通过 %d 台，失败 %d 台，平均得分 %.1f 分。",
                totalServers, successCount, failCount, avgScore);

        // 创建报告
        Report report = new Report();
        report.setTaskId(taskId);
        report.setTitle(request.getTitle() != null ? request.getTitle() : 
                task.getName() + " 测试报告 - " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        report.setSummary(summary);
        report.setConclusion(conclusion);
        report.setReportData(reportData);
        report.setFileFormat(request.getFileFormat() != null ? request.getFileFormat() : "html");

        reportMapper.insert(report);
        log.info("生成报告成功: id={}, taskId={}", report.getId(), taskId);

        return report;
    }

    @Override
    public void delete(Long id) {
        reportMapper.deleteById(id);
    }

    @Override
    public String getFilePath(Long id) {
        Report report = reportMapper.selectById(id);
        return report != null ? report.getFilePath() : null;
    }

    private Map<String, Object> toMap(Object obj) {
        Map<String, Object> map = new HashMap<>();
        if (obj instanceof ReportDetailResponse.ExecutionOverview) {
            ReportDetailResponse.ExecutionOverview ov = (ReportDetailResponse.ExecutionOverview) obj;
            map.put("totalServers", ov.getTotalServers());
            map.put("successCount", ov.getSuccessCount());
            map.put("failCount", ov.getFailCount());
            map.put("totalTimeMs", ov.getTotalTimeMs());
            map.put("avgScore", ov.getAvgScore());
        }
        return map;
    }

    private int getInt(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    private long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
}
