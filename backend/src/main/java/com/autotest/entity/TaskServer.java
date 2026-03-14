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
}
