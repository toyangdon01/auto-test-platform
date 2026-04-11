package com.autotest.service;

import com.autotest.dto.ReportGenerateRequest;

import java.io.OutputStream;
import java.util.Map;

/**
 * 报告导出服务接口
 *
 * @author auto-test-platform
 */
public interface ReportExportService {

    /**
     * 生成 HTML 报告
     */
    String generateHtml(Long reportId);

    /**
     * 生成 PDF 报告
     */
    void generatePdf(Long reportId, OutputStream outputStream);

    /**
     * 获取报告文件路径
     */
    String getReportFilePath(Long reportId, String format);

    /**
     * 生成报告文件
     */
    Map<String, Object> generateReportFile(Long reportId, String format);
}
