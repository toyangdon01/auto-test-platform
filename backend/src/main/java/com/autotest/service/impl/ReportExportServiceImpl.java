package com.autotest.service.impl;

import com.autotest.dto.ReportDetailResponse;
import com.autotest.entity.Report;
import com.autotest.entity.Server;
import com.autotest.entity.Task;
import com.autotest.entity.TestResult;
import com.autotest.mapper.ReportMapper;
import com.autotest.mapper.ServerMapper;
import com.autotest.mapper.TaskMapper;
import com.autotest.mapper.TestResultMapper;
import com.autotest.service.ReportExportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 报告导出服务实现
 *
 * @author auto-test-platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportExportServiceImpl implements ReportExportService {

    private final ReportMapper reportMapper;
    private final TaskMapper taskMapper;
    private final TestResultMapper testResultMapper;
    private final ServerMapper serverMapper;
    private final ObjectMapper objectMapper;

    @Value("${app.storage.report-path:./reports}")
    private String reportPath;

    @Override
    public String generateHtml(Long reportId) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new RuntimeException("报告不存在");
        }

        ReportDetailResponse detail = buildReportDetail(report);
        return renderHtmlTemplate(detail);
    }

    @Override
    public void generatePdf(Long reportId, OutputStream outputStream) {
        String html = generateHtml(reportId);
        try {
            outputStream.write(html.getBytes("UTF-8"));
        } catch (IOException e) {
            throw new RuntimeException("生成 PDF 失败", e);
        }
    }

    @Override
    public String getReportFilePath(Long reportId, String format) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            return null;
        }
        String fileName = "report_" + reportId + "_" + 
                report.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return Paths.get(reportPath, fileName + "." + format.toLowerCase()).toString();
    }

    @Override
    public Map<String, Object> generateReportFile(Long reportId, String format) {
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            Path dirPath = Paths.get(reportPath);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            String fileName = "report_" + reportId + "_" + System.currentTimeMillis() + "." + format.toLowerCase();
            Path filePath = Paths.get(reportPath, fileName);

            if ("pdf".equalsIgnoreCase(format) || "html".equalsIgnoreCase(format)) {
                String html = generateHtml(reportId);

                try (OutputStream os = Files.newOutputStream(filePath)) {
                    os.write(html.getBytes("UTF-8"));
                }

                result.put("success", true);
                result.put("fileName", fileName);
                result.put("filePath", filePath.toString());
                result.put("fileSize", Files.size(filePath));
                result.put("format", format);
            } else {
                result.put("success", false);
                result.put("error", "不支持的格式: " + format);
            }
        } catch (Exception e) {
            log.error("生成报告文件失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 构建报告详情
     */
    private ReportDetailResponse buildReportDetail(Report report) {
        ReportDetailResponse detail = new ReportDetailResponse();
        detail.setId(report.getId());
        detail.setTaskId(report.getTaskId());
        detail.setTitle(report.getTitle());
        detail.setSummary(report.getSummary());
        detail.setConclusion(report.getConclusion());
        detail.setCreatedAt(report.getCreatedAt());

        // 获取任务
        Task task = taskMapper.selectById(report.getTaskId());
        if (task != null) {
            detail.setTaskName(task.getName());
        }

        // 获取测试结果
        List<TestResult> allResults = testResultMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TestResult>()
                        .eq(TestResult::getTaskId, report.getTaskId())
        );

        // 概览
        ReportDetailResponse.ExecutionOverview overview = new ReportDetailResponse.ExecutionOverview();
        overview.setTotalServers((int) allResults.stream().map(TestResult::getServerId).distinct().count());
        overview.setSuccessCount((int) allResults.stream().filter(r -> "pass".equals(r.getResult())).count());
        overview.setFailCount((int) allResults.stream().filter(r -> "fail".equals(r.getResult())).count());
        overview.setTotalTimeMs(allResults.stream().mapToInt(r -> r.getDurationMs() != null ? r.getDurationMs() : 0).sum());
        
        double avgScore = allResults.stream()
                .filter(r -> r.getOverallScore() != null)
                .mapToInt(TestResult::getOverallScore)
                .average().orElse(0);
        overview.setAvgScore(String.format("%.1f", avgScore));
        
        detail.setOverview(overview);

        // 测试结果列表
        List<ReportDetailResponse.TestResultSummary> resultSummaries = allResults.stream().map(r -> {
            ReportDetailResponse.TestResultSummary summary = new ReportDetailResponse.TestResultSummary();
            summary.setId(r.getId());
            summary.setResult(r.getResult());
            summary.setScore(r.getOverallScore());
            summary.setDurationMs(r.getDurationMs());

            Server server = serverMapper.selectById(r.getServerId());
            if (server != null) {
                summary.setServerName(server.getName());
                summary.setServerIp(server.getHost());
            }

            if (r.getMetrics() != null) {
                summary.setKeyMetrics(extractKeyMetrics(r.getMetrics()));
            }

            return summary;
        }).collect(Collectors.toList());
        detail.setResults(resultSummaries);

        // 指标统计
        detail.setMetricStats(calculateMetricStats(allResults));

        return detail;
    }

    /**
     * 提取关键指标
     */
    private Map<String, Object> extractKeyMetrics(Map<String, Object> metrics) {
        Map<String, Object> keyMetrics = new LinkedHashMap<>();
        int count = 0;
        for (Map.Entry<String, Object> entry : metrics.entrySet()) {
            if (count >= 5) break;
            keyMetrics.put(entry.getKey(), entry.getValue());
            count++;
        }
        return keyMetrics;
    }

    /**
     * 计算指标统计
     */
    private Map<String, ReportDetailResponse.MetricStats> calculateMetricStats(List<TestResult> results) {
        Map<String, ReportDetailResponse.MetricStats> stats = new LinkedHashMap<>();
        Map<String, List<Double>> metricValues = new LinkedHashMap<>();

        for (TestResult r : results) {
            if (r.getMetrics() != null) {
                extractMetricValues(r.getMetrics(), "", metricValues);
            }
        }

        for (Map.Entry<String, List<Double>> entry : metricValues.entrySet()) {
            List<Double> values = entry.getValue();
            if (!values.isEmpty()) {
                ReportDetailResponse.MetricStats stat = new ReportDetailResponse.MetricStats();
                stat.setName(entry.getKey());
                stat.setMin(values.stream().mapToDouble(Double::doubleValue).min().orElse(0));
                stat.setMax(values.stream().mapToDouble(Double::doubleValue).max().orElse(0));
                stat.setAvg(values.stream().mapToDouble(Double::doubleValue).average().orElse(0));
                stats.put(entry.getKey(), stat);
            }
        }

        return stats;
    }

    /**
     * 提取指标值
     */
    private void extractMetricValues(Object obj, String prefix, Map<String, List<Double>> metricValues) {
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = prefix.isEmpty() ? String.valueOf(entry.getKey()) : prefix + "." + entry.getKey();
                Object value = entry.getValue();

                if (value instanceof Map) {
                    Map<?, ?> valueMap = (Map<?, ?>) value;
                    if (valueMap.containsKey("value")) {
                        Object v = valueMap.get("value");
                        if (v instanceof Number) {
                            metricValues.computeIfAbsent(key, k -> new ArrayList<>())
                                    .add(((Number) v).doubleValue());
                        }
                    } else {
                        extractMetricValues(value, key, metricValues);
                    }
                } else if (value instanceof Number) {
                    metricValues.computeIfAbsent(key, k -> new ArrayList<>())
                            .add(((Number) value).doubleValue());
                }
            }
        }
    }

    /**
     * 渲染 HTML 模板
     */
    private String renderHtmlTemplate(ReportDetailResponse detail) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"zh-CN\">\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <title>").append(detail.getTitle()).append("</title>\n");
        html.append("  <style>\n");
        html.append("    body { font-family: 'Microsoft YaHei', Arial, sans-serif; margin: 40px; background: #f5f5f5; }\n");
        html.append("    .container { max-width: 1000px; margin: 0 auto; background: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 12px rgba(0,0,0,0.1); }\n");
        html.append("    h1 { color: #333; border-bottom: 2px solid #409EFF; padding-bottom: 10px; }\n");
        html.append("    h2 { color: #666; margin-top: 30px; }\n");
        html.append("    .overview { display: grid; grid-template-columns: repeat(4, 1fr); gap: 20px; margin: 20px 0; }\n");
        html.append("    .stat-card { background: #f8f9fa; padding: 20px; border-radius: 8px; text-align: center; }\n");
        html.append("    .stat-value { font-size: 28px; font-weight: bold; color: #409EFF; }\n");
        html.append("    .stat-label { color: #999; margin-top: 5px; }\n");
        html.append("    .pass { color: #67C23A; }\n");
        html.append("    .fail { color: #F56C6C; }\n");
        html.append("    table { width: 100%; border-collapse: collapse; margin: 20px 0; }\n");
        html.append("    th, td { padding: 12px; text-align: left; border-bottom: 1px solid #eee; }\n");
        html.append("    th { background: #f5f7fa; color: #606266; }\n");
        html.append("    .footer { margin-top: 40px; padding-top: 20px; border-top: 1px solid #eee; color: #999; text-align: center; }\n");
        html.append("  </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("  <div class=\"container\">\n");

        // 标题
        html.append("    <h1>").append(detail.getTitle()).append("</h1>\n");
        html.append("    <p style=\"color: #999;\">任务：").append(detail.getTaskName() != null ? detail.getTaskName() : "-").append("</p>\n");
        html.append("    <p style=\"color: #999;\">生成时间：").append(detail.getCreatedAt() != null ?
                detail.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "").append("</p>\n");

        // 概览
        if (detail.getOverview() != null) {
            ReportDetailResponse.ExecutionOverview overview = detail.getOverview();
            html.append("    <h2>执行概览</h2>\n");
            html.append("    <div class=\"overview\">\n");
            html.append("      <div class=\"stat-card\">\n");
            html.append("        <div class=\"stat-value\">").append(overview.getTotalServers()).append("</div>\n");
            html.append("        <div class=\"stat-label\">测试服务器</div>\n");
            html.append("      </div>\n");
            html.append("      <div class=\"stat-card\">\n");
            html.append("        <div class=\"stat-value pass\">").append(overview.getSuccessCount()).append("</div>\n");
            html.append("        <div class=\"stat-label\">通过数</div>\n");
            html.append("      </div>\n");
            html.append("      <div class=\"stat-card\">\n");
            html.append("        <div class=\"stat-value fail\">").append(overview.getFailCount()).append("</div>\n");
            html.append("        <div class=\"stat-label\">失败数</div>\n");
            html.append("      </div>\n");
            html.append("      <div class=\"stat-card\">\n");
            html.append("        <div class=\"stat-value\">").append(overview.getAvgScore()).append("</div>\n");
            html.append("        <div class=\"stat-label\">平均分</div>\n");
            html.append("      </div>\n");
            html.append("    </div>\n");
        }

        // 测试结果
        if (detail.getResults() != null && !detail.getResults().isEmpty()) {
            html.append("    <h2>测试结果</h2>\n");
            html.append("    <table>\n");
            html.append("      <tr><th>服务器</th><th>IP地址</th><th>结果</th><th>分数</th><th>耗时(ms)</th></tr>\n");

            for (ReportDetailResponse.TestResultSummary item : detail.getResults()) {
                String resultClass = "pass".equals(item.getResult()) ? "pass" : "fail";
                html.append("      <tr>\n");
                html.append("        <td>").append(item.getServerName() != null ? item.getServerName() : "-").append("</td>\n");
                html.append("        <td>").append(item.getServerIp() != null ? item.getServerIp() : "-").append("</td>\n");
                html.append("        <td class=\"").append(resultClass).append("\">").append(item.getResult()).append("</td>\n");
                html.append("        <td>").append(item.getScore() != null ? item.getScore() : "-").append("</td>\n");
                html.append("        <td>").append(item.getDurationMs() != null ? item.getDurationMs() : "-").append("</td>\n");
                html.append("      </tr>\n");
            }

            html.append("    </table>\n");
        }

        // 指标统计
        if (detail.getMetricStats() != null && !detail.getMetricStats().isEmpty()) {
            html.append("    <h2>指标统计</h2>\n");
            html.append("    <table>\n");
            html.append("      <tr><th>指标名称</th><th>最小值</th><th>最大值</th><th>平均值</th></tr>\n");

            for (Map.Entry<String, ReportDetailResponse.MetricStats> entry : detail.getMetricStats().entrySet()) {
                ReportDetailResponse.MetricStats stat = entry.getValue();
                html.append("      <tr>\n");
                html.append("        <td>").append(stat.getName()).append("</td>\n");
                html.append("        <td>").append(formatNumber(stat.getMin())).append("</td>\n");
                html.append("        <td>").append(formatNumber(stat.getMax())).append("</td>\n");
                html.append("        <td>").append(formatNumber(stat.getAvg())).append("</td>\n");
                html.append("      </tr>\n");
            }

            html.append("    </table>\n");
        }

        // 结论
        if (detail.getConclusion() != null) {
            html.append("    <h2>测试结论</h2>\n");
            html.append("    <p>").append(detail.getConclusion()).append("</p>\n");
        }

        // 页脚
        html.append("    <div class=\"footer\">\n");
        html.append("      <p>自动化测试管理平台 - 测试报告</p>\n");
        html.append("      <p>© 2026 Auto Test Platform</p>\n");
        html.append("    </div>\n");

        html.append("  </div>\n");
        html.append("</body>\n");
        html.append("</html>\n");

        return html.toString();
    }

    /**
     * 格式化数字
     */
    private String formatNumber(Object value) {
        if (value == null) return "-";
        if (value instanceof Double) {
            return String.format("%.2f", value);
        }
        return String.valueOf(value);
    }
}
