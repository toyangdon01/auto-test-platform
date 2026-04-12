package com.autotest.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 编排执行记录
 */
@Data
@TableName("pipeline_runs")
public class PipelineRun {
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 编排ID
     */
    private Long pipelineId;

    /**
     * 编排名称(快照)
     */
    private String pipelineName;

    /**
     * 执行状态: pending, running, completed, failed, cancelled
     */
    private String status;

    /**
     * 开始时间
     */
    private LocalDateTime startedAt;

    /**
     * 结束时间
     */
    private LocalDateTime finishedAt;

    /**
     * 触发者
     */
    private String triggeredBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
