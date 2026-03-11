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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务器管理控制器
 *
 * @author auto-test-platform
 */
@Tag(name = "servers", description = "服务器管理")
@RestController
@RequestMapping("/servers")
@RequiredArgsConstructor
public class ServerController {

    private final ServerService serverService;
    private final ServerGroupMapper serverGroupMapper;
    private final ServerMapper serverMapper;

    // ==================== 分组管理（必须在 /{id} 之前） ====================

    @Operation(summary = "获取服务器分组列表")
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

    @Operation(summary = "添加服务器")
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

    @Operation(summary = "测试连接")
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
}
