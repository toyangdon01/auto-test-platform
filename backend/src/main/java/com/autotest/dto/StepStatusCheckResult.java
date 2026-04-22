package com.autotest.dto;

import lombok.Data;

/**
 * 步骤状态检查结果
 */
@Data
public class StepStatusCheckResult {
    
    /**
     * 步骤状态: running, success, failed, cancelled
     */
    private String status;
    
    /**
     * 状态变更原因
     */
    private String reason;
    
    /**
     * 退出码（如果有）
     */
    private Integer exitCode;
    
    /**
     * 完整日志（用于后台步骤）
     */
    private String output;
    
    private StepStatusCheckResult() {}
    
    public static StepStatusCheckResult running() {
        StepStatusCheckResult result = new StepStatusCheckResult();
        result.setStatus("running");
        return result;
    }
    
    public static StepStatusCheckResult running(String reason) {
        StepStatusCheckResult result = new StepStatusCheckResult();
        result.setStatus("running");
        result.setReason(reason);
        return result;
    }
    
    public static StepStatusCheckResult success(String reason, String output) {
        StepStatusCheckResult result = new StepStatusCheckResult();
        result.setStatus("success");
        result.setReason(reason);
        result.setOutput(output);
        return result;
    }
    
    public static StepStatusCheckResult success(String reason, Integer exitCode, String output) {
        StepStatusCheckResult result = new StepStatusCheckResult();
        result.setStatus("success");
        result.setReason(reason);
        result.setExitCode(exitCode);
        result.setOutput(output);
        return result;
    }
    
    public static StepStatusCheckResult failed(String reason) {
        StepStatusCheckResult result = new StepStatusCheckResult();
        result.setStatus("failed");
        result.setReason(reason);
        return result;
    }
    
    public static StepStatusCheckResult failed(String reason, Integer exitCode) {
        StepStatusCheckResult result = new StepStatusCheckResult();
        result.setStatus("failed");
        result.setReason(reason);
        result.setExitCode(exitCode);
        return result;
    }
    
    public static StepStatusCheckResult cancelled(String reason) {
        StepStatusCheckResult result = new StepStatusCheckResult();
        result.setStatus("cancelled");
        result.setReason(reason);
        return result;
    }
    
    public boolean isStillRunning() {
        return "running".equals(status);
    }
}
