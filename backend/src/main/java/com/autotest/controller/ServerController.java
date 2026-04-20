package com.autotest.controller;

import com.autotest.common.ApiResponse;
import com.autotest.common.PageResult;
import com.autotest.dto.request.ServerCreateRequest;
import com.autotest.dto.request.ServerQueryRequest;
import com.autotest.dto.response.ServerDetailResponse;
import com.autotest.entity.Server;
import com.autotest.entity.ServerGroup;
import com.autotest.mapper.ServerGroupMapper;
import com.autotest.mapper.ServerMapper;
import com.autotest.service.ServerService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 服务器管理控制器
 *
 * @author auto-test-platform
 */
@Tag(name = "servers", description = "服务器管理")
@RestController
@RequestMapping("/api/v1/servers")
@RequiredArgsConstructor
public class ServerController {

    private final ServerService serverService;
    private final ServerGroupMapper serverGroupMapper;
    private final ServerMapper serverMapper;

    // ==================== 分组管理（必须在 /{id} 之前） ====================

    @Operation(
        summary = "获取服务器分组列表",
        description = "获取所有服务器分组，包含每个分组的服务器数量"
    )
    @GetMapping("/groups")
    public ApiResponse<List<ServerGroup>> listGroups() {
        List<ServerGroup> groups = serverGroupMapper.selectList(null);
        
        // 统计每个分组的服务器数量
        for (ServerGroup group : groups) {
            Long count = serverMapper.selectCount(
                new LambdaQueryWrapper<Server>().eq(Server::getGroupId, group.getId())
            );
            group.setServerCount(count.intValue());
        }
        
        return ApiResponse.success(groups);
    }

    @Operation(summary = "创建服务器分组")
    @PostMapping("/groups")
    public ApiResponse<ServerGroup> createGroup(@RequestBody ServerGroup group) {
        group.setCreatedAt(LocalDateTime.now());
        group.setUpdatedAt(LocalDateTime.now());
        serverGroupMapper.insert(group);
        return ApiResponse.success(group);
    }

    @Operation(summary = "更新服务器分组")
    @PutMapping("/groups/{id}")
    public ApiResponse<Void> updateGroup(@PathVariable Long id, @RequestBody ServerGroup group) {
        group.setId(id);
        group.setUpdatedAt(LocalDateTime.now());
        serverGroupMapper.updateById(group);
        return ApiResponse.success();
    }

    @Operation(summary = "删除服务器分组")
    @DeleteMapping("/groups/{id}")
    public ApiResponse<Void> deleteGroup(@PathVariable Long id) {
        serverGroupMapper.deleteById(id);
        return ApiResponse.success();
    }

    // ==================== 服务器管理 ====================

    @Operation(summary = "获取服务器列表")
    @GetMapping
    public ApiResponse<PageResult<Server>> listServers(ServerQueryRequest request) {
        return ApiResponse.success(serverService.listServers(request));
    }

    @Operation(summary = "获取服务器详情")
    @GetMapping("/{id}")
    public ApiResponse<ServerDetailResponse> getServer(@PathVariable Long id) {
        return ApiResponse.success(serverService.getServerDetail(id));
    }

    @Operation(
        summary = "添加服务器",
        description = "添加新的目标服务器。\n" +
                     "\n**认证方式：**\n" +
                     "- password: 使用密码认证，需要填写 username 和 password\n" +
                     "- key: 使用密钥认证，需要填写 username 和 privateKey\n" +
                     "\n**字段说明：**\n" +
                     "- name: 服务器名称\n" +
                     "- host: 主机地址（IP或域名）\n" +
                     "- port: SSH端口，默认22\n" +
                     "- authType: 认证类型（password/key）\n" +
                     "- groupId: 所属分组ID"
    )
    @PostMapping
    public ApiResponse<Server> createServer(@Valid @RequestBody ServerCreateRequest request) {
        return ApiResponse.success(serverService.createServer(request));
    }

    @Operation(summary = "批量导入服务器")
    @PostMapping("/batch")
    public ApiResponse<Map<String, Object>> batchCreateServers(@Valid @RequestBody List<ServerCreateRequest> requests) {
        int success = 0;
        int failed = 0;
        StringBuilder errors = new StringBuilder();
        
        for (int i = 0; i < requests.size(); i++) {
            try {
                serverService.createServer(requests.get(i));
                success++;
            } catch (Exception e) {
                failed++;
                if (errors.length() > 0) errors.append("; ");
                errors.append("第").append(i + 1).append("行: ").append(e.getMessage());
            }
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("total", requests.size());
        result.put("success", success);
        result.put("failed", failed);
        result.put("errors", errors.toString());
        
        return ApiResponse.success(result);
    }

    @Operation(summary = "更新服务器")
    @PutMapping("/{id}")
    public ApiResponse<Server> updateServer(@PathVariable Long id, @Valid @RequestBody ServerCreateRequest request) {
        return ApiResponse.success(serverService.updateServer(id, request));
    }

    @Operation(summary = "删除服务器")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteServer(@PathVariable Long id) {
        serverService.deleteServer(id);
        return ApiResponse.success();
    }

    @Operation(
        summary = "测试连接",
        description = "测试与服务器的 SSH 连接。\n" +
                     "\n**测试内容：**\n" +
                     "1. SSH 连接是否可达\n" +
                     "2. 认证是否成功\n" +
                     "\n**返回值：**\n" +
                     "- connected: true/false"
    )
    @PostMapping("/{id}/test")
    public ApiResponse<Map<String, Object>> testConnection(@PathVariable Long id) {
        boolean connected = serverService.testConnection(id);
        Map<String, Object> result = new HashMap<>();
        result.put("connected", connected);
        return ApiResponse.success(result);
    }

    @Operation(summary = "获取服务器状态")
    @GetMapping("/{id}/status")
    public ApiResponse<Server> getServerStatus(@PathVariable Long id) {
        return ApiResponse.success(serverService.getServerStatus(id));
    }

    @Operation(summary = "刷新服务器信息")
    @PostMapping("/{id}/refresh")
    public ApiResponse<Server> refreshServerInfo(@PathVariable Long id) {
        return ApiResponse.success(serverService.refreshServerInfo(id));
    }

    // ==================== 批量操作 ====================

    @Operation(summary = "批量删除服务器")
    @DeleteMapping("/batch")
    public ApiResponse<Void> batchDeleteServers(@RequestBody List<Long> ids) {
        serverService.batchDeleteServers(ids);
        return ApiResponse.success();
    }

    @Operation(summary = "批量启用服务器")
    @PostMapping("/batch/enable")
    public ApiResponse<Void> batchEnableServers(@RequestBody List<Long> ids) {
        serverService.batchUpdateEnabled(ids, true);
        return ApiResponse.success();
    }

    @Operation(summary = "批量禁用服务器")
    @PostMapping("/batch/disable")
    public ApiResponse<Void> batchDisableServers(@RequestBody List<Long> ids) {
        serverService.batchUpdateEnabled(ids, false);
        return ApiResponse.success();
    }

    // ==================== 导出功能 ====================

    @Operation(summary = "导出服务器列表为 YAML")
    @GetMapping("/export")
    public ApiResponse<String> exportServers() {
        List<Server> servers = serverMapper.selectList(
            new LambdaQueryWrapper<Server>().orderByDesc(Server::getId)
        );
        
        ObjectMapper yamlMapper = new ObjectMapper(
            new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
        );
        yamlMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        
        List<Map<String, Object>> serverConfigs = new ArrayList<>();
        for (Server server : servers) {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("name", server.getName());
            config.put("host", server.getHost());
            if (server.getPort() != null && server.getPort() != 22) {
                config.put("port", server.getPort());
            }
            config.put("username", server.getUsername());
            config.put("authType", server.getAuthType() != null ? server.getAuthType() : "password");
            // authSecret 不导出（敏感信息）
            if (server.getGroupId() != null) {
                config.put("groupId", server.getGroupId());
            }
            if (server.getTags() != null && !server.getTags().isEmpty()) {
                config.put("tags", server.getTags());
            }
            if (server.getRemark() != null && !server.getRemark().isEmpty()) {
                config.put("remark", server.getRemark());
            }
            serverConfigs.add(config);
        }
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("servers", serverConfigs);
        
        try {
            String yaml = yamlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
            return ApiResponse.success(yaml);
        } catch (Exception e) {
            return ApiResponse.error("导出失败: " + e.getMessage());
        }
    }
}
