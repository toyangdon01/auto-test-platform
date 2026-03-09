package com.autotest.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 指标数据实体
 *
 * @author auto-test-platform
 */
@Data
@TableName(value = "metrics", autoResultMap = true)
public class Metric implements Serializable {

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
     * 时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 指标类型
     */
    private String metricType;

    /**
     * 指标名称
     */
    private String metricName;

    /**
     * 指标值
     */
    private Double value;

    /**
     * 单位
     */
    private String unit;

    /**
     * 标签（JSON）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> tags;
}
