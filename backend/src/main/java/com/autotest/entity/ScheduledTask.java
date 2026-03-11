package com.autotest.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 定时任务实体
 *
 * @author auto-test-platform
 */
@Data
@TableName("scheduled_tasks")
public class ScheduledTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 任务名称
     */
    private String name;

    /**
     * 关联的测试任务ID
     */
    private Long taskId;

    /**
     * Cron 表达式
     */
    private String cronExpression;

    /**
     * 执行类型: cron/interval/once
     */
    private String scheduleType;

    /**
     * 执行间隔（分钟）
     */
    private Integer intervalMinutes;

    /**
     * 下次执行时间
     */
    private LocalDateTime nextRunTime;

    /**
     * 上次执行时间
     */
    private LocalDateTime lastRunTime;

    /**
     * 执行状态: enabled/disabled/running
     */
    private String status;

    /**
     * 执行参数（JSON）
     */
    @TableField(typeHandler = com.autotest.handler.JsonbTypeHandler.class)
    private Map<String, Object> parameters;

    /**
     * 执行次数
     */
    private Integer runCount;

    /**
     * 失败次数
     */
    private Integer failCount;

    /**
     * 备注
     */
    private String remark;

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
