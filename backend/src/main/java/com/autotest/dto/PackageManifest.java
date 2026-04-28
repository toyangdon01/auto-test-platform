package com.autotest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 脚本包清单
 *
 * @author auto-test-platform
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PackageManifest {
    
    /**
     * 格式版本
     */
    private String format;
    
    /**
     * 导出日期
     */
    private String exportedAt;
    
    /**
     * 脚本名称列表（兼容旧格式）
     */
    private List<String> scripts;
    
    /**
     * 脚本详情列表（包含是否存在状态）
     */
    private List<ScriptPreview> scriptDetails;
    
    /**
     * 资源文件列表（可选）
     */
    private List<String> resources;
    
    /**
     * 脚本预览信息
     */
    @Data
    public static class ScriptPreview {
        /**
         * 脚本名称
         */
        private String name;
        
        /**
         * 是否已存在
         */
        private boolean existing;
        
        /**
         * 已存在脚本的ID（如果存在）
         */
        private Long existingId;
    }
    
    /**
     * 是否有效
     */
    public boolean isValid() {
        return format != null && ((scripts != null && !scripts.isEmpty()) || 
               (scriptDetails != null && !scriptDetails.isEmpty()));
    }
}
