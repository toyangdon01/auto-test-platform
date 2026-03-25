package com.autotest.controller;

import com.autotest.common.ApiResponse;
import com.autotest.common.PageResult;
import com.autotest.dto.request.TaskCreateRequest;
import com.autotest.dto.request.TaskQueryRequest;
import com.autotest.dto.response.TaskDetailResponse;
import com.autotest.entity.Task;
import com.autotest.entity.TaskStep;
import com.autotest.entity.Server;
import com.autotest.entity.Metric;
import com.autotest.mapper.MetricMapper;
import com.autotest.mapper.TaskStepMapper;
import com.autotest.mapper.ServerMapper;
import com.autotest.service.TaskService;
import com.autotest.service.SshService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 任务管理控制器
 *
 * @author auto-test-platform
 */
@Tag(name = "tasks", description = "任务管理")
@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final MetricMapper metricMapper;
    private final TaskStepMapper taskStepMapper;
    private final ServerMapper serverMapper;

    @Operation(summary = "获取任务列表")
    @GetMapping
    public ApiResponse<PageResult<Task>> listTasks(TaskQueryRequest request) {
        return ApiResponse.success(taskService.listTasks(request));
    }

    @Operation(summary = "获取任务详情")
    @GetMapping("/{id}")
    public ApiResponse<TaskDetailResponse> getTask(@PathVariable Long id) {
        return ApiResponse.success(taskService.getTaskDetail(id));
    }

    @Operation(summary = "创建任务")
    @PostMapping
    public ApiResponse<Task> createTask(@Valid @RequestBody TaskCreateRequest request) {
        return ApiResponse.success(taskService.createTask(request));
    }

    @Operation(summary = "更新任务")
    @PutMapping("/{id}")
    public ApiResponse<Task> updateTask(@PathVariable Long id, @Valid @RequestBody TaskCreateRequest request) {
        return ApiResponse.success(taskService.updateTask(id, request));
    }

    @Operation(summary = "删除任务")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ApiResponse.success();
    }

    @Operation(summary = "执行任务")
    @PostMapping("/{id}/execute")
    public ApiResponse<Void> executeTask(@PathVariable Long id) {
        taskService.executeTask(id);
        return ApiResponse.success();
    }

    @Operation(summary = "取消任务")
    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancelTask(@PathVariable Long id) {
        taskService.cancelTask(id);
        return ApiResponse.success();
    }

    @Operation(summary = "重试任务")
    @PostMapping("/{id}/retry")
    public ApiResponse<Void> retryTask(@PathVariable Long id) {
        taskService.retryTask(id);
        return ApiResponse.success();
    }

    @Operation(summary = "获取执行进度")
    @GetMapping("/{id}/progress")
    public ApiResponse<Object> getTaskProgress(@PathVariable Long id) {
        return ApiResponse.success(taskService.getTaskProgress(id));
    }

    @Operation(summary = "获取执行日志")
    @GetMapping("/{id}/logs")
    public ApiResponse<Object> getTaskLogs(
            @PathVariable Long id,
            @RequestParam(required = false) Long serverId,
            @RequestParam(defaultValue = "all") String stage) {
        return ApiResponse.success(taskService.getTaskLogs(id, serverId, stage));
    }
    
    @Operation(summary = "获取任务步骤")
    @GetMapping("/{id}/steps")
    public ApiResponse<List<TaskStep>> getTaskSteps(@PathVariable Long id) {
        return ApiResponse.success(taskStepMapper.findByTaskIdWithServer(id));
    }
    
    @Operation(summary = "修复任务状态")
    @PostMapping("/fix-status")
    public ApiResponse<Map<String, Integer>> fixTaskStatus() {
        return ApiResponse.success(taskService.fixAllTaskStatus());
    }
    
    @Operation(summary = "获取步骤文件内容（文本文件）")
    @GetMapping("/steps/{stepId}/files/{fileName}")
    public ApiResponse<Map<String, String>> getStepFileContent(
            @PathVariable Long stepId,
            @PathVariable String fileName) {
        
        TaskStep taskStep = taskStepMapper.findByIdWithServer(stepId);
        if (taskStep == null) {
            return ApiResponse.error("步骤不存在");
        }
        
        Server server = serverMapper.selectById(taskStep.getServerId());
        if (server == null) {
            return ApiResponse.error("服务器不存在");
        }
        
        // 从 outputFiles 中查找文件
        List<Map<String, Object>> outputFiles = taskStep.getOutputFiles();
        if (outputFiles == null || outputFiles.isEmpty()) {
            return ApiResponse.error("没有收集的文件");
        }
        
        Map<String, Object> targetFile = null;
        for (Map<String, Object> f : outputFiles) {
            if (fileName.equals(f.get("name"))) {
                targetFile = f;
                break;
            }
        }
        
        if (targetFile == null) {
            return ApiResponse.error("文件不存在: " + fileName);
        }
        
        String filePath = (String) targetFile.get("path");
        if (filePath == null) {
            return ApiResponse.error("文件路径无效");
        }
        
        try {
            // 读取文件内容（限制 1MB）
            SshService.ExecuteResult result = SshService.executeCommand(server,
                "cat " + filePath + " 2>/dev/null | head -c 1048576", null, 30000);
            
            if (result.getExitCode() != 0) {
                return ApiResponse.error("读取文件失败");
            }
            
            return ApiResponse.success(Map.of("content", result.getOutput()));
        } catch (Exception e) {
            return ApiResponse.error("读取文件失败: " + e.getMessage());
        }
    }
    
    @Operation(summary = "下载步骤文件")
    @GetMapping("/steps/{stepId}/files/{fileName}/download")
    public void downloadStepFile(
            @PathVariable Long stepId,
            @PathVariable String fileName,
            HttpServletResponse response) throws IOException {
        
        TaskStep taskStep = taskStepMapper.findByIdWithServer(stepId);
        if (taskStep == null) {
            response.sendError(404, "步骤不存在");
            return;
        }
        
        Server server = serverMapper.selectById(taskStep.getServerId());
        if (server == null) {
            response.sendError(404, "服务器不存在");
            return;
        }
        
        // 从 outputFiles 中查找文件
        List<Map<String, Object>> outputFiles = taskStep.getOutputFiles();
        if (outputFiles == null || outputFiles.isEmpty()) {
            response.sendError(404, "没有收集的文件");
            return;
        }
        
        Map<String, Object> targetFile = null;
        for (Map<String, Object> f : outputFiles) {
            if (fileName.equals(f.get("name"))) {
                targetFile = f;
                break;
            }
        }
        
        if (targetFile == null) {
            response.sendError(404, "文件不存在: " + fileName);
            return;
        }
        
        String filePath = (String) targetFile.get("path");
        if (filePath == null) {
            response.sendError(500, "文件路径无效");
            return;
        }
        
        try {
            // 读取文件内容
            SshService.ExecuteResult result = SshService.executeCommand(server,
                "cat " + filePath + " 2>/dev/null", null, 60000);
            
            if (result.getExitCode() != 0) {
                response.sendError(500, "读取文件失败");
                return;
            }
            
            byte[] fileContent = result.getOutput().getBytes(StandardCharsets.UTF_8);
            
            // 设置响应头
            String encodedFilename = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFilename);
            response.setContentLength(fileContent.length);
            
            response.getOutputStream().write(fileContent);
            response.getOutputStream().flush();
        } catch (Exception e) {
            response.sendError(500, "下载文件失败: " + e.getMessage());
        }
    }

    @Operation(summary = "导出任务指标数据")
    @GetMapping("/{id}/metrics/export")
    public void exportMetrics(
            @PathVariable Long id,
            @RequestParam(defaultValue = "csv") String format,
            HttpServletResponse response) throws IOException {
        
        // 查询任务的指标数据
        LambdaQueryWrapper<Metric> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Metric::getTaskId, id);
        List<Metric> metrics = metricMapper.selectList(wrapper);
        
        // 设置响应头
        String fileName = "task_" + id + "_metrics_" + System.currentTimeMillis() + "." + format;
        String encodedFilename = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        
        if ("json".equalsIgnoreCase(format)) {
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFilename);
            
            StringBuilder json = new StringBuilder();
            json.append("[\n");
            for (int i = 0; i < metrics.size(); i++) {
                Metric m = metrics.get(i);
                json.append("  {\n");
                json.append("    \"id\": ").append(m.getId()).append(",\n");
                json.append("    \"taskId\": ").append(m.getTaskId()).append(",\n");
                json.append("    \"serverId\": ").append(m.getServerId()).append(",\n");
                json.append("    \"metricName\": \"").append(escapeJson(m.getMetricName())).append("\",\n");
                json.append("    \"metricType\": \"").append(escapeJson(m.getMetricType() != null ? m.getMetricType() : "")).append("\",\n");
                json.append("    \"value\": ").append(m.getValue() != null ? m.getValue() : "null").append(",\n");
                json.append("    \"unit\": \"").append(escapeJson(m.getUnit() != null ? m.getUnit() : "")).append("\",\n");
                json.append("    \"timestamp\": \"").append(m.getTimestamp() != null ? m.getTimestamp().toString() : "").append("\"\n");
                json.append(i < metrics.size() - 1 ? "  },\n" : "  }\n");
            }
            json.append("]");
            response.getWriter().write(json.toString());
        } else {
            // 默认 CSV
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFilename);
            
            OutputStreamWriter writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
            writer.write('\ufeff'); // BOM
            writer.write("ID,任务ID,服务器ID,指标名称,指标类型,指标值,单位,采集时间\n");
            
            for (Metric m : metrics) {
                writer.write(String.format("%d,%d,%d,%s,%s,%s,%s,%s\n",
                        m.getId(),
                        m.getTaskId() != null ? m.getTaskId() : 0,
                        m.getServerId() != null ? m.getServerId() : 0,
                        escapeCsv(m.getMetricName()),
                        escapeCsv(m.getMetricType()),
                        m.getValue() != null ? m.getValue().toString() : "",
                        escapeCsv(m.getUnit()),
                        m.getTimestamp() != null ? m.getTimestamp().toString() : ""
                ));
            }
            writer.flush();
        }
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private String escapeCsv(String str) {
        if (str == null) return "";
        if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }
}
