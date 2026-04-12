package com.autotest.service;

import com.autotest.entity.Pipeline;
import com.autotest.entity.PipelineRun;
import com.autotest.entity.PipelineTask;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;
import java.util.Map;

public interface PipelineService {
    /**
     * 创建编排
     */
    Pipeline createPipeline(Pipeline pipeline, List<PipelineTask> tasks);

    /**
     * 分页查询编排
     */
    Page<Pipeline> listPipelines(int page, int size, String keyword);

    /**
     * 获取编排详情
     */
    Pipeline getPipelineById(Long id);

    /**
     * 更新编排
     */
    Pipeline updatePipeline(Long id, Pipeline pipeline, List<PipelineTask> tasks);

    /**
     * 删除编排
     */
    void deletePipeline(Long id);

    /**
     * 获取编排任务列表
     */
    List<PipelineTask> getPipelineTasks(Long pipelineId);

    /**
     * 添加编排任务
     */
    PipelineTask addPipelineTask(Long pipelineId, PipelineTask task);

    /**
     * 更新编排任务
     */
    PipelineTask updatePipelineTask(Long pipelineId, Long taskId, PipelineTask task);

    /**
     * 删除编排任务
     */
    void deletePipelineTask(Long pipelineId, Long taskId);

    /**
     * 执行编排
     */
    PipelineRun executePipeline(Long pipelineId, Map<Long, List<Long>> serverMapping);

    /**
     * 获取执行记录
     */
    Page<PipelineRun> listPipelineRuns(Long pipelineId, int page, int size);

    /**
     * 获取执行详情
     */
    PipelineRun getPipelineRunById(Long runId);

    /**
     * 获取执行任务列表
     */
    List<Map<String, Object>> getPipelineRunTasks(Long runId);

    /**
     * 取消执行
     */
    void cancelPipelineRun(Long runId);
}
