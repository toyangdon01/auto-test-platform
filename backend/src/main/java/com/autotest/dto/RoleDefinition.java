package com.autotest.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 角色定义DTO
 * 用于脚本版本中定义支持的角色
 *
 * @author auto-test-platform
 */
@Data
public class RoleDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 角色名称（唯一标识）
     */
    private String name;

    /**
     * 显示名称
     */
    private String displayName;

    /**
     * 角色描述
     */
    private String description;

    /**
     * 入口函数名
     */
    private String entryFunction;

    /**
     * 清理函数名
     */
    private String cleanupFunction;

    /**
     * 角色参数定义
     */
    private List<RoleParam> params;

    /**
     * 是否收集结果
     */
    private Boolean resultCollector;

    /**
     * 依赖的角色列表
     */
    private List<String> dependsOn;

    /**
     * 启动探测配置
     */
    private StartupProbe startupProbe;

    /**
     * 角色参数定义
     */
    @Data
    public static class RoleParam implements Serializable {
        private String name;
        private String type;
        private String label;
        private Object defaultValue;
        private Boolean required;
        private String suggestFromRole;  // 建议从哪个角色获取值
    }

    /**
     * 启动探测配置
     */
    @Data
    public static class StartupProbe implements Serializable {
        private String type;      // tcp, http, script
        private Integer port;     // TCP端口
        private String portParam; // 端口参数名，如 "${port}"
        private String url;       // HTTP URL
        private String script;    // 探测脚本
        private Integer timeout;  // 超时时间（秒）
        private Integer interval; // 探测间隔（秒）
        private Integer retries;  // 重试次数
    }
}
