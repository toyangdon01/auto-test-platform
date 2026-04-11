package com.autotest.dto;

import com.autotest.entity.Script;
import lombok.Data;

import java.util.List;

/**
 * 脚本上传/创建结果
 * 包含脚本信息和配置解析警告
 * 
 * @author auto-test-platform
 */
@Data
public class ScriptUploadResult {
    
    /**
     * 创建的脚本
     */
    private Script script;
    
    /**
     * 配置解析状态
     */
    private String configStatus;
    
    /**
     * 警告信息列表
     */
    private List<String> warnings;
    
    /**
     * 错误信息（解析失败时）
     */
    private String error;
    
    /**
     * 错误位置（解析失败时）
     */
    private String errorLocation;
    
    /**
     * 从脚本和解析结果创建
     */
    public static ScriptUploadResult of(Script script, ConfigParseResult parseResult) {
        ScriptUploadResult result = new ScriptUploadResult();
        result.setScript(script);
        
        if (parseResult == null) {
            result.setConfigStatus("no_config");
            return result;
        }
        
        switch (parseResult.getStatus()) {
            case SUCCESS:
                result.setConfigStatus("success");
                break;
            case FILE_NOT_FOUND:
                result.setConfigStatus("no_config");
                break;
            case PARTIAL_SUCCESS:
                result.setConfigStatus("success_with_warnings");
                result.setWarnings(parseResult.getWarnings());
                break;
            case PARSE_ERROR:
                result.setConfigStatus("error");
                result.setError(parseResult.getError());
                result.setErrorLocation(parseResult.getErrorLocation());
                break;
        }
        
        return result;
    }
}
