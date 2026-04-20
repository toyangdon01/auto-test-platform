package com.autotest.dto.pipeline;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 任务 YAML 配置
 *
 * @author auto-test-platform
 */
@Data
public class TaskYamlConfig {
    /**
     * 任务名称
     */
    private String name;

    /**
     * 脚本名称（与 scriptId 二选一）
     */
    private String script;

    /**
     * 脚本ID
     */
    private Long scriptId;

    /**
     * 依赖任务名称列表
     */
    private List<String> dependsOn;

    /**
     * 超时时间（秒）
     */
    private Integer timeout;

    /**
     * 步骤服务器映射
     * key: 步骤名称
     * value: 服务器名称或ID列表
     */
    private Map<String, List<Object>> stepServerMapping;

    /**
     * 共享参数
     */
    private Map<String, Object> sharedParams;

    /**
     * 步骤参数
     */
    private Map<String, Map<String, Object>> stepParams;
}
