package com.autotest.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务服务器关联实体
 *
 * @author auto-test-platform
 */
@Data
@TableName("task_servers")
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
     * 部署状态
     */
    private String deployStatus;

    /**
     * 部署输出
     */
    private String deployOutput;

    /**
     * 部署错误
     */
    private String deployError;

    /**
     * 部署退出码
     */
    private Integer deployExitCode;

    /**
     * 执行状态
     */
    private String runStatus;

    /**
     * 执行输出
     */
    private String runOutput;

    /**
     * 执行错误
     */
    private String runError;

    /**
     * 执行退出码
     */
    private Integer runExitCode;

    /**
     * 卸载状态
     */
    private String cleanupStatus;

    /**
     * 卸载输出
     */
    private String cleanupOutput;

    /**
     * 卸载错误
     */
    private String cleanupError;

    /**
     * 卸载退出码
     */
    private Integer cleanupExitCode;

    /**
     * 整体状态
     */
    private String overallStatus;

    /**
     * 开始时间
     */
    private LocalDateTime startedAt;

    /**
     * 结束时间
     */
    private LocalDateTime finishedAt;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
