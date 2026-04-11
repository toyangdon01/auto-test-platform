package com.autotest.dto;

import lombok.Data;

import java.util.List;

/**
 * 脚本导出选项
 *
 * @author auto-test-platform
 */
@Data
public class ExportOptions {
    
    /**
     * 要导出的脚本 ID 列表
     */
    private List<Long> scriptIds;
    
    /**
     * 是否包含资源文件
     */
    private boolean includeResources = true;
    
    /**
     * 导出格式（zip）
     */
    private String format = "zip";
}
