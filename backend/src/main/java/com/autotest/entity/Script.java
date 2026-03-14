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
     * 当前版本
     */
    private String currentVersion;

    /**
     * 生命周期模式: simple/full
     */
    private String lifecycleMode;

    /**
     * 是否包含部署阶段
     */
    private Boolean hasDeploy;

    /**
     * 是否包含卸载阶段
     */
    private Boolean hasCleanup;

    /**
     * 部署入口文件
     */
    private String deployEntry;

    /**
     * 卸载入口文件
     */
    private String cleanupEntry;

    /**
     * 入口文件
     */
    private String entryFile;

    /**
     * 文件列表（JSONB List）
     */
    @TableField(typeHandler = JsonbListTypeHandler.class)
    private List<Map<String, Object>> fileList;

    /**
     * 解析规则（JSONB Map）
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> parseRules;

    /**
     * 默认超时（秒）
     */
    private Integer defaultTimeout;

    /**
     * 默认重试次数
     */
    private Integer defaultRetry;

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
     * 执行脚本内容（在线编辑模式，不存数据库）
     */
    @TableField(exist = false)
    private String runContent;
    
    /**
     * 部署脚本内容（在线编辑模式，不存数据库）
     */
    @TableField(exist = false)
    private String deployContent;
    
    /**
     * 卸载脚本内容（在线编辑模式，不存数据库）
     */
    @TableField(exist = false)
    private String cleanupContent;
    
    /**
     * 角色定义（用于创建时传给 ScriptVersion，不存 scripts 表）
     */
    @TableField(exist = false)
    private Map<String, Object> roles;
    
    /**
     * 输出收集配置（用于创建时传给 ScriptVersion，不存 scripts 表）
     */
    @TableField(exist = false)
    private Map<String, Object> outputConfig;
    
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
