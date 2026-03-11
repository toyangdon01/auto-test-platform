package com.autotest.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资源文件实体
 */
@Data
@TableName("resource_files")
public class ResourceFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文件名称
     */
    private String name;

    /**
     * 平台存储路径
     */
    private String storagePath;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件类型：binary/rpm/tar/zip/config/other
     */
    private String fileType;

    /**
     * 分类：与脚本分类一致
     */
    private String category;

    /**
     * MD5校验值
     */
    private String checksum;

    /**
     * 描述
     */
    private String description;

    /**
     * 上传者
     */
    private String createdBy;

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
