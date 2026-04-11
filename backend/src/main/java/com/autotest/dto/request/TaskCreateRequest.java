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
     * 服务器ID列表（本地执行时可以为空）
     */
    private List<Long> serverIds;

    /**
     * 是否包含本地执行
     */
    private Boolean isLocal;

    /**
     * 服务器角色配置（可选）
     * 如果不配置，所有服务器使用默认角色
     */
    private List<ServerRoleConfig> serverRoles;

    /**
     * 角色-服务器映射（新版格式）
     * 格式: { "server": [1, 2], "client": [1] }
     * 表示步骤 deploy 在服务器 1,2 上执行，步骤 test 在服务器 1 上执行
     * 注意：使用 List<Object> 因为 JSON 反序列化时数字可能是 Integer 或 Long
     */
    private Map<String, List<Object>> stepServerMapping;

    /**
     * 步骤参数
     * 格式: { "step_1": { "MYSQL_PORT": 3306 }, "step_2": { "WAREHOUSES": 10 } }
     */
    private Map<String, Map<String, Object>> stepParams;

    /**
     * 角色执行策略
     */
    private Map<String, Object> roleExecutionStrategy;

    /**
     * 共享参数
     */
    private Map<String, Object> sharedParams;

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
     * 超时时间（毫秒），默认 86400000（24小时）
     */
    private Integer timeout = 86400000;

}
