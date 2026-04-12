package com.autotest.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 编排执行任务关联
 */
@Data
@TableName("pipeline_run_tasks")
public class PipelineRunTask {
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 执行记录ID
     */
    private Long pipelineRunId;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 执行状态
     */
    private String status;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
