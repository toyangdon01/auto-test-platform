package com.autotest.dto;

import com.autotest.config.ScriptConfig;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置解析结果
 * 包含解析的配置和警告信息
 * 
 * @author auto-test-platform
 */
@Data
public class ConfigParseResult {
    
    /**
     * 解析状态
     */
    public enum Status {
        SUCCESS,        // 解析成功
        FILE_NOT_FOUND, // 文件不存在
        PARSE_ERROR,    // 解析错误
        PARTIAL_SUCCESS // 部分成功（有警告）
    }
    
    /**
     * 解析状态
     */
    private Status status;
    
    /**
     * 解析后的配置对象
     */
    private ScriptConfig config;
    
    /**
     * 警告信息列表
     */
    private List<String> warnings = new ArrayList<>();
    
    /**
     * 错误信息（解析失败时）
     */
    private String error;
    
    /**
     * 错误位置（解析失败时，行号或字段名）
     */
    private String errorLocation;
    
    // ========== 静态工厂方法 ==========
    
    /**
     * 创建成功结果
     */
    public static ConfigParseResult success(ScriptConfig config) {
        ConfigParseResult result = new ConfigParseResult();
        result.setStatus(Status.SUCCESS);
        result.setConfig(config);
        return result;
    }
    
    /**
     * 创建成功结果（带警告）
     */
    public static ConfigParseResult successWithWarnings(ScriptConfig config, List<String> warnings) {
        ConfigParseResult result = new ConfigParseResult();
        result.setStatus(Status.PARTIAL_SUCCESS);
        result.setConfig(config);
        result.setWarnings(warnings);
        return result;
    }
    
    /**
     * 创建文件不存在结果
     */
    public static ConfigParseResult fileNotFound() {
        ConfigParseResult result = new ConfigParseResult();
        result.setStatus(Status.FILE_NOT_FOUND);
        return result;
    }
    
    /**
     * 创建解析错误结果
     */
    public static ConfigParseResult parseError(String error, String location) {
        ConfigParseResult result = new ConfigParseResult();
        result.setStatus(Status.PARSE_ERROR);
        result.setError(error);
        result.setErrorLocation(location);
        return result;
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 是否成功（包括部分成功）
     */
    public boolean isSuccess() {
        return status == Status.SUCCESS || status == Status.PARTIAL_SUCCESS;
    }
    
    /**
     * 是否有配置
     */
    public boolean hasConfig() {
        return config != null;
    }
    
    /**
     * 是否有警告
     */
    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }
    
    /**
     * 添加警告
     */
    public void addWarning(String warning) {
        if (warnings == null) {
            warnings = new ArrayList<>();
        }
        warnings.add(warning);
    }
}
