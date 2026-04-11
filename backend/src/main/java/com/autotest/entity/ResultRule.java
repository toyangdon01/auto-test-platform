package com.autotest.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 测试结果解析规则
 */
@Data
@TableName("result_rules")
public class ResultRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联脚本ID（NULL表示全局规则）
     */
    private Long scriptId;

    /**
     * 规则名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 解析方式: builtin=内置规则, script=解析脚本
     */
    private String parserType;

    /**
     * 内置规则格式: key_value, json
     */
    private String builtinFormat;

    /**
     * 脚本来源: package=从脚本包选择, inline=直接编写
     */
    private String scriptSource;

    /**
     * 脚本包中的路径
     */
    private String scriptPath;

    /**
     * 内联脚本内容
     */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private String scriptContent;

    /**
     * 脚本语言: python, shell
     */
    private String scriptLanguage;

    /**
     * 输入来源: stdout=标准输出, file=指定文件
     */
    private String inputSource;

    /**
     * 文件路径正则表达式
     */
    private String filePattern;

    /**
     * 输出格式: json, csv
     */
    private String outputFormat;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 优先级（用于规则排序）
     */
    private Integer priority;

    /**
     * 规则配置（JSON格式，用于旧的判定规则）
     */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private Map<String, Object> rules;

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
