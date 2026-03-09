package com.autotest.controller;

import com.autotest.common.ApiResponse;
import com.autotest.common.PageResult;
import com.autotest.entity.Script;
import com.autotest.mapper.ScriptMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 脚本管理控制器
 *
 * @author auto-test-platform
 */
@Tag(name = "scripts", description = "脚本管理")
@RestController
@RequestMapping("/scripts")
@RequiredArgsConstructor
public class ScriptController {

    private final ScriptMapper scriptMapper;

    @Operation(summary = "获取脚本列表")
    @GetMapping
    public ApiResponse<PageResult<Script>> listScripts(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String testCategory,
            @RequestParam(required = false) String status) {
        
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Script> wrapper = 
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        
        if (name != null && !name.isEmpty()) {
            wrapper.like(Script::getName, name);
        }
        if (testCategory != null && !testCategory.isEmpty()) {
            wrapper.eq(Script::getTestCategory, testCategory);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Script::getStatus, status);
        }
        wrapper.orderByDesc(Script::getCreatedAt);
        
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Script> pageObj = 
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
        
        return ApiResponse.success(PageResult.of(scriptMapper.selectPage(pageObj, wrapper)));
    }

    @Operation(summary = "获取脚本详情")
    @GetMapping("/{id}")
    public ApiResponse<Script> getScript(@PathVariable Long id) {
        return ApiResponse.success(scriptMapper.selectById(id));
    }

    @Operation(summary = "创建脚本")
    @PostMapping
    public ApiResponse<Script> createScript(@RequestBody Script script) {
        script.setCurrentVersion("v1.0");
        script.setCreatedAt(LocalDateTime.now());
        script.setUpdatedAt(LocalDateTime.now());
        scriptMapper.insert(script);
        return ApiResponse.success(script);
    }

    @Operation(summary = "更新脚本")
    @PutMapping("/{id}")
    public ApiResponse<Void> updateScript(@PathVariable Long id, @RequestBody Script script) {
        script.setId(id);
        script.setUpdatedAt(LocalDateTime.now());
        scriptMapper.updateById(script);
        return ApiResponse.success();
    }

    @Operation(summary = "删除脚本")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteScript(@PathVariable Long id) {
        scriptMapper.deleteById(id);
        return ApiResponse.success();
    }
}
