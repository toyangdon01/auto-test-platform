package com.autotest.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 服务器实体
 *
 * @author auto-test-platform
 */
@Data
@TableName(value = "servers", autoResultMap = true)
public class Server implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 服务器名称
     */
    private String name;

    /**
     * 主机地址
     */
    private String host;

    /**
     * SSH端口
     */
    private Integer port;

    /**
     * 用户名
     */
    private String username;

    /**
     * 认证类型: password/ssh_key
     */
    private String authType;

    /**
     * 认证密钥（加密存储）
     */
    private String authSecret;

    /**
     * 操作系统类型
     */
    private String osType;

    /**
     * 操作系统版本
     */
    private String osVersion;

    /**
     * CPU核心数
     */
    private Integer cpuCores;

    /**
     * CPU型号
     */
    private String cpuModel;

    /**
     * 内存大小（文本展示）
     */
    private String memorySize;

    /**
     * 内存大小（MB）
     */
    private Long memoryTotalMb;

    /**
     * 磁盘信息（JSON）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> diskInfo;

    /**
     * 分组ID
     */
    private Long groupId;

    /**
     * 标签（JSON数组）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    /**
     * 备注
     */
    private String remark;

    /**
     * 状态: online/offline/maintenance
     */
    private String status;

    /**
     * 最后检查时间
     */
    private LocalDateTime lastCheckAt;

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
