package com.autotest.controller;

import com.autotest.common.ApiResponse;
import com.autotest.dto.ParseRuleRequest;
import com.autotest.service.ParseRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 解析规则控制器
 *
 * @author auto-test-platform
 */
@Tag(name = "parse-rules", description = "输出解析规则")
@RestController
@RequestMapping("/scripts/{scriptId}/parse-rules")
@RequiredArgsConstructor
public class ParseRuleController {

    private final ParseRuleService parseRuleService;

    @Operation(summary = "获取解析规则")
    @GetMapping
    public ApiResponse<Map<String, Object>> getParseRules(
            @Parameter(description = "脚本ID") @PathVariable Long scriptId) {
        return ApiResponse.success(parseRuleService.getParseRules(scriptId));
    }

    @Operation(summary = "保存解析规则")
    @PostMapping
    public ApiResponse<Void> saveParseRules(
            @Parameter(description = "脚本ID") @PathVariable Long scriptId,
            @RequestBody ParseRuleRequest request) {
        parseRuleService.saveParseRules(scriptId, request);
        return ApiResponse.success();
    }

    @Operation(summary = "更新解析规则")
    @PutMapping
    public ApiResponse<Void> updateParseRules(
            @Parameter(description = "脚本ID") @PathVariable Long scriptId,
            @RequestBody ParseRuleRequest request) {
        parseRuleService.saveParseRules(scriptId, request);
        return ApiResponse.success();
    }

    @Operation(summary = "测试解析规则")
    @PostMapping("/test")
    public ApiResponse<Map<String, Object>> testParseRule(
            @Parameter(description = "脚本ID") @PathVariable Long scriptId,
            @RequestBody Map<String, String> body) {
        String sampleOutput = body.get("sampleOutput");
        return ApiResponse.success(parseRuleService.testParseRule(scriptId, sampleOutput));
    }
}
