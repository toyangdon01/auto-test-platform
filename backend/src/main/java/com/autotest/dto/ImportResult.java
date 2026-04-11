package com.autotest.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 脚本包导入结果
 *
 * @author auto-test-platform
 */
@Data
public class ImportResult {
    
    /**
     * 总数
     */
    private int total;
    
    /**
     * 导入成功数量
     */
    private int imported;
    
    /**
     * 跳过数量
     */
    private int skipped;
    
    /**
     * 失败数量
     */
    private int failed;
    
    /**
     * 脚本导入状态列表
     */
    private List<ScriptImportStatus> scripts = new ArrayList<>();
    
    /**
     * 警告信息列表
     */
    private List<String> warnings = new ArrayList<>();
    
    /**
     * 错误信息列表（包级别错误）
     */
    private List<String> errors = new ArrayList<>();
    
    /**
     * 添加脚本状态
     */
    public void addScript(ScriptImportStatus status) {
        scripts.add(status);
        updateCounts();
    }
    
    /**
     * 添加导入成功
     */
    public void addImported(String name, Long id) {
        ScriptImportStatus status = new ScriptImportStatus();
        status.setName(name);
        status.setStatus("imported");
        status.setId(id);
        status.setMessage("导入成功");
        addScript(status);
    }
    
    /**
     * 添加导入成功（带消息）
     */
    public void addImported(String name, Long id, String message) {
        ScriptImportStatus status = new ScriptImportStatus();
        status.setName(name);
        status.setStatus("imported");
        status.setId(id);
        status.setMessage(message);
        addScript(status);
    }
    
    /**
     * 添加跳过
     */
    public void addSkipped(String name, String message) {
        ScriptImportStatus status = new ScriptImportStatus();
        status.setName(name);
        status.setStatus("skipped");
        status.setMessage(message);
        addScript(status);
    }
    
    /**
     * 添加失败
     */
    public void addFailed(String name, String error) {
        ScriptImportStatus status = new ScriptImportStatus();
        status.setName(name);
        status.setStatus("failed");
        status.setError(error);
        addScript(status);
    }
    
    /**
     * 添加警告
     */
    public void addWarning(String scriptName, String message) {
        warnings.add(scriptName + ": " + message);
    }
    
    /**
     * 添加错误
     */
    public void addError(String error) {
        errors.add(error);
    }
    
    /**
     * 是否有警告
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
    
    /**
     * 是否有错误
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
    
    /**
     * 更新计数
     */
    private void updateCounts() {
        imported = 0;
        skipped = 0;
        failed = 0;
        
        for (ScriptImportStatus status : scripts) {
            switch (status.getStatus()) {
                case "imported":
                    imported++;
                    break;
                case "skipped":
                    skipped++;
                    break;
                case "failed":
                    failed++;
                    break;
            }
        }
    }
}
