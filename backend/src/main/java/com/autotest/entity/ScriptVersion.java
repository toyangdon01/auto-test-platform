package com.autotest.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
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
     * 文件列表（JSON）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
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
     * 变更日志
     */
    private String changeLog;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
