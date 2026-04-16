package com.autotest.dto.pipeline;

import lombok.Data;

import java.util.List;

/**
 * Pipeline YAML 配置
 *
 * @author auto-test-platform
 */
@Data
public class PipelineYamlConfig {
    /**
     * 编排名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 最大并行数
     */
    private Integer maxParallel = 5;

    /**
     * 服务器配置列表
     */
    private List<ServerYamlConfig> servers;

    /**
     * 任务配置列表
     */
    private List<TaskYamlConfig> tasks;
}
