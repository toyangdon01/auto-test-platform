package com.autotest.controller;

import com.autotest.common.ApiResponse;
import com.autotest.common.PageResult;
import com.autotest.entity.ScriptVersion;
import com.autotest.service.ScriptVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 脚本版本管理控制器
 *
 * @author auto-test-platform
 */
@Tag(name = "script-versions", description = "脚本版本管理")
@RestController
@RequestMapping("/scripts/{scriptId}/versions")
@RequiredArgsConstructor
public class ScriptVersionController {

    private final ScriptVersionService versionService;

    @Operation(summary = "获取版本列表")
    @GetMapping
    public ApiResponse<PageResult<ScriptVersion>> listVersions(
            @Parameter(description = "脚本ID") @PathVariable Long scriptId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") Integer size) {
        return ApiResponse.success(versionService.listVersions(scriptId, page, size));
    }

    @Operation(summary = "获取版本详情（按版本号）")
    @GetMapping("/{version}")
    public ApiResponse<ScriptVersion> getVersion(
            @Parameter(description = "脚本ID") @PathVariable Long scriptId,
            @Parameter(description = "版本号") @PathVariable String version) {
        return ApiResponse.success(versionService.getVersion(scriptId, version));
    }

    @Operation(summary = "获取版本详情（按ID）")
    @GetMapping("/id/{versionId}")
    public ApiResponse<ScriptVersion> getVersionById(
            @Parameter(description = "脚本ID") @PathVariable Long scriptId,
            @Parameter(description = "版本ID") @PathVariable Long versionId) {
        return ApiResponse.success(versionService.getVersionById(versionId));
    }

    @Operation(summary = "创建新版本")
    @PostMapping
    public ApiResponse<ScriptVersion> createVersion(
            @Parameter(description = "脚本ID") @PathVariable Long scriptId,
            @RequestBody ScriptVersion version) {
        return ApiResponse.success(versionService.createVersion(scriptId, version));
    }

    @Operation(summary = "更新版本")
    @PutMapping("/{versionId}")
    public ApiResponse<ScriptVersion> updateVersion(
            @Parameter(description = "脚本ID") @PathVariable Long scriptId,
            @Parameter(description = "版本ID") @PathVariable Long versionId,
            @RequestBody ScriptVersion version) {
        return ApiResponse.success(versionService.updateVersion(scriptId, versionId, version));
    }

    @Operation(summary = "删除版本")
    @DeleteMapping("/{versionId}")
    public ApiResponse<Void> deleteVersion(
            @Parameter(description = "脚本ID") @PathVariable Long scriptId,
            @Parameter(description = "版本ID") @PathVariable Long versionId) {
        versionService.deleteVersion(scriptId, versionId);
        return ApiResponse.success();
    }

    @Operation(summary = "回退到指定版本")
    @PostMapping("/rollback/{version}")
    public ApiResponse<Void> rollbackToVersion(
            @Parameter(description = "脚本ID") @PathVariable Long scriptId,
            @Parameter(description = "目标版本号") @PathVariable String version) {
        versionService.rollbackToVersion(scriptId, version);
        return ApiResponse.success();
    }

    @Operation(summary = "版本对比")
    @GetMapping("/compare")
    public ApiResponse<Map<String, Object>> compareVersions(
            @Parameter(description = "脚本ID") @PathVariable Long scriptId,
            @Parameter(description = "版本1") @RequestParam(required = false) String version1,
            @Parameter(description = "版本2") @RequestParam(required = false) String version2,
            @Parameter(description = "起始版本") @RequestParam(required = false) String from,
            @Parameter(description = "目标版本") @RequestParam(required = false) String to) {
        // 支持两种参数名格式
        String v1 = version1 != null ? version1 : from;
        String v2 = version2 != null ? version2 : to;
        return ApiResponse.success(versionService.compareVersions(scriptId, v1, v2));
    }

    @Operation(summary = "设置当前版本")
    @PutMapping("/current/{version}")
    public ApiResponse<Void> setCurrentVersion(
            @Parameter(description = "脚本ID") @PathVariable Long scriptId,
            @Parameter(description = "版本号") @PathVariable String version) {
        versionService.setCurrentVersion(scriptId, version);
        return ApiResponse.success();
    }
}
