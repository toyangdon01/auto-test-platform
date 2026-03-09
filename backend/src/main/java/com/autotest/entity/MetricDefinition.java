package com.autotest.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 指标定义实体
 *
 * @author auto-test-platform
 */
@Data
@TableName(value = "metric_definitions", autoResultMap = true)
public class MetricDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 指标名称（唯一标识）
     */
    private String name;

    /**
     * 显示名称
     */
    private String displayName;

    /**
     * 分类
     */
    private String category;

    /**
     * 单位
     */
    private String unit;

    /**
     * 描述
     */
    private String description;

    /**
     * 基准线配置（JSON）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> baselineConfig;

    /**
     * 对比模式: higher_better/lower_better
     */
    private String comparisonMode;

    /**
     * 适用的测试类型（JSON数组）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> applicableCategories;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
