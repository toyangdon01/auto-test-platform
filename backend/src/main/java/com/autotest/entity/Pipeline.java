package com.autotest.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务编排实体
 */
@Data
@TableName("pipelines")
public class Pipeline {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    /**
     * 最大并行数(默认5)
     */
    private Integer maxParallel;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 创建者
     */
    private String createdBy;

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
