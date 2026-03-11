package com.autotest.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统配置实体
 *
 * @author auto-test-platform
 */
@Data
@TableName("system_config")
public class SystemConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 配置键（主键）
     */
    @TableId(value = "key", type = IdType.INPUT)
    private String key;

    /**
     * 配置值
     */
    @TableField("value")
    private String value;

    /**
     * 描述
     */
    private String description;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
