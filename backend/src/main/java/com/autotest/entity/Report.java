package com.autotest.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.autotest.handler.JsonbTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 报告实体
 *
 * @author auto-test-platform
 */
@Data
@TableName(value = "reports", autoResultMap = true)
public class Report implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 报告标题
     */
    private String title;

    /**
     * 摘要
     */
    private String summary;

    /**
     * 结论: pass/fail/warning
     */
    private String conclusion;

    /**
     * 报告数据（JSON）
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> reportData;

    /**
     * 文件格式
     */
    private String fileFormat;

    /**
     * 文件路径
     */
    private String filePath;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
