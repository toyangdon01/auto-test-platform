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

    // 脚本信息
    private ScriptInfo script;

    // 参数
    private Map<String, Object> sharedParams;

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
    
    // 统计信息
    private Integer totalServers;
    private Integer successCount;
    private Integer failCount;

    @Data
    public static class ScriptInfo {
        private Long id;
        private String name;
        private String version;
    }

    @Data
    public static class ServerProgress {
        private Long serverId;
        private String serverName;
        private String overallStatus;
        private Integer progress;
        
        // 角色信息
        private String role;
        private Map<String, Object> roleParams;
        
        // 当前执行信息
        private String currentPhase;
        private String currentCommand;
        private LocalDateTime commandStartedAt;
        
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
        private String output;
    }
}
