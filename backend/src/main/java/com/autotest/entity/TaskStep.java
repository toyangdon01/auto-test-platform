package com.autotest.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 任务步骤实体
 */
@Data
@TableName(value = "task_steps", autoResultMap = true)
public class TaskStep {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long taskId;
    
    private Long serverId;
    
    private String stepName;
    
    private String displayName;
    
    /**
     * 执行的脚本文件
     */
    private String script;
    
    /**
     * 依赖的步骤（逗号分隔）
     */
    private String dependsOn;
    
    /**
     * 步骤参数
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> params;
    
    /**
     * 步骤状态: pending, waiting, running, success, failed, skipped
     */
    private String status;
    
    /**
     * 等待原因
     */
    private String waitReason;
    
    /**
     * 开始时间
     */
    private LocalDateTime startedAt;
    
    /**
     * 结束时间
     */
    private LocalDateTime finishedAt;
    
    /**
     * 退出码
     */
    private Integer exitCode;
    
    /**
     * 输出内容
     */
    private String output;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 是否收集结果
     */
    private Boolean resultCollector;
    
    /**
     * 解析后的结果
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> parsedResult;
    
    /**
     * 启动探测配置
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> startupProbe;
    
    /**
     * 探测状态
     */
    private String probeStatus;
    
    /**
     * 探测开始时间
     */
    private LocalDateTime probeStartedAt;
    
    /**
     * 探测结束时间
     */
    private LocalDateTime probeFinishedAt;
    
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
    
    /**
     * 关联的服务器信息（非持久化）
     */
    @TableField(exist = false)
    private Server server;
    
    /**
     * 服务器名称（非持久化，用于前端展示）
     */
    @TableField(exist = false)
    private String serverName;
    
    /**
     * 服务器主机地址（非持久化）
     */
    @TableField(exist = false)
    private String serverHost;
}
