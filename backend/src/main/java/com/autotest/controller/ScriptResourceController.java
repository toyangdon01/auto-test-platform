package com.autotest.controller;

import com.autotest.common.ApiResponse;
import com.autotest.entity.ScriptResource;
import com.autotest.service.ScriptResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 脚本资源关联控制器
 */
@RestController
@RequestMapping("/scripts/{scriptId}/resources")
@RequiredArgsConstructor
@Tag(name = "脚本资源关联")
public class ScriptResourceController {

    private final ScriptResourceService scriptResourceService;

    @Operation(summary = "获取脚本的资源列表")
    @GetMapping
    public ApiResponse<List<ScriptResource>> getScriptResources(@PathVariable Long scriptId) {
        List<ScriptResource> resources = scriptResourceService.getByScriptId(scriptId);
        return ApiResponse.success(resources);
    }

    @Operation(summary = "添加资源关联")
    @PostMapping
    public ApiResponse<ScriptResource> addResource(
            @PathVariable Long scriptId,
            @RequestBody AddResourceRequest request) {

        try {
            ScriptResource sr = scriptResourceService.addResource(
                    scriptId,
                    request.getResourceId(),
                    request.getTargetPath(),
                    request.getPermissions(),
                    request.getUploadOrder()
            );
            return ApiResponse.success(sr);
        } catch (Exception e) {
            return ApiResponse.error("添加失败：" + e.getMessage());
        }
    }

    @Operation(summary = "更新资源关联")
    @PutMapping("/{resourceId}")
    public ApiResponse<ScriptResource> updateResource(
            @PathVariable Long scriptId,
            @PathVariable Long resourceId,
            @RequestBody UpdateResourceRequest request) {

        try {
            ScriptResource sr = scriptResourceService.updateResource(
                    scriptId,
                    resourceId,
                    request.getTargetPath(),
                    request.getPermissions(),
                    request.getUploadOrder()
            );
            return ApiResponse.success(sr);
        } catch (Exception e) {
            return ApiResponse.error("更新失败：" + e.getMessage());
        }
    }

    @Operation(summary = "删除资源关联")
    @DeleteMapping("/{resourceId}")
    public ApiResponse<Void> removeResource(
            @PathVariable Long scriptId,
            @PathVariable Long resourceId) {

        scriptResourceService.removeResource(scriptId, resourceId);
        return ApiResponse.success();
    }

    // 请求体 DTO
    @lombok.Data
    public static class AddResourceRequest {
        private Long resourceId;
        private String targetPath;
        private String permissions;
        private Integer uploadOrder;
    }

    @lombok.Data
    public static class UpdateResourceRequest {
        private String targetPath;
        private String permissions;
        private Integer uploadOrder;
    }
}
