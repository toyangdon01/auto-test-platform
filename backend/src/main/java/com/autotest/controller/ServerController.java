package com.autotest.controller;

import com.autotest.common.ApiResponse;
import com.autotest.common.PageResult;
import com.autotest.dto.request.ServerCreateRequest;
import com.autotest.dto.request.ServerQueryRequest;
import com.autotest.dto.response.ServerDetailResponse;
import com.autotest.entity.Server;
import com.autotest.service.ServerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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
