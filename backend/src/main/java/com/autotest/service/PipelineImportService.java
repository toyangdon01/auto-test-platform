package com.autotest.service;

import com.autotest.entity.Pipeline;

/**
 * Pipeline 导入服务接口
 *
 * @author auto-test-platform
 */
public interface PipelineImportService {

    /**
     * 从 YAML 字符串导入 Pipeline
     *
     * @param yamlContent YAML 内容
     * @return 创建的 Pipeline
     */
    Pipeline importFromYaml(String yamlContent);

    /**
     * 用 YAML 内容整体替换已有 Pipeline（任务列表按删除重建，pipelineTaskId 会变）
     *
     * @param pipelineId  已有编排 id
     * @param yamlContent YAML 内容
     * @return 更新后的 Pipeline
     */
    Pipeline updateFromYaml(Long pipelineId, String yamlContent);

    /**
     * 导出 Pipeline 为 YAML
     *
     * @param pipelineId Pipeline ID
     * @return YAML 内容
     */
    String exportToYaml(Long pipelineId);
}
