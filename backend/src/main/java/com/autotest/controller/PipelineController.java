package com.autotest.controller;

import com.autotest.common.ApiResponse;
import com.autotest.entity.Pipeline;
import com.autotest.entity.PipelineRun;
import com.autotest.entity.PipelineTask;
import com.autotest.service.PipelineImportService;
import com.autotest.service.PipelineService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "pipelines", description = "流水线编排管理")
@RestController
@RequestMapping("/api/v1/pipelines")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineService pipelineService;
    private final PipelineImportService pipelineImportService;

    /**
     * 创建编排
     */
    @Operation(
        summary = "创建流水线编排",
        description = "创建新的流水线编排。\n" +
                     "\n**请求体说明：**\n" +
                     "- name: 编排名称\n" +
                     "- description: 描述\n" +
                     "- maxParallel: 最大并行任务数（默认5）\n" +
                     "- tasks: 任务列表，每个任务包含：\n" +
                     "  - name: 任务名称\n" +
                     "  - scriptId: 关联脚本ID\n" +
                     "  - serverIds: 目标服务器ID列表\n" +
                     "  - dependsOn: 依赖的任务ID列表\n" +
                     "  - timeout: 超时时间\n" +
                     "  - stepServerMapping: 步骤级别的服务器映射\n" +
                     "  - stepParams: 步骤级别的参数\n" +
                     "  - sharedParams: 共享参数"
    )
    @PostMapping
    public ApiResponse<Pipeline> createPipeline(@RequestBody Map<String, Object> body) {
        Pipeline pipeline = new Pipeline();
        pipeline.setName((String) body.get("name"));
        pipeline.setDescription((String) body.get("description"));
        pipeline.setMaxParallel(body.get("maxParallel") != null ? 
            ((Number) body.get("maxParallel")).intValue() : 5);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tasksData = (List<Map<String, Object>>) body.get("tasks");

        List<PipelineTask> tasks = null;
        if (tasksData != null) {
            tasks = tasksData.stream().map(t -> {
                PipelineTask task = new PipelineTask();
                task.setName((String) t.get("name"));
                task.setScriptId(((Number) t.get("scriptId")).longValue());
                if (t.get("serverIds") != null) {
                    task.setServerIds(t.get("serverIds").toString());
                }
                if (t.get("stepServerMapping") != null) {
                    task.setStepServerMapping(t.get("stepServerMapping").toString());
                }
                if (t.get("stepParams") != null) {
                    task.setStepParams(t.get("stepParams").toString());
                }
                if (t.get("sharedParams") != null) {
                    task.setSharedParams(t.get("sharedParams").toString());
                }
                if (t.get("timeout") != null) {
                    task.setTimeout(((Number) t.get("timeout")).longValue());
                }
                if (t.get("dependsOn") != null) {
                    task.setDependsOn(t.get("dependsOn").toString());
                }
                return task;
            }).toList();
        }

        return ApiResponse.success(pipelineService.createPipeline(pipeline, tasks));
    }

    /**
     * 分页查询编排
     */
    @Operation(summary = "获取流水线列表", description = "分页获取流水线列表，支持关键字搜索")
    @GetMapping
    public ApiResponse<Page<Pipeline>> listPipelines(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(pipelineService.listPipelines(page, size, keyword));
    }

    /**
     * 获取编排详情
     */
    @GetMapping("/{id}")
    public ApiResponse<Pipeline> getPipeline(@PathVariable Long id) {
        Pipeline pipeline = pipelineService.getPipelineById(id);
        if (pipeline == null) {
            return ApiResponse.error("Pipeline not found");
        }
        return ApiResponse.success(pipeline);
    }

    /**
     * 更新编排
     */
    @Operation(summary = "更新流水线", description = "更新流水线配置，包括任务列表和依赖关系")
    @PutMapping("/{id}")
    public ApiResponse<Pipeline> updatePipeline(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Pipeline pipeline = new Pipeline();
        pipeline.setName((String) body.get("name"));
        pipeline.setDescription((String) body.get("description"));
        pipeline.setMaxParallel(body.get("maxParallel") != null ? 
            ((Number) body.get("maxParallel")).intValue() : 5);
        pipeline.setEnabled((Boolean) body.getOrDefault("enabled", true));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tasksData = (List<Map<String, Object>>) body.get("tasks");

        List<PipelineTask> tasks = null;
        if (tasksData != null) {
            tasks = tasksData.stream().map(t -> {
                PipelineTask task = new PipelineTask();
                task.setName((String) t.get("name"));
                task.setScriptId(((Number) t.get("scriptId")).longValue());
                if (t.get("serverIds") != null) {
                    task.setServerIds(t.get("serverIds").toString());
                }
                if (t.get("stepServerMapping") != null) {
                    task.setStepServerMapping(t.get("stepServerMapping").toString());
                }
                if (t.get("stepParams") != null) {
                    task.setStepParams(t.get("stepParams").toString());
                }
                if (t.get("sharedParams") != null) {
                    task.setSharedParams(t.get("sharedParams").toString());
                }
                if (t.get("timeout") != null) {
                    task.setTimeout(((Number) t.get("timeout")).longValue());
                }
                if (t.get("dependsOn") != null) {
                    task.setDependsOn(t.get("dependsOn").toString());
                }
                return task;
            }).toList();
        }

        return ApiResponse.success(pipelineService.updatePipeline(id, pipeline, tasks));
    }

    /**
     * 删除编排
     */
    @Operation(summary = "删除流水线", description = "删除流水线及其所有任务配置")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePipeline(@PathVariable Long id) {
        pipelineService.deletePipeline(id);
        return ApiResponse.success(null);
    }

    /**
     * 获取编排任务列表
     */
    @GetMapping("/{id}/tasks")
    public ApiResponse<List<PipelineTask>> getPipelineTasks(@PathVariable Long id) {
        return ApiResponse.success(pipelineService.getPipelineTasks(id));
    }

    /**
     * 添加编排任务
     */
    @PostMapping("/{id}/tasks")
    public ApiResponse<PipelineTask> addPipelineTask(@PathVariable Long id, @RequestBody PipelineTask task) {
        return ApiResponse.success(pipelineService.addPipelineTask(id, task));
    }

    /**
     * 执行编排
     */
    @Operation(
        summary = "执行流水线",
        description = "触发流水线执行。\n" +
                     "\n**执行流程：**\n" +
                     "1. 创建执行记录（PipelineRun）\n" +
                     "2. 按依赖关系调度任务\n" +
                     "3. 并行执行无依赖的任务\n" +
                     "4. 收集每个任务的执行结果"
    )
    @PostMapping("/{id}/execute")
    public ApiResponse<PipelineRun> executePipeline(
            @PathVariable Long id,
            @RequestBody(required = false) Map<Long, List<Long>> serverMapping) {
        return ApiResponse.success(pipelineService.executePipeline(id, serverMapping));
    }

    /**
     * 获取执行记录列表
     */
    @GetMapping("/runs")
    public ApiResponse<Page<PipelineRun>> listPipelineRuns(
            @RequestParam(required = false) Long pipelineId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(pipelineService.listPipelineRuns(pipelineId, page, size));
    }

    /**
     * 获取执行详情
     */
    @GetMapping("/runs/{runId}")
    public ApiResponse<PipelineRun> getPipelineRun(@PathVariable Long runId) {
        PipelineRun run = pipelineService.getPipelineRunById(runId);
        if (run == null) {
            return ApiResponse.error("PipelineRun not found");
        }
        return ApiResponse.success(run);
    }

    /**
     * 获取执行任务列表
     */
    @GetMapping("/runs/{runId}/tasks")
    public ApiResponse<List<Map<String, Object>>> getPipelineRunTasks(@PathVariable Long runId) {
        return ApiResponse.success(pipelineService.getPipelineRunTasks(runId));
    }

    /**
     * 取消执行
     */
    @PostMapping("/runs/{runId}/cancel")
    public ApiResponse<Void> cancelPipelineRun(@PathVariable Long runId) {
        pipelineService.cancelPipelineRun(runId);
        return ApiResponse.success(null);
    }

    // ==================== YAML 导入导出 ====================

    /**
     * 从 YAML 字符串导入编排
     */
    @Operation(
        summary = "从 YAML 导入流水线",
        description = "从 YAML 格式字符串导入流水线配置。\n" +
                     "\n**YAML 格式示例：**\n" +
                     "```yaml\n" +
                     "name: my-pipeline\n" +
                     "max_parallel: 3\n" +
                     "tasks:\n" +
                     "  - name: task1\n" +
                     "    script: script_name\n" +
                     "    servers: [1, 2, 3]\n" +
                     "```"
    )
    @PostMapping("/import")
    public ApiResponse<Map<String, Object>> importFromYaml(@RequestBody String yamlContent) {
        Pipeline pipeline = pipelineImportService.importFromYaml(yamlContent);
        
        // 获取任务数量
        List<PipelineTask> tasks = pipelineService.getPipelineTasks(pipeline.getId());
        
        Map<String, Object> result = new HashMap<>();
        result.put("id", pipeline.getId());
        result.put("name", pipeline.getName());
        result.put("description", pipeline.getDescription());
        result.put("maxParallel", pipeline.getMaxParallel());
        result.put("enabled", pipeline.getEnabled());
        result.put("createdAt", pipeline.getCreatedAt());
        result.put("taskCount", tasks.size());
        
        return ApiResponse.success(result);
    }

    /**
     * 从文件上传导入编排
     */
    @PostMapping("/import/file")
    public ApiResponse<Pipeline> importFromFile(@RequestParam("file") MultipartFile file) {
        try {
            String yamlContent = new String(file.getBytes(), StandardCharsets.UTF_8);
            return ApiResponse.success(pipelineImportService.importFromYaml(yamlContent));
        } catch (Exception e) {
            return ApiResponse.error("文件读取失败: " + e.getMessage());
        }
    }

    /**
     * 导出编排为 YAML
     */
    @Operation(summary = "导出流水线为 YAML", description = "将流水线配置导出为 YAML 格式字符串")
    @GetMapping("/{id}/export")
    public ApiResponse<String> exportToYaml(@PathVariable Long id) {
        return ApiResponse.success(pipelineImportService.exportToYaml(id));
    }
}
