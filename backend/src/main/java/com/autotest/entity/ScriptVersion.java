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
 * 脚本版本实体
 *
 * @author auto-test-platform
 */
@Data
@TableName(value = "script_versions", autoResultMap = true)
public class ScriptVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 脚本ID
     */
    private Long scriptId;

    /**
     * 版本号
     */
    private String version;

    /**
     * 生命周期模式
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
     * 存储路径
     */
    private String storagePath;

    /**
     * 总大小（字节）
     */
    private Long totalSize;

    /**
     * 文件数量
     */
    private Integer fileCount;

    /**
     * 文件校验和
     */
    private String checksum;

    /**
     * 角色定义列表（JSONB）
     * 格式: [{"name": "server", "displayName": "被测服务", "entryFunction": "run_server", ...}, ...]
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> roles;

    /**
     * 输出收集配置（JSONB）
     * 格式: {"collectEnabled": true, "collectRules": [...]}
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> outputConfig;

    /**
     * 执行步骤配置（JSONB）
     * 格式: {"step_1": {"displayName": "...", "script": "...", "dependsOn": [], ...}, ...}
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> steps;

    /**
     * 变更日志
     */
    private String changeLog;

    /**
     * 脚本内容
     */
    private String content;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
