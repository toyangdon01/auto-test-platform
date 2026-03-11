package com.autotest.service.impl;

import com.autotest.common.PageResult;
import com.autotest.entity.MetricDefinition;
import com.autotest.mapper.MetricDefinitionMapper;
import com.autotest.service.MetricDefinitionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 指标定义服务实现
 *
 * @author auto-test-platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricDefinitionServiceImpl implements MetricDefinitionService {

    private final MetricDefinitionMapper metricDefinitionMapper;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<MetricDefinition> getPage(Integer page, Integer size, String category, Boolean enabled) {
        LambdaQueryWrapper<MetricDefinition> wrapper = new LambdaQueryWrapper<>();

        if (category != null && !category.isEmpty()) {
            wrapper.eq(MetricDefinition::getCategory, category);
        }
        if (enabled != null) {
            wrapper.eq(MetricDefinition::getEnabled, enabled);
        }

        wrapper.orderByAsc(MetricDefinition::getCategory)
                .orderByAsc(MetricDefinition::getName);

        Page<MetricDefinition> pageObj = new Page<>(page, size);
        return PageResult.of(metricDefinitionMapper.selectPage(pageObj, wrapper));
    }

    @Override
    public List<MetricDefinition> getAllEnabled() {
        return metricDefinitionMapper.selectList(
                new LambdaQueryWrapper<MetricDefinition>()
                        .eq(MetricDefinition::getEnabled, true)
                        .orderByAsc(MetricDefinition::getCategory)
                        .orderByAsc(MetricDefinition::getName)
        );
    }

    @Override
    public MetricDefinition getById(Long id) {
        return metricDefinitionMapper.selectById(id);
    }

    @Override
    @Transactional
    public MetricDefinition create(MetricDefinition definition) {
        definition.setCreatedAt(LocalDateTime.now());
        definition.setUpdatedAt(LocalDateTime.now());
        if (definition.getEnabled() == null) {
            definition.setEnabled(true);
        }
        metricDefinitionMapper.insert(definition);
        return definition;
    }

    @Override
    @Transactional
    public MetricDefinition update(Long id, MetricDefinition definition) {
        MetricDefinition existing = metricDefinitionMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("指标定义不存在");
        }

        definition.setId(id);
        definition.setUpdatedAt(LocalDateTime.now());
        metricDefinitionMapper.updateById(definition);
        return metricDefinitionMapper.selectById(id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        metricDefinitionMapper.deleteById(id);
    }

    @Override
    public Map<String, Object> extractMetrics(Long scriptId, String output) {
        Map<String, Object> metrics = new LinkedHashMap<>();

        // 获取所有启用的指标定义
        List<MetricDefinition> definitions = getAllEnabled();

        for (MetricDefinition def : definitions) {
            try {
                Object value = extractSingleMetric(def, output);
                if (value != null) {
                    Map<String, Object> metricData = new LinkedHashMap<>();
                    metricData.put("value", value);
                    metricData.put("unit", def.getUnit());
                    metricData.put("category", def.getCategory());
                    metricData.put("extractRule", def.getExtractRule());
                    metrics.put(def.getName(), metricData);
                }
            } catch (Exception e) {
                log.warn("提取指标 {} 失败: {}", def.getName(), e.getMessage());
            }
        }

        return metrics;
    }

    /**
     * 提取单个指标
     */
    private Object extractSingleMetric(MetricDefinition def, String output) {
        String extractRule = def.getExtractRule();
        if (extractRule == null || extractRule.isEmpty()) {
            return null;
        }

        try {
            Map<String, Object> ruleConfig = objectMapper.readValue(extractRule, Map.class);
            String ruleType = (String) ruleConfig.get("type");

            switch (ruleType) {
                case "regex":
                    return extractByRegex(ruleConfig, output, def.getDataType());
                case "jsonPath":
                    return extractByJsonPath(ruleConfig, output, def.getDataType());
                case "keyword":
                    return extractByKeyword(ruleConfig, output);
                case "line":
                    return extractByLine(ruleConfig, output, def.getDataType());
                default:
                    log.warn("未知的提取规则类型: {}", ruleType);
                    return null;
            }
        } catch (Exception e) {
            log.error("解析提取规则失败: {}", def.getName(), e);
            return null;
        }
    }

    /**
     * 正则表达式提取
     */
    private Object extractByRegex(Map<String, Object> rule, String output, String dataType) {
        String pattern = (String) rule.get("pattern");
        Integer group = rule.get("group") != null ? ((Number) rule.get("group")).intValue() : 1;

        if (pattern == null) return null;

        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.MULTILINE);
        java.util.regex.Matcher m = p.matcher(output);

        if (m.find()) {
            String value = m.groupCount() >= group ? m.group(group) : m.group();
            return convertValue(value, dataType);
        }

        return null;
    }

    /**
     * JSON Path 提取
     */
    private Object extractByJsonPath(Map<String, Object> rule, String output, String dataType) {
        String jsonPath = (String) rule.get("path");
        if (jsonPath == null) return null;

        // 尝试从输出中提取 JSON
        try {
            // 查找 JSON 块
            int start = output.indexOf('{');
            int end = output.lastIndexOf('}');
            if (start >= 0 && end > start) {
                String jsonStr = output.substring(start, end + 1);
                Map<String, Object> json = objectMapper.readValue(jsonStr, Map.class);
                Object value = getValueByPath(json, jsonPath);
                return value != null ? convertValue(String.valueOf(value), dataType) : null;
            }
        } catch (Exception e) {
            log.debug("JSON Path 提取失败: {}", e.getMessage());
        }

        return null;
    }

    /**
     * 关键字提取
     */
    private Object extractByKeyword(Map<String, Object> rule, String output) {
        String keyword = (String) rule.get("keyword");
        String delimiter = (String) rule.getOrDefault("delimiter", ":");

        if (keyword == null) return null;

        for (String line : output.split("\n")) {
            if (line.contains(keyword)) {
                int idx = line.indexOf(keyword) + keyword.length();
                if (idx < line.length()) {
                    String remaining = line.substring(idx).trim();
                    if (remaining.startsWith(delimiter)) {
                        remaining = remaining.substring(delimiter.length()).trim();
                    }
                    // 尝试解析数值
                    try {
                        if (remaining.contains(".")) {
                            return Double.parseDouble(remaining.replaceAll("[^0-9.]", ""));
                        } else {
                            return Long.parseLong(remaining.replaceAll("[^0-9-]", ""));
                        }
                    } catch (NumberFormatException e) {
                        return remaining;
                    }
                }
            }
        }

        return null;
    }

    /**
     * 按行提取
     */
    private Object extractByLine(Map<String, Object> rule, String output, String dataType) {
        Integer lineNum = rule.get("lineNumber") != null ? ((Number) rule.get("lineNumber")).intValue() : 1;
        String delimiter = (String) rule.getOrDefault("delimiter", "\\s+");
        Integer field = rule.get("field") != null ? ((Number) rule.get("field")).intValue() : 1;

        String[] lines = output.split("\n");
        if (lineNum > 0 && lineNum <= lines.length) {
            String line = lines[lineNum - 1].trim();
            String[] fields = line.split(delimiter);
            if (field > 0 && field <= fields.length) {
                return convertValue(fields[field - 1], dataType);
            }
        }

        return null;
    }

    /**
     * 根据路径获取值
     */
    private Object getValueByPath(Map<String, Object> json, String path) {
        String[] parts = path.replace("$", "").split("\\.");
        Object current = json;

        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
        }

        return current;
    }

    /**
     * 转换值类型
     */
    private Object convertValue(String value, String dataType) {
        if (value == null || dataType == null) return value;

        try {
            switch (dataType.toLowerCase()) {
                case "integer":
                case "int":
                    return Long.parseLong(value.trim());
                case "float":
                case "double":
                    return Double.parseDouble(value.trim());
                case "boolean":
                    return Boolean.parseBoolean(value.trim());
                default:
                    return value.trim();
            }
        } catch (NumberFormatException e) {
            return value;
        }
    }
}
