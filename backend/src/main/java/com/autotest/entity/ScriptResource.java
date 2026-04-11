package com.autotest.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 脚本资源关联实体
 */
@Data
@TableName("script_resources")
public class ScriptResource {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 脚本ID
     */
    private Long scriptId;

    /**
     * 资源文件ID
     */
    private Long resourceId;

    /**
     * 目标路径
     */
    private String targetPath;

    /**
     * 文件权限
     */
    private String permissions;

    /**
     * 上传顺序
     */
    private Integer uploadOrder;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 关联的资源文件（非持久化）
     */
    @TableField(exist = false)
    private ResourceFile resource;
}
