package com.autotest.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 脚本导入状态
 *
 * @author auto-test-platform
 */
@Data
public class ScriptImportStatus {
    
    /**
     * 脚本名称
     */
    private String name;
    
    /**
     * 状态：imported | skipped | failed
     */
    private String status;
    
    /**
     * 导入后的脚本 ID
     */
    private Long id;
    
    /**
     * 消息
     */
    private String message;
    
    /**
     * 错误详情（失败时）
     */
    private String error;
    
    /**
     * 警告列表
     */
    private List<String> warnings = new ArrayList<>();
    
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
