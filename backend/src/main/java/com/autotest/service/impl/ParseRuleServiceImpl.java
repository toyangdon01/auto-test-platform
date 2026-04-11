package com.autotest.service.impl;

import com.autotest.dto.ParseRuleRequest;
import com.autotest.entity.Script;
import com.autotest.mapper.ScriptMapper;
import com.autotest.service.ParseRuleService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 输出解析服务实现
 *
 * @author auto-test-platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParseRuleServiceImpl implements ParseRuleService {

    private final ScriptMapper scriptMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void saveParseRules(Long scriptId, ParseRuleRequest request) {
        Script script = scriptMapper.selectById(scriptId);
        if (script == null) {
            throw new RuntimeException("脚本不存在");
        }

        try {
            Map<String, Object> rules = objectMapper.convertValue(request, new TypeReference<Map<String, Object>>() {});
            script.setParseRules(rules);
            script.setUpdatedAt(LocalDateTime.now());
            scriptMapper.updateById(script);
        } catch (Exception e) {
            throw new RuntimeException("保存解析规则失败: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getParseRules(Long scriptId) {
        Script script = scriptMapper.selectById(scriptId);
        if (script == null) {
            throw new RuntimeException("脚本不存在");
        }
        return script.getParseRules();
    }

    @Override
    public Map<String, Object> testParseRule(Long scriptId, String sampleOutput) {
        Map<String, Object> rules = getParseRules(scriptId);
        if (rules == null || rules.isEmpty()) {
            return Map.of("success", false, "message", "未配置解析规则");
        }

        try {
            Map<String, Object> result = parseWithRules(rules, sampleOutput);
            String judgement = judgeWithRules(rules, sampleOutput, 0);

            result.put("judgement", judgement);
            result.put("success", true);
            return result;
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> parseOutput(Long scriptId, String output) {
        Map<String, Object> rules = getParseRules(scriptId);
        if (rules == null || rules.isEmpty()) {
            return new HashMap<>();
        }
        return parseWithRules(rules, output);
    }

    @Override
    public String judgeResult(Long scriptId, String output, Integer exitCode) {
        Map<String, Object> rules = getParseRules(scriptId);
        if (rules == null || rules.isEmpty()) {
            // 默认规则：exitCode == 0 表示成功
            return exitCode == 0 ? "pass" : "fail";
        }
        return judgeWithRules(rules, output, exitCode);
    }

    /**
     * 使用规则解析输出
     */
    private Map<String, Object> parseWithRules(Map<String, Object> rules, String output) {
        Map<String, Object> result = new LinkedHashMap<>();

        String parseType = (String) rules.get("parseType");
        Map<String, Object> config = (Map<String, Object>) rules.get("config");

        if (config == null) {
            return result;
        }

        switch (parseType) {
            case "regex":
                result.putAll(parseWithRegex(config, output));
                break;
            case "json":
                result.putAll(parseWithJsonPath(config, output));
                break;
            case "keyword":
                result.putAll(parseWithKeyword(config, output));
                break;
            default:
                log.warn("未知的解析类型: {}", parseType);
        }

        return result;
    }

    /**
     * 正则解析
     */
    private Map<String, Object> parseWithRegex(Map<String, Object> config, String output) {
        Map<String, Object> result = new LinkedHashMap<>();

        List<Map<String, Object>> patterns = (List<Map<String, Object>>) config.get("regexPatterns");
        if (patterns == null) {
            return result;
        }

        for (Map<String, Object> pattern : patterns) {
            String metricName = (String) pattern.get("metricName");
            String regex = (String) pattern.get("pattern");
            Integer groupIndex = (Integer) pattern.getOrDefault("groupIndex", 1);
            String unit = (String) pattern.get("unit");

            try {
                Pattern p = Pattern.compile(regex, Pattern.MULTILINE);
                Matcher m = p.matcher(output);

                if (m.find()) {
                    String value = groupIndex <= m.groupCount() ? m.group(groupIndex) : m.group();
                    Map<String, Object> metric = new LinkedHashMap<>();
                    metric.put("value", parseValue(value));
                    metric.put("unit", unit);
                    metric.put("raw", m.group());
                    result.put(metricName, metric);
                }
            } catch (Exception e) {
                log.error("正则解析失败: metric={}, pattern={}", metricName, regex, e);
            }
        }

        return result;
    }

    /**
     * JSON Path 解析
     */
    private Map<String, Object> parseWithJsonPath(Map<String, Object> config, String output) {
        Map<String, Object> result = new LinkedHashMap<>();

        List<Map<String, Object>> jsonPaths = (List<Map<String, Object>>) config.get("jsonPaths");
        if (jsonPaths == null) {
            return result;
        }

        try {
            // 清理 ANSI 颜色代码
            String cleanOutput = output.replaceAll("\u001B\\[[;\\d]*m", "");
            cleanOutput = cleanOutput.replaceAll("\\[\\d+;\\d+m", "");
            
            // 尝试从输出中提取 JSON（可能嵌在日志文本中）
            String jsonStr = extractJsonFromOutput(cleanOutput);
            
            Map<String, Object> jsonOutput = objectMapper.readValue(jsonStr, new TypeReference<Map<String, Object>>() {});

            for (Map<String, Object> pathConfig : jsonPaths) {
                String metricName = (String) pathConfig.get("metricName");
                String path = (String) pathConfig.get("path");
                String unit = (String) pathConfig.get("unit");

                Object value = getJsonValue(jsonOutput, path);
                if (value != null) {
                    Map<String, Object> metric = new LinkedHashMap<>();
                    metric.put("value", value);
                    metric.put("unit", unit);
                    result.put(metricName, metric);
                }
            }
        } catch (Exception e) {
            log.error("JSON解析失败", e);
        }

        return result;
    }
    
    /**
     * 从混合输出中提取 JSON
     */
    private String extractJsonFromOutput(String output) {
        // 如果输出本身就是 JSON，直接返回
        String trimmed = output.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return trimmed;
        }
        
        // 从混合文本中提取 JSON 对象
        int start = output.lastIndexOf('{');
        int end = -1;
        int braceCount = 0;
        
        if (start >= 0) {
            for (int i = start; i < output.length(); i++) {
                char c = output.charAt(i);
                if (c == '{') braceCount++;
                else if (c == '}') {
                    braceCount--;
                    if (braceCount == 0) {
                        end = i + 1;
                        break;
                    }
                }
            }
        }
        
        if (start >= 0 && end > start) {
            return output.substring(start, end);
        }
        
        return output;
    }

    /**
     * 关键字解析
     */
    private Map<String, Object> parseWithKeyword(Map<String, Object> config, String output) {
        Map<String, Object> result = new LinkedHashMap<>();

        List<Map<String, Object>> keywords = (List<Map<String, Object>>) config.get("keywords");
        if (keywords == null) {
            return result;
        }

        for (Map<String, Object> keywordConfig : keywords) {
            String keyword = (String) keywordConfig.get("keyword");
            String value = (String) keywordConfig.get("value");
            String type = (String) keywordConfig.get("type");

            if (output.contains(keyword)) {
                Map<String, Object> match = new LinkedHashMap<>();
                match.put("keyword", keyword);
                match.put("value", value);
                match.put("type", type);
                result.put(keyword, match);
            }
        }

        return result;
    }

    /**
     * 判断结果
     */
    private String judgeWithRules(Map<String, Object> rules, String output, Integer exitCode) {
        // 1. 先检查退出码
        Map<String, Object> successRule = (Map<String, Object>) rules.get("successRule");
        if (successRule != null && exitCode != null) {
            List<Integer> exitCodes = (List<Integer>) successRule.get("exitCodes");
            if (exitCodes != null && !exitCodes.isEmpty()) {
                if (!exitCodes.contains(exitCode)) {
                    return "fail";
                }
            } else if (exitCode != 0) {
                return "fail";
            }
        }

        // 2. 检查失败规则
        Map<String, Object> failRule = (Map<String, Object>) rules.get("failRule");
        if (failRule != null) {
            String matchType = (String) failRule.get("matchType");
            List<String> keywords = (List<String>) failRule.get("keywords");
            String pattern = (String) failRule.get("pattern");

            if ("any".equals(matchType) && keywords != null) {
                for (String keyword : keywords) {
                    if (output.contains(keyword)) {
                        return "fail";
                    }
                }
            } else if ("all".equals(matchType) && keywords != null) {
                boolean allMatch = true;
                for (String keyword : keywords) {
                    if (!output.contains(keyword)) {
                        allMatch = false;
                        break;
                    }
                }
                if (allMatch) {
                    return "fail";
                }
            } else if ("regex".equals(matchType) && pattern != null) {
                if (Pattern.compile(pattern, Pattern.MULTILINE).matcher(output).find()) {
                    return "fail";
                }
            }
        }

        // 3. 检查成功规则
        if (successRule != null) {
            String matchType = (String) successRule.get("matchType");
            List<String> keywords = (List<String>) successRule.get("keywords");
            String pattern = (String) successRule.get("pattern");

            if ("any".equals(matchType) && keywords != null) {
                for (String keyword : keywords) {
                    if (output.contains(keyword)) {
                        return "pass";
                    }
                }
            } else if ("all".equals(matchType) && keywords != null) {
                boolean allMatch = true;
                for (String keyword : keywords) {
                    if (!output.contains(keyword)) {
                        allMatch = false;
                        break;
                    }
                }
                if (allMatch) {
                    return "pass";
                }
            } else if ("regex".equals(matchType) && pattern != null) {
                if (Pattern.compile(pattern, Pattern.MULTILINE).matcher(output).find()) {
                    return "pass";
                }
            }
        }

        // 4. 默认根据退出码判断
        return exitCode == 0 ? "pass" : "fail";
    }

    /**
     * 解析数值
     */
    private Object parseValue(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        value = value.trim();

        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            } else {
                return Long.parseLong(value);
            }
        } catch (NumberFormatException e) {
            return value;
        }
    }

    /**
     * 获取 JSON 值（简单路径实现）
     */
    private Object getJsonValue(Map<String, Object> json, String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        // 简单路径: $.data.result 或 data.result
        path = path.replace("$.", "").replace("$", "");

        String[] parts = path.split("\\.");
        Object current = json;

        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return null;
            }
        }

        return current;
    }
}
