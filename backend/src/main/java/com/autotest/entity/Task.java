package com.autotest.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.autotest.handler.JsonbTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 任务实体
 *
 * @author auto-test-platform
 */
@Data
@TableName(value = "tasks", autoResultMap = true)
public class Task implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 任务名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 脚本ID
     */
    private Long scriptId;

    /**
     * 脚本版本
     */
    private String scriptVersion;

    /**
     * 共享参数（JSON）
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> sharedParams;

    /**
     * 步骤参数（JSONB）
     * 格式: {stepName: {paramName: paramValue}}
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Map<String, Object>> stepParams;

    /**
     * 角色执行策略（JSON）
     * 格式: {"mode": "ordered", "startupWait": true, "resultRoles": ["client"]}
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> roleExecutionStrategy;

    /**
     * 任务状态
     */
    private String status;

    /**
     * 执行模式: immediate/scheduled
     */
    private String executionMode;

    /**
     * 定时执行时间
     */
    private LocalDateTime scheduledTime;

    /**
     * 并行模式: sequential/parallel
     */
    private String parallelMode;

    /**
     * 最大并行数
     */
    private Integer maxParallel;

    /**
     * 失败策略: continue/stop
     */
    private String failureStrategy;

    /**
     * 是否启用指标采集
     */
    private Boolean collectEnabled;

    /**
     * 指标采集配置（JSON）
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> collectConfig;

    /**
     * 超时时间（毫秒），默认 300000（5分钟）
     */
    private Integer timeout;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 开始时间
     */
    private LocalDateTime startedAt;

    /**
     * 结束时间
     */
    private LocalDateTime finishedAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // === 非持久化字段（用于展示统计） ===

    /**
     * 服务器总数
     */
    @TableField(exist = false)
    private Integer serverCount;

    /**
     * 成功数
     */
    @TableField(exist = false)
    private Integer successCount;

    /**
     * 失败数
     */
    @TableField(exist = false)
    private Integer failCount;

    /**
     * 执行中数
     */
    @TableField(exist = false)
    private Integer runningCount;

    /**
     * 脚本名称（非持久化）
     */
    @TableField(exist = false)
    private String scriptName;
}
