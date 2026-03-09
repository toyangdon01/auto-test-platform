package com.autotest.controller;

import com.autotest.common.ApiResponse;
import com.autotest.entity.ServerGroup;
import com.autotest.mapper.ServerGroupMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 服务器分组控制器
 *
 * @author auto-test-platform
 */
@Tag(name = "server-groups", description = "服务器分组管理")
@RestController
@RequestMapping("/server-groups")
@RequiredArgsConstructor
public class ServerGroupController {

    private final ServerGroupMapper serverGroupMapper;

    @Operation(summary = "获取分组列表")
    @GetMapping
    public ApiResponse<List<ServerGroup>> listGroups() {
        return ApiResponse.success(serverGroupMapper.selectList(null));
    }

    @Operation(summary = "创建分组")
    @PostMapping
    public ApiResponse<ServerGroup> createGroup(@RequestBody ServerGroup group) {
        group.setCreatedAt(LocalDateTime.now());
        group.setUpdatedAt(LocalDateTime.now());
        serverGroupMapper.insert(group);
        return ApiResponse.success(group);
    }

    @Operation(summary = "更新分组")
    @PutMapping("/{id}")
    public ApiResponse<Void> updateGroup(@PathVariable Long id, @RequestBody ServerGroup group) {
        group.setId(id);
        group.setUpdatedAt(LocalDateTime.now());
        serverGroupMapper.updateById(group);
        return ApiResponse.success();
    }

    @Operation(summary = "删除分组")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteGroup(@PathVariable Long id) {
        serverGroupMapper.deleteById(id);
        return ApiResponse.success();
    }
}
