package com.autotest.dto;

import lombok.Data;

import java.util.List;

/**
 * 报告生成请求
 *
 * @author auto-test-platform
 */
@Data
public class ReportGenerateRequest {

    /**
     * 任务ID列表
     */
    private List<Long> taskIds;

    /**
     * 报告标题
     */
    private String title;

    /**
     * 文件格式: html/pdf
     */
    private String fileFormat = "html";

    /**
     * 包含内容
     */
    private IncludeOptions include;

    @Data
    public static class IncludeOptions {
        private boolean summary = true;
        private boolean metrics = true;
        private boolean rawOutput = false;
        private boolean comparison = true;
        private boolean trend = true;
    }
}
