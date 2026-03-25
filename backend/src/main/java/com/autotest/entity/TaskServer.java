package com.autotest.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
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
     * 整体状态
     */
    private String overallStatus;

    /**
     * 进度
     */
    private Integer progress;

    /**
     * 当前执行阶段
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

    // ==================== 部署阶段 ====================

    /**
     * 部署状态：pending/running/completed/failed/skipped
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
     * 部署输出日志
     */
    private String deployOutput;

    /**
     * 部署错误信息
     */
    private String deployError;

    // ==================== 执行阶段 ====================

    /**
     * 执行状态：pending/running/completed/failed
     */
    private String runStatus;

    /**
     * 执行开始时间
     */
    private LocalDateTime runStartedAt;

    /**
     * 执行结束时间
     */
    private LocalDateTime runFinishedAt;

    /**
     * 执行退出码
     */
    private Integer runExitCode;

    /**
     * 执行输出日志
     */
    private String runOutput;

    /**
     * 执行错误信息
     */
    private String runError;

    /**
     * 解析后的结果（JSON）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> parsedResult;

    // ==================== 卸载阶段 ====================

    /**
     * 卸载状态：pending/running/completed/failed/skipped
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
     * 卸载输出日志
     */
    private String cleanupOutput;

    /**
     * 卸载错误信息
     */
    private String cleanupError;
}
