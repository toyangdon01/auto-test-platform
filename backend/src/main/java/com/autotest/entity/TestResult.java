package com.autotest.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 测试结果实体
 *
 * @author auto-test-platform
 */
@Data
@TableName(value = "test_results", autoResultMap = true)
public class TestResult implements Serializable {

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
     * 脚本ID
     */
    private Long scriptId;

    /**
     * 脚本版本
     */
    private String scriptVersion;

    /**
     * 结果: pass/fail/warning/error
     */
    private String result;

    /**
     * 结果原因
     */
    private String resultReason;

    /**
     * 综合得分（0-100）
     */
    private Integer overallScore;

    /**
     * 指标数据（JSON）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metrics;

    /**
     * 原始标准输出
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private String rawOutput;

    /**
     * 原始错误输出
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private String rawError;

    /**
     * 执行参数（JSON）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> parameters;

    /**
     * 退出码
     */
    private Integer exitCode;

    /**
     * 执行时长（毫秒）
     */
    private Long durationMs;

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
}
