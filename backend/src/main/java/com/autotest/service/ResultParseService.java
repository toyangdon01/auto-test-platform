package com.autotest.service;

import com.autotest.entity.ResultRule;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结果解析服务
 */
@Slf4j
@Service
public class ResultParseService {

    private final ObjectMapper objectMapper;
    private final ScriptFileService scriptFileService;

    public ResultParseService(ObjectMapper objectMapper, ScriptFileService scriptFileService) {
        this.objectMapper = objectMapper;
        this.scriptFileService = scriptFileService;
    }

    // 脚本执行超时时间（秒）
    private static final int SCRIPT_TIMEOUT = 30;

    // 最大输出大小（字节）
    private static final int MAX_OUTPUT_SIZE = 10 * 1024 * 1024;

    /**
     * 解析测试结果
     *
     * @param input 输入数据
     * @param rule  解析规则
     * @return 解析后的数据
     */
    public Map<String, Object> parse(String input, ResultRule rule) {
        try {
            if (input == null || input.isEmpty()) {
                throw new RuntimeException("输入数据为空");
            }

            // 执行解析
            Map<String, Object> result;
            if ("builtin".equals(rule.getParserType())) {
                result = parseWithBuiltin(input, rule.getBuiltinFormat());
            } else {
                result = parseWithScript(input, rule);
            }

            return result;

        } catch (Exception e) {
            log.error("解析失败: ruleId={}", rule.getId(), e);
            throw new RuntimeException("解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 内置解析
     */
    private Map<String, Object> parseWithBuiltin(String input, String format) {
        try {
            if ("key_value".equals(format)) {
                return parseKeyValue(input);
            } else if ("json".equals(format)) {
                return parseJson(input);
            } else {
                throw new IllegalArgumentException("未知的内置格式: " + format);
            }
        } catch (Exception e) {
            throw new RuntimeException("内置解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * Key-Value 格式解析
     */
    private Map<String, Object> parseKeyValue(String input) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        // 匹配 key=value 或 key: value 格式
        Pattern pattern = Pattern.compile("^([^=:]+?)\\s*[=:]\\s*(.+)$");
        
        for (String line : input.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                String key = matcher.group(1).trim();
                String value = matcher.group(2).trim();
                
                // 尝试转换为数值
                try {
                    // 提取数字部分
                    String numStr = value.replaceAll("[^\\d.]", "");
                    if (!numStr.isEmpty()) {
                        if (numStr.contains(".")) {
                            value = numStr;
                        } else {
                            value = numStr;
                        }
                    }
                } catch (Exception ignored) {
                    // 保持原值
                }
                
                result.put(key, value);
            }
        }
        
        return result;
    }

    /**
     * JSON 格式解析
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String input) {
        try {
            // 尝试直接解析
            return objectMapper.readValue(input, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            // 尝试提取 JSON 块
            int start = input.indexOf('{');
            int end = input.lastIndexOf('}');
            
            if (start >= 0 && end > start) {
                String jsonPart = input.substring(start, end + 1);
                try {
                    return objectMapper.readValue(jsonPart, new TypeReference<Map<String, Object>>() {});
                } catch (Exception ex) {
                    throw new RuntimeException("无效的 JSON 格式: " + ex.getMessage());
                }
            }
            
            throw new RuntimeException("无效的 JSON 格式: " + e.getMessage());
        }
    }

    /**
     * 脚本解析
     */
    private Map<String, Object> parseWithScript(String input, ResultRule rule) {
        try {
            // 获取脚本内容
            String script = getScriptContent(rule);
            if (script == null || script.isEmpty()) {
                throw new RuntimeException("脚本内容为空");
            }

            // 执行脚本
            String output = executeScript(script, input, rule.getScriptLanguage());
            
            // 解析输出为 JSON
            return parseJson(output);

        } catch (Exception e) {
            throw new RuntimeException("脚本解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取脚本内容
     */
    private String getScriptContent(ResultRule rule) {
        if ("inline".equals(rule.getScriptSource())) {
            return rule.getScriptContent();
        } else if ("package".equals(rule.getScriptSource())) {
            // 从脚本包读取
            try {
                Long scriptId = rule.getScriptId();
                String scriptPath = rule.getScriptPath();
                if (scriptId == null || scriptPath == null) {
                    throw new RuntimeException("脚本ID或路径为空");
                }
                return scriptFileService.readScriptFile(scriptId, scriptPath);
            } catch (Exception e) {
                throw new RuntimeException("从脚本包读取失败: " + e.getMessage(), e);
            }
        }
        throw new IllegalArgumentException("未知的脚本来源: " + rule.getScriptSource());
    }

    /**
     * 执行解析脚本
     */
    private String executeScript(String script, String input, String language) throws Exception {
        // 创建临时脚本文件
        Path tempDir = Files.createTempDirectory("parse_script_");
        Path scriptFile;
        
        if ("python".equals(language)) {
            scriptFile = tempDir.resolve("parse.py");
        } else if ("shell".equals(language)) {
            scriptFile = tempDir.resolve("parse.sh");
        } else {
            throw new IllegalArgumentException("不支持的脚本语言: " + language);
        }
        
        try {
            // 写入脚本内容
            Files.writeString(scriptFile, script);
            
            // 构建命令
            ProcessBuilder pb;
            if ("python".equals(language)) {
                pb = new ProcessBuilder("python", scriptFile.toString());
            } else {
                pb = new ProcessBuilder("bash", scriptFile.toString());
            }
            
            pb.directory(tempDir.toFile());
            pb.redirectErrorStream(false);
            
            // 启动进程
            Process process = pb.start();
            
            // 写入输入数据
            try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(input);
            }
            
            // 读取输出
            StringBuilder output = new StringBuilder();
            StringBuilder error = new StringBuilder();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    error.append(line).append("\n");
                }
            }
            
            // 等待完成
            boolean finished = process.waitFor(SCRIPT_TIMEOUT, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("脚本执行超时");
            }
            
            if (process.exitValue() != 0) {
                throw new RuntimeException("脚本执行失败: " + error);
            }
            
            return output.toString().trim();
            
        } finally {
            // 清理临时文件
            Files.deleteIfExists(scriptFile);
            Files.deleteIfExists(tempDir);
        }
    }

    /**
     * 测试解析规则
     *
     * @param rule       解析规则
     * @param sampleInput 示例输入
     * @return 解析结果
     */
    public Map<String, Object> testParse(ResultRule rule, String sampleInput) {
        try {
            if ("builtin".equals(rule.getParserType())) {
                return parseWithBuiltin(sampleInput, rule.getBuiltinFormat());
            } else {
                return parseWithScript(sampleInput, rule);
            }
        } catch (Exception e) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", e.getMessage());
            return errorResult;
        }
    }
}
