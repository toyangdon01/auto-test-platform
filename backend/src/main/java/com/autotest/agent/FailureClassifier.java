package com.autotest.agent;

import com.autotest.entity.TaskStep;

/**
 * 步骤失败归因：把「平台故障 / 环境故障 / 被测失败」分开，供智能体决定是重试还是改参数。
 */
public final class FailureClassifier {

    private FailureClassifier() {
    }

    /**
     * @param reasonCode        机器可读的失败分类
     * @param failurePhase      preparing / uploading / running / collecting
     * @param retryable         是否值得原样重试
     * @param retryAfterSeconds 重试前建议等待的秒数，null 表示无需等待
     */
    public record Verdict(String reasonCode, String failurePhase, boolean retryable, Integer retryAfterSeconds) {

        static final Verdict NONE = new Verdict("NONE", null, false, null);
    }

    public static Verdict classify(TaskStep step) {
        String status = step.getStatus();
        if (status == null || "pending".equals(status) || "success".equals(status) || "skipped".equals(status)) {
            return Verdict.NONE;
        }

        String err = step.getErrorMessage() == null ? "" : step.getErrorMessage();
        String phase = step.getCommand() == null ? "uploading" : "running";

        if (err.startsWith("创建工作目录失败")) {
            return new Verdict("WORKDIR_FAILED", "preparing", true, null);
        }
        if (err.startsWith("脚本上传失败") || err.startsWith("步骤资源上传失败")) {
            return new Verdict("UPLOAD_FAILED", "uploading", true, null);
        }
        if (err.contains("Auth fail") || err.contains("auth fail")) {
            // 客户端无法区分“密码错”和“被 faillock 锁定”，两者报同一个错；先按可等待重试处理
            return new Verdict("SSH_AUTH_FAILED", phase, true, 65);
        }
        if (err.contains("Connection refused") || err.contains("UnknownHostException")
            || err.contains("connect timed out") || err.contains("Connection reset")) {
            return new Verdict("HOST_UNREACHABLE", phase, true, null);
        }
        if (err.contains("NORMAL_TIMEOUT") || err.contains("Timeout after")) {
            return new Verdict("RUN_TIMEOUT", "running", false, null);
        }
        if (err.contains("SESSION_NOT_FOUND") || err.contains("SESSION_DEAD")
            || err.contains("BACKGROUND_PROCESS_STOPPED")) {
            return new Verdict("PROCESS_DIED", phase, true, null);
        }
        if (step.getExitCode() != null && step.getExitCode() != 0) {
            return new Verdict("NON_ZERO_EXIT", "running", false, null);
        }
        if ("failed".equals(status)) {
            return new Verdict("INTERNAL", phase, false, null);
        }
        return Verdict.NONE;
    }
}
