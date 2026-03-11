package com.autotest.controller;

import com.autotest.common.ApiResponse;
import com.autotest.entity.ResultRule;
import com.autotest.service.ResultRuleService;
import com.autotest.service.ResultParseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 解析规则控制器
 */
@RestController
@RequestMapping("/result-rules")
@RequiredArgsConstructor
public class ResultRuleController {

    private final ResultRuleService resultRuleService;
    private final ResultParseService resultParseService;

    /**
     * 获取脚本的解析规则列表
     */
    @GetMapping("/script/{scriptId}")
    public ApiResponse<List<ResultRule>> listByScript(@PathVariable Long scriptId) {
        return ApiResponse.success(resultRuleService.listByScriptId(scriptId));
    }

    /**
     * 获取全局解析规则列表
     */
    @GetMapping("/global")
    public ApiResponse<List<ResultRule>> listGlobal() {
        return ApiResponse.success(resultRuleService.listGlobalRules());
    }

    /**
     * 获取解析规则详情
     */
    @GetMapping("/{id}")
    public ApiResponse<ResultRule> getById(@PathVariable Long id) {
        ResultRule rule = resultRuleService.getById(id);
        if (rule == null) {
            return ApiResponse.error("解析规则不存在");
        }
        return ApiResponse.success(rule);
    }

    /**
     * 创建解析规则
     */
    @PostMapping
    public ApiResponse<ResultRule> create(@RequestBody ResultRule rule) {
        return ApiResponse.success(resultRuleService.create(rule));
    }

    /**
     * 更新解析规则
     */
    @PutMapping("/{id}")
    public ApiResponse<ResultRule> update(@PathVariable Long id, @RequestBody ResultRule rule) {
        return ApiResponse.success(resultRuleService.update(id, rule));
    }

    /**
     * 删除解析规则
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        resultRuleService.delete(id);
        return ApiResponse.success(null);
    }

    /**
     * 启用/禁用规则
     */
    @PutMapping("/{id}/enabled")
    public ApiResponse<Void> setEnabled(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        resultRuleService.setEnabled(id, body.get("enabled"));
        return ApiResponse.success(null);
    }

    /**
     * 测试解析规则
     */
    @PostMapping("/test")
    public ApiResponse<Map<String, Object>> testParse(@RequestBody Map<String, Object> body) {
        ResultRule rule = new ResultRule();
        rule.setParserType((String) body.get("parserType"));
        rule.setBuiltinFormat((String) body.get("builtinFormat"));
        rule.setScriptSource((String) body.get("scriptSource"));
        rule.setScriptContent((String) body.get("scriptContent"));
        rule.setScriptLanguage((String) body.get("scriptLanguage"));
        
        String sampleInput = (String) body.get("sampleInput");
        
        Map<String, Object> result = resultParseService.testParse(rule, sampleInput);
        return ApiResponse.success(result);
    }
}
