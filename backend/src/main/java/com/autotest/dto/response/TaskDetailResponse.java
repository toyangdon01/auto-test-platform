package com.autotest.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 任务详情响应
 *
 * @author auto-test-platform
 */
@Data
public class TaskDetailResponse {

    private Long id;
    private String name;
    private String description;
    private Long scriptId;
    private String scriptName;
    private String scriptVersion;
    private String status;
    private Integer progress;
    private String lifecycleMode;

    // 脚本信息
    private ScriptInfo script;

    // 生命周期配置
    private LifecycleConfig lifecycleConfig;

    // 生命周期汇总
    private LifecycleSummary lifecycleSummary;

    // 参数
    private Map<String, Object> sharedParams;
    private Map<String, Object> deployParams;
    private Map<String, Object> runParams;

    // 服务器详情
    private List<ServerProgress> servers;

    // 执行配置
    private String executionMode;
    private LocalDateTime scheduledTime;
    private String parallelMode;
    private Integer maxParallel;
    private String failureStrategy;
    private Boolean collectEnabled;

    // 时间信息
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    @Data
    public static class ScriptInfo {
        private Long id;
        private String name;
        private String version;
        private String lifecycleMode;
        private Boolean hasDeploy;
        private Boolean hasCleanup;
    }

    @Data
    public static class LifecycleConfig {
        private Boolean skipDeploy;
        private Boolean skipCleanup;
        private Integer deployTimeout;
        private Integer cleanupTimeout;
    }

    @Data
    public static class LifecycleSummary {
        private StageSummary deploy;
        private StageSummary run;
        private StageSummary cleanup;
    }

    @Data
    public static class StageSummary {
        private String status;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private Integer duration;
        private Integer successCount;
        private Integer failedCount;
    }

    @Data
    public static class ServerProgress {
        private Long serverId;
        private String serverName;
        private String overallStatus;
        private StageDetail deploy;
        private StageDetail run;
        private StageDetail cleanup;
    }

    @Data
    public static class StageDetail {
        private String status;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private Integer duration;
        private Integer exitCode;
    }
}
