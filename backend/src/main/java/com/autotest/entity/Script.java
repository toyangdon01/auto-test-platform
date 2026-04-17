package com.autotest.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.autotest.handler.JsonbTypeHandler;
import com.autotest.handler.JsonbListTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "脚本信息")
public class Script implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "脚本ID", example = "1")
    private Long id;

    /**
     * 脚本名称
     */
    @Schema(description = "脚本名称，唯一标识", example = "mysql_performance_test", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    /**
     * 脚本类型: shell/python
     */
    @Schema(description = "脚本类型", example = "shell", allowableValues = {"shell", "python"})
    private String scriptType;

    /**
     * 测试类型
     */
    @Schema(description = "测试分类", example = "database", allowableValues = {"system", "database", "network", "performance", "functional"})
    private String testCategory;

    /**
     * 描述
     */
    @Schema(description = "脚本描述", example = "MySQL 数据库性能测试脚本")
    private String description;

    /**
     * 文件列表（非持久化，用于创建时传递）
     */
    @TableField(exist = false)
    @Schema(description = "文件列表，上传时返回")
    private List<Map<String, Object>> fileList;

    /**
     * 当前版本
     */
    @Schema(description = "当前版本号", example = "v1.0.0")
    private String currentVersion;

    /**
     * 默认超时（秒）
     */
    @Schema(description = "默认超时时间（秒）", example = "3600")
    private Integer defaultTimeout;

    /**
     * 是否内置
     */
    @Schema(description = "是否内置脚本", example = "false")
    private Boolean isBuiltin;

    /**
     * 状态: enabled/disabled
     */
    @Schema(description = "脚本状态", example = "enabled", allowableValues = {"enabled", "disabled"})
    private String status;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
    
    // ========== 非数据库字段（用于前端传输） ==========
    
    /**
     * 执行步骤配置（用于创建时传给 ScriptVersion，不存 scripts 表）
     */
    @TableField(exist = false)
    @Schema(description = "执行步骤配置，定义脚本的执行流程")
    private Map<String, Object> steps;
    
    /**
     * 共享参数定义（用于创建时传给 ScriptVersion，不存数据库）
     */
    @TableField(exist = false)
    @Schema(description = "参数定义列表，定义脚本可接受的参数")
    private java.util.List<Map<String, Object>> parameters;
    
    /**
     * 解析规则（非持久化，用于创建时传递）
     */
    @TableField(exist = false)
    @Schema(description = "结果解析规则，用于解析脚本输出")
    private Map<String, Object> parseRules;
    
    /**
     * 临时文件路径（上传时的临时目录，不存数据库）
     */
    @TableField(exist = false)
    @Schema(description = "临时文件路径，上传脚本后返回，用于创建脚本时关联文件")
    private String tempFilePath;
    
    public String getTempFilePath() {
        return tempFilePath;
    }
    
    public void setTempFilePath(String tempFilePath) {
        this.tempFilePath = tempFilePath;
    }
}
