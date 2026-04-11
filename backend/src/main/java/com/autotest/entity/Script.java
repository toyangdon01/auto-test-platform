package com.autotest.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.autotest.handler.JsonbTypeHandler;
import com.autotest.handler.JsonbListTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 脚本实体
 *
 * @author auto-test-platform
 */
@Data
@TableName(value = "scripts", autoResultMap = true)
public class Script implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 脚本名称
     */
    private String name;

    /**
     * 脚本类型: shell/python
     */
    private String scriptType;

    /**
     * 测试类型
     */
    private String testCategory;

    /**
     * 描述
     */
    private String description;

    /**
     * 文件列表（非持久化，用于创建时传递）
     */
    @TableField(exist = false)
    private List<Map<String, Object>> fileList;

    /**
     * 当前版本
     */
    private String currentVersion;

    /**
     * 默认超时（秒）
     */
    private Integer defaultTimeout;

    /**
     * 是否内置
     */
    private Boolean isBuiltin;

    /**
     * 状态: enabled/disabled
     */
    private String status;

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
    
    // ========== 非数据库字段（用于前端传输） ==========
    
    /**
     * 执行步骤配置（用于创建时传给 ScriptVersion，不存 scripts 表）
     */
    @TableField(exist = false)
    private Map<String, Object> steps;
    
    /**
     * 共享参数定义（用于创建时传给 ScriptVersion，不存 scripts 表）
     */
    @TableField(exist = false)
    private java.util.List<Map<String, Object>> parameters;
    
    /**
     * 解析规则（非持久化，用于创建时传递）
     */
    @TableField(exist = false)
    private Map<String, Object> parseRules;
    
    /**
     * 临时文件路径（上传时的临时目录，不存数据库）
     */
    @TableField(exist = false)
    private String tempFilePath;
    
    public String getTempFilePath() {
        return tempFilePath;
    }
    
    public void setTempFilePath(String tempFilePath) {
        this.tempFilePath = tempFilePath;
    }
}
