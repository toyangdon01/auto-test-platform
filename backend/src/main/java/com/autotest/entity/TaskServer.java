package com.autotest.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 任务服务器关联实体
 *
 * @author auto-test-platform
 */
@Data
@TableName(value = "task_servers", autoResultMap = true)
public class TaskServer implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 服务器ID
     */
    private Long serverId;

    /**
     * 服务器角色
     */
    private String role;

    /**
     * 角色特定参数（JSONB）
     */
    @TableField(typeHandler = com.autotest.handler.JsonbTypeHandler.class)
    private Map<String, Object> roleParams;

    /**
     * 部署状态
     */
    private String deployStatus;

    /**
     * 部署开始时间
     */
    private LocalDateTime deployStartedAt;

    /**
     * 部署结束时间
     */
    private LocalDateTime deployFinishedAt;

    /**
     * 部署退出码
     */
    private Integer deployExitCode;

    /**
     * 部署输出
     */
    private String deployOutput;

    /**
     * 部署错误
     */
    private String deployError;

    /**
     * 执行状态
     */
    private String runStatus;

    /**
     * 执行开始时间
     */
    private LocalDateTime startedAt;

    /**
     * 执行结束时间
     */
    private LocalDateTime finishedAt;

    /**
     * 执行退出码
     */
    private Integer exitCode;

    /**
     * 执行输出
     */
    private String output;

    /**
     * 执行错误
     */
    private String errorMessage;

    /**
     * 解析结果（JSON）
     */
    @TableField(typeHandler = com.autotest.handler.JsonbTypeHandler.class)
    private Map<String, Object> parsedResult;

    /**
     * 卸载状态
     */
    private String cleanupStatus;

    /**
     * 卸载开始时间
     */
    private LocalDateTime cleanupStartedAt;

    /**
     * 卸载结束时间
     */
    private LocalDateTime cleanupFinishedAt;

    /**
     * 卸载退出码
     */
    private Integer cleanupExitCode;

    /**
     * 卸载输出
     */
    private String cleanupOutput;

    /**
     * 卸载错误
     */
    private String cleanupError;

    /**
     * 整体状态
     */
    private String overallStatus;

    /**
     * 进度
     */
    private Integer progress;

    /**
     * 当前执行阶段: deploy/run/cleanup
     */
    private String currentPhase;

    /**
     * 当前执行的命令
     */
    private String currentCommand;

    /**
     * 当前命令开始时间
     */
    private LocalDateTime commandStartedAt;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
