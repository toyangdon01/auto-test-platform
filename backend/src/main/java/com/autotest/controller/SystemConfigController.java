package com.autotest.controller;

import com.autotest.common.ApiResponse;
import com.autotest.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 系统配置控制器
 *
 * @author auto-test-platform
 */
@Tag(name = "系统配置")
@RestController
@RequestMapping("/system/config")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService configService;

    @GetMapping
    @Operation(summary = "获取所有配置")
    public ApiResponse<Map<String, String>> getAll() {
        return ApiResponse.success(configService.getAll());
    }

    @GetMapping("/{key}")
    @Operation(summary = "获取单个配置")
    public ApiResponse<String> get(
            @Parameter(description = "配置键") @PathVariable String key,
            @Parameter(description = "默认值") @RequestParam(required = false) String defaultValue) {
        return ApiResponse.success(configService.get(key, defaultValue));
    }

    @PutMapping
    @Operation(summary = "批量更新配置")
    public ApiResponse<Void> setAll(@RequestBody Map<String, String> configs) {
        configService.setAll(configs);
        return ApiResponse.success(null);
    }

    @PutMapping("/{key}")
    @Operation(summary = "更新单个配置")
    public ApiResponse<Void> set(
            @Parameter(description = "配置键") @PathVariable String key,
            @RequestBody String value) {
        configService.set(key, value);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{key}")
    @Operation(summary = "删除配置")
    public ApiResponse<Void> delete(@Parameter(description = "配置键") @PathVariable String key) {
        configService.delete(key);
        return ApiResponse.success(null);
    }
}
