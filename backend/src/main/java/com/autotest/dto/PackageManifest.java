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
     * 脚本名称列表
     */
    private List<String> scripts;
    
    /**
     * 资源文件列表（可选）
     */
    private List<String> resources;
    
    /**
     * 是否有效
     */
    public boolean isValid() {
        return format != null && scripts != null && !scripts.isEmpty();
    }
}
