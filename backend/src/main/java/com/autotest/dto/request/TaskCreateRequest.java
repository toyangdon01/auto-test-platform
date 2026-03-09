package com.autotest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 创建任务请求
 *
 * @author auto-test-platform
 */
@Data
public class TaskCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务名称
     */
    @NotBlank(message = "任务名称不能为空")
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 脚本ID
     */
    @NotNull(message = "脚本ID不能为空")
    private Long scriptId;

    /**
     * 脚本版本
     */
    @NotBlank(message = "脚本版本不能为空")
    private String scriptVersion;

    /**
     * 服务器ID列表
     */
    @NotEmpty(message = "服务器列表不能为空")
    private List<Long> serverIds;

    /**
     * 共享参数
     */
    private Map<String, Object> sharedParams;

    /**
     * 部署参数
     */
    private Map<String, Object> deployParams;

    /**
     * 执行参数
     */
    private Map<String, Object> runParams;

    /**
     * 生命周期配置
     */
    private LifecycleConfig lifecycleConfig;

    /**
     * 是否启用指标采集
     */
    private Boolean collectEnabled = true;

    /**
     * 指标采集配置
     */
    private Map<String, Object> collectConfig;

    /**
     * 执行模式: immediate/scheduled
     */
    @NotBlank(message = "执行模式不能为空")
    private String executionMode;

    /**
     * 定时执行时间
     */
    private String scheduledTime;

    /**
     * 并行模式: sequential/parallel
     */
    private String parallelMode = "sequential";

    /**
     * 最大并行数
     */
    private Integer maxParallel = 1;

    /**
     * 失败策略: continue/stop
     */
    private String failureStrategy = "continue";

    /**
     * 生命周期配置
     */
    @Data
    public static class LifecycleConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        private Boolean skipDeploy = false;
        private Boolean skipCleanup = false;
        private Integer deployTimeout = 600;
        private Integer cleanupTimeout = 300;
    }
}
