package com.autotest.controller;

import com.autotest.common.ApiResponse;
import com.autotest.common.PageResult;
import com.autotest.dto.ResultCompareRequest;
import com.autotest.dto.ResultCompareResponse;
import com.autotest.dto.TestResultDetailResponse;
import com.autotest.entity.TestResult;
import com.autotest.service.ResultCompareService;
import com.autotest.service.TestResultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 测试结果控制器
 *
 * @author auto-test-platform
 */
@RestController
@RequestMapping("/results")
@RequiredArgsConstructor
@Tag(name = "测试结果", description = "测试结果的增删改查")
public class TestResultController {

    private final TestResultService testResultService;
    private final ResultCompareService resultCompareService;

    @GetMapping
    @Operation(summary = "分页查询测试结果")
    public ApiResponse<PageResult<TestResult>> getPage(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "任务ID") @RequestParam(required = false) Long taskId,
            @Parameter(description = "服务器ID") @RequestParam(required = false) Long serverId,
            @Parameter(description = "结果状态") @RequestParam(required = false) String result) {
        return ApiResponse.success(testResultService.getPage(page, pageSize, taskId, serverId, result));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取测试结果详情")
    public ApiResponse<TestResult> getById(@Parameter(description = "结果ID") @PathVariable Long id) {
        return ApiResponse.success(testResultService.getById(id));
    }

    @GetMapping("/{id}/detail")
    @Operation(summary = "获取测试结果详情（包含关联信息）")
    public ApiResponse<TestResultDetailResponse> getDetail(@Parameter(description = "结果ID") @PathVariable Long id) {
        return ApiResponse.success(testResultService.getDetail(id));
    }

    @PostMapping
    @Operation(summary = "创建测试结果")
    public ApiResponse<TestResult> create(@RequestBody TestResult testResult) {
        return ApiResponse.success(testResultService.create(testResult));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新测试结果")
    public ApiResponse<TestResult> update(
            @Parameter(description = "结果ID") @PathVariable Long id,
            @RequestBody TestResult testResult) {
        return ApiResponse.success(testResultService.update(id, testResult));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除测试结果")
    public ApiResponse<Void> delete(@Parameter(description = "结果ID") @PathVariable Long id) {
        testResultService.delete(id);
        return ApiResponse.success();
    }

    @DeleteMapping("/batch")
    @Operation(summary = "批量删除测试结果")
    public ApiResponse<Void> deleteBatch(@RequestBody List<Long> ids) {
        testResultService.deleteBatch(ids);
        return ApiResponse.success();
    }

    @GetMapping("/task/{taskId}")
    @Operation(summary = "获取任务的测试结果列表")
    public ApiResponse<List<TestResult>> getByTaskId(@Parameter(description = "任务ID") @PathVariable Long taskId) {
        return ApiResponse.success(testResultService.getByTaskId(taskId));
    }

    @GetMapping("/statistics")
    @Operation(summary = "获取结果统计数据")
    public ApiResponse<Map<String, Object>> getStatistics(
            @Parameter(description = "任务ID") @RequestParam(required = false) Long taskId) {
        return ApiResponse.success(testResultService.getStatistics(taskId));
    }

    @GetMapping("/trend/{taskId}")
    @Operation(summary = "获取趋势数据")
    public ApiResponse<List<Map<String, Object>>> getTrend(
            @Parameter(description = "任务ID") @PathVariable Long taskId,
            @Parameter(description = "天数") @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.success(testResultService.getTrend(taskId, days));
    }

    @PostMapping("/compare")
    @Operation(summary = "结果对比")
    public ApiResponse<ResultCompareResponse> compareResults(@RequestBody ResultCompareRequest request) {
        return ApiResponse.success(resultCompareService.compareResults(request));
    }

    @GetMapping("/trend-data")
    @Operation(summary = "获取趋势数据（通用）")
    public ApiResponse<Map<String, Object>> getTrendData(
            @Parameter(description = "脚本ID") @RequestParam(required = false) Long scriptId,
            @Parameter(description = "服务器ID") @RequestParam(required = false) Long serverId,
            @Parameter(description = "指标名称") @RequestParam(required = false) String metricName,
            @Parameter(description = "天数") @RequestParam(defaultValue = "7") Integer days) {
        return ApiResponse.success(resultCompareService.getTrendData(scriptId, serverId, metricName, days));
    }

    @GetMapping("/comparable/{taskId}")
    @Operation(summary = "获取可对比的结果列表")
    public ApiResponse<List<Map<String, Object>>> getComparableResults(@Parameter(description = "任务ID") @PathVariable Long taskId) {
        return ApiResponse.success(resultCompareService.getComparableResults(taskId));
    }

    @GetMapping("/export")
    @Operation(summary = "导出测试结果")
    public void exportResults(
            @Parameter(description = "任务ID") @RequestParam(required = false) Long taskId,
            @Parameter(description = "服务器ID") @RequestParam(required = false) Long serverId,
            @Parameter(description = "格式：csv/json") @RequestParam(defaultValue = "csv") String format,
            HttpServletResponse response) throws IOException {
        
        // 获取结果数据
        PageResult<TestResult> pageResult = testResultService.getPage(1, 10000, taskId, serverId, null);
        List<TestResult> results = pageResult.getItems();
        
        // 设置响应头
        String fileName = "test_results_" + System.currentTimeMillis() + "." + format;
        String encodedFilename = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        
        if ("json".equalsIgnoreCase(format)) {
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFilename);
            
            // 导出 JSON
            StringBuilder json = new StringBuilder();
            json.append("[\n");
            for (int i = 0; i < results.size(); i++) {
                TestResult r = results.get(i);
                json.append("  {\n");
                json.append("    \"id\": ").append(r.getId()).append(",\n");
                json.append("    \"taskId\": ").append(r.getTaskId()).append(",\n");
                json.append("    \"serverId\": ").append(r.getServerId()).append(",\n");
                json.append("    \"result\": \"").append(r.getResult() != null ? r.getResult() : "").append("\",\n");
                json.append("    \"overallScore\": ").append(r.getOverallScore() != null ? r.getOverallScore() : "null").append(",\n");
                json.append("    \"resultReason\": \"").append(escapeJson(r.getResultReason())).append("\",\n");
                json.append("    \"createdAt\": \"").append(r.getCreatedAt() != null ? r.getCreatedAt().toString() : "").append("\"\n");
                json.append(i < results.size() - 1 ? "  },\n" : "  }\n");
            }
            json.append("]");
            response.getWriter().write(json.toString());
        } else {
            // 默认 CSV
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFilename);
            
            // 导出 CSV（添加 BOM 以支持 Excel 打开）
            OutputStreamWriter writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
            writer.write('\ufeff'); // BOM
            writer.write("ID,任务ID,服务器ID,结果,得分,结果原因,创建时间\n");
            
            for (TestResult r : results) {
                writer.write(String.format("%d,%d,%d,%s,%s,%s,%s\n",
                        r.getId(),
                        r.getTaskId() != null ? r.getTaskId() : 0,
                        r.getServerId() != null ? r.getServerId() : 0,
                        r.getResult() != null ? r.getResult() : "",
                        r.getOverallScore() != null ? r.getOverallScore().toString() : "",
                        escapeCsv(r.getResultReason()),
                        r.getCreatedAt() != null ? r.getCreatedAt().toString() : ""
                ));
            }
            writer.flush();
        }
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private String escapeCsv(String str) {
        if (str == null) return "";
        if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }
}
