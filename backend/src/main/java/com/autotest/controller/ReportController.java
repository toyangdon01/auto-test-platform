package com.autotest.controller;

import com.autotest.common.ApiResponse;
import com.autotest.common.PageResult;
import com.autotest.dto.ReportDetailResponse;
import com.autotest.dto.ReportGenerateRequest;
import com.autotest.entity.Report;
import com.autotest.service.ReportExportService;
import com.autotest.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 报告控制器
 *
 * @author auto-test-platform
 */
@Tag(name = "报告管理")
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ReportExportService reportExportService;

    @GetMapping
    @Operation(summary = "获取报告列表")
    public ApiResponse<PageResult<Report>> getList(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "任务ID") @RequestParam(required = false) Long taskId) {
        return ApiResponse.success(reportService.getList(page, pageSize, taskId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取报告详情")
    public ApiResponse<Report> getById(@Parameter(description = "报告ID") @PathVariable Long id) {
        return ApiResponse.success(reportService.getDetail(id) != null ? 
                reportService.getList(1, 1, null).getItems().stream()
                        .filter(r -> r.getId().equals(id))
                        .findFirst()
                        .orElse(null) : null);
    }

    @GetMapping("/{id}/detail")
    @Operation(summary = "获取报告详情（包含完整数据）")
    public ApiResponse<ReportDetailResponse> getDetail(@Parameter(description = "报告ID") @PathVariable Long id) {
        return ApiResponse.success(reportService.getDetail(id));
    }

    @PostMapping("/generate")
    @Operation(summary = "生成报告")
    public ApiResponse<Report> generate(@RequestBody ReportGenerateRequest request) {
        return ApiResponse.success(reportService.generate(request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除报告")
    public ApiResponse<Void> delete(@Parameter(description = "报告ID") @PathVariable Long id) {
        reportService.delete(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "下载报告")
    public void downloadReport(
            @Parameter(description = "报告ID") @PathVariable Long id,
            @Parameter(description = "格式：pdf/html") @RequestParam(defaultValue = "html") String format,
            HttpServletResponse response) throws IOException {
        
        Report report = reportService.getList(1, 1, null).getItems().stream()
                .filter(r -> r.getId().equals(id))
                .findFirst()
                .orElse(null);
        
        if (report == null) {
            response.setStatus(404);
            response.getWriter().write("报告不存在");
            return;
        }

        // 生成文件
        Map<String, Object> result = reportExportService.generateReportFile(id, format);
        
        if (!Boolean.TRUE.equals(result.get("success"))) {
            response.setStatus(500);
            response.getWriter().write("生成失败: " + result.get("error"));
            return;
        }

        // 设置响应头
        String fileName = report.getTitle() + "." + format.toLowerCase();
        if ("html".equalsIgnoreCase(format)) {
            response.setContentType("text/html;charset=UTF-8");
        } else {
            response.setContentType("application/pdf");
        }
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, 
                "attachment; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + "\"");

        // 输出内容
        String html = reportExportService.generateHtml(id);
        response.getWriter().write(html);
    }

    @GetMapping("/{id}/export")
    @Operation(summary = "导出报告文件")
    public void exportReport(
            @Parameter(description = "报告ID") @PathVariable Long id,
            @Parameter(description = "格式：pdf/html") @RequestParam(defaultValue = "html") String format,
            HttpServletResponse response) throws IOException {
        
        Report report = reportService.getList(1, 1, null).getItems().stream()
                .filter(r -> r.getId().equals(id))
                .findFirst()
                .orElse(null);
        
        if (report == null) {
            response.setStatus(404);
            response.getWriter().write("报告不存在");
            return;
        }

        // 生成 HTML 内容
        String htmlContent = reportExportService.generateHtml(id);
        
        // 设置响应头
        String fileName = report.getTitle() + "." + format.toLowerCase();
        if ("html".equalsIgnoreCase(format)) {
            response.setContentType("text/html;charset=UTF-8");
        } else {
            response.setContentType("application/pdf");
        }
        
        String encodedFilename = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, 
                "attachment; filename*=UTF-8''" + encodedFilename);

        // 输出内容
        response.getWriter().write(htmlContent);
    }

    @GetMapping("/{id}/html")
    @Operation(summary = "获取报告HTML内容")
    public ApiResponse<String> getReportHtml(@Parameter(description = "报告ID") @PathVariable Long id) {
        return ApiResponse.success(reportExportService.generateHtml(id));
    }
}
