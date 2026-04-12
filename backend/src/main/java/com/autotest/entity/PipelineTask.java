package com.autotest.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 编排任务配置实体
 */
@Data
@TableName("pipeline_tasks")
public class PipelineTask {
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 编排ID
     */
    private Long pipelineId;

    /**
     * 任务名称
     */
    private String name;

    /**
     * 脚本ID
     */
    private Long scriptId;

    /**
     * 执行顺序
     */
    private Integer orderNum;

    /**
     * 服务器ID列表(JSON)
     */
    private String serverIds;

    /**
     * 步骤服务器映射(JSON): {"stepName": [serverId1, serverId2]}
     */
    private String stepServerMapping;

    /**
     * 步骤参数(JSON): {"stepName": {"param1": "value1"}}
     */
    private String stepParams;

    /**
     * 共享参数(JSON): {"param1": "value1"}
     */
    private String sharedParams;

    /**
     * 超时时间(毫秒)
     */
    private Long timeout;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 依赖的任务ID(用于DAG模式)
     */
    private String dependsOn;

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
