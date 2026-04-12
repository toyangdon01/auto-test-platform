package com.autotest.service;

import com.autotest.config.ScriptConfig;
import com.autotest.dto.ConfigParseResult;
import com.autotest.entity.ResourceFile;
import com.autotest.entity.Script;
import com.autotest.entity.ScriptResource;
import com.autotest.entity.ScriptVersion;
import com.autotest.mapper.ResourceFileMapper;
import com.autotest.mapper.ScriptMapper;
import com.autotest.mapper.ScriptResourceMapper;
import com.autotest.mapper.ScriptVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 脚本配置服务
 * 处理 autotest.yaml 文件的解析和生成
 * 
 * @author auto-test-platform
 */
@Slf4j
@Service
public class ScriptConfigService {
    
    private static final String CONFIG_FILE = "autotest.yaml";
    
    @Value("${autotest.storage.scripts-path:C:/data/auto-test/scripts}")
    private String scriptsPath;
    
    private final ScriptMapper scriptMapper;
    private final ScriptVersionMapper scriptVersionMapper;
    private final ScriptResourceMapper scriptResourceMapper;
    private final ResourceFileMapper resourceFileMapper;
    
    private final ObjectMapper yamlMapper;
    
    public ScriptConfigService(ScriptMapper scriptMapper, ScriptVersionMapper scriptVersionMapper,
                               ScriptResourceMapper scriptResourceMapper, ResourceFileMapper resourceFileMapper) {
        this.scriptMapper = scriptMapper;
        this.scriptVersionMapper = scriptVersionMapper;
        this.scriptResourceMapper = scriptResourceMapper;
        this.resourceFileMapper = resourceFileMapper;
        
        // 配置 YAML Mapper
        YAMLFactory yamlFactory = new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)  // 不写 ---
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);         // 最小化引号
        this.yamlMapper = new ObjectMapper(yamlFactory);
        
        // 忽略 null 值和空值
        this.yamlMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    }
    
    /**
     * 解析脚本包中的 autotest.yaml
     * 
     * @param scriptId 脚本 ID
     * @return 配置对象，如果文件不存在则返回 null
     */
    public ScriptConfig parseConfig(Long scriptId) {
        Path configPath = Paths.get(scriptsPath, scriptId.toString(), CONFIG_FILE);
        
        if (!Files.exists(configPath)) {
            log.debug("配置文件不存在：{}", configPath);
            return null;
        }
        
        try {
            ScriptConfig config = yamlMapper.readValue(configPath.toFile(), ScriptConfig.class);
            log.info("成功解析配置文件：{}", configPath);
            return config;
        } catch (IOException e) {
            log.error("解析配置文件失败：{}", configPath, e);
            return null;
        }
    }
    
    /**
     * 从临时目录解析 autotest.yaml（上传时使用）
     * 
     * @param tempPath 临时目录路径
     * @return 配置对象，如果文件不存在则返回 null
     */
    public ScriptConfig parseConfigFromTemp(String tempPath) {
        ConfigParseResult result = parseConfigFromTempWithDetails(tempPath);
        return result.getConfig();
    }
    
    /**
     * 从临时目录解析 autotest.yaml（返回详细结果）
     * 
     * @param tempPath 临时目录路径
     * @return 解析结果（包含配置、状态、警告、错误信息）
     */
    public ConfigParseResult parseConfigFromTempWithDetails(String tempPath) {
        Path configPath = Paths.get(tempPath, CONFIG_FILE);
        
        // 文件不存在
        if (!Files.exists(configPath)) {
            log.debug("临时目录中没有配置文件：{}", configPath);
            return ConfigParseResult.fileNotFound();
        }
        
        try {
            ScriptConfig config = yamlMapper.readValue(configPath.toFile(), ScriptConfig.class);
            log.info("成功解析临时配置文件：{}", configPath);
            
            // 验证配置并收集警告
            List<String> warnings = validateConfig(config);
            
            if (warnings.isEmpty()) {
                return ConfigParseResult.success(config);
            } else {
                return ConfigParseResult.successWithWarnings(config, warnings);
            }
            
        } catch (JsonMappingException e) {
            // JSON/YAML 映射错误（字段类型不匹配等）
            log.error("解析临时配置文件失败（映射错误）：{}", configPath, e);
            String location = e.getLocation() != null 
                ? "行 " + e.getLocation().getLineNr() + ", 列 " + e.getLocation().getColumnNr()
                : "未知位置";
            String error = extractReadableErrorMessage(e);
            return ConfigParseResult.parseError(error, location);
            
        } catch (IOException e) {
            // IO 错误或 YAML 语法错误
            log.error("解析临时配置文件失败：{}", configPath, e);
            String error = e.getMessage();
            // 尝试提取更友好的错误信息
            if (error != null && error.contains("line")) {
                return ConfigParseResult.parseError("YAML 语法错误", extractLineInfo(error));
            }
            return ConfigParseResult.parseError(error, "未知位置");
        }
    }
    
    /**
     * 验证配置并返回警告列表
     */
    private List<String> validateConfig(ScriptConfig config) {
        List<String> warnings = new ArrayList<>();
        
        if (config == null) {
            return warnings;
        }
        
        // 检查必要字段
        if (config.getName() == null || config.getName().isEmpty()) {
            warnings.add("未配置脚本名称 (name)，将使用文件名");
        }
        
        if (config.getType() == null || config.getType().isEmpty()) {
            warnings.add("未配置脚本类型 (type)，默认使用 shell");
        }
        
        // 检查步骤配置
        if (config.getSteps() != null && !config.getSteps().isEmpty()) {
            validateSteps(config.getSteps(), warnings);
        }
        
        return warnings;
    }
    
    /**
     * 验证步骤配置
     */
    @SuppressWarnings("unchecked")
    private void validateSteps(Map<String, Object> steps, List<String> warnings) {
        for (Map.Entry<String, Object> entry : steps.entrySet()) {
            String stepName = entry.getKey();
            
            // 跳过元数据
            if ("_meta".equals(stepName)) {
                continue;
            }
            
            Object stepObj = entry.getValue();
            if (!(stepObj instanceof Map)) {
                warnings.add("步骤 '" + stepName + "' 配置格式错误");
                continue;
            }
            
            Map<String, Object> stepConfig = (Map<String, Object>) stepObj;
            
            // 检查脚本路径
            if (!stepConfig.containsKey("script")) {
                warnings.add("步骤 '" + stepName + "' 未配置执行脚本 (script)");
            }
            
            // 检查依赖是否存在
            Object dependsOn = stepConfig.get("dependsOn");
            if (dependsOn instanceof List) {
                List<String> deps = (List<String>) dependsOn;
                for (String dep : deps) {
                    if (!steps.containsKey(dep)) {
                        warnings.add("步骤 '" + stepName + "' 依赖的步骤 '" + dep + "' 不存在");
                    }
                }
            }
        }
    }
    
    /**
     * 提取可读的错误信息
     */
    private String extractReadableErrorMessage(JsonMappingException e) {
        String message = e.getOriginalMessage();
        if (message == null) {
            message = e.getMessage();
        }
        
        // 简化常见错误信息
        if (message != null) {
            if (message.contains("Cannot deserialize value")) {
                return "字段类型错误：" + message;
            }
            if (message.contains("Unrecognized field")) {
                return "未知字段：" + message;
            }
            if (message.contains("Missing required creator property")) {
                return "缺少必要字段：" + message;
            }
        }
        
        return message;
    }
    
    /**
     * 从错误信息中提取行号信息
     */
    private String extractLineInfo(String error) {
        // 尝试匹配 "line X, column Y" 格式
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("line (\\d+), column (\\d+)");
        java.util.regex.Matcher matcher = pattern.matcher(error);
        if (matcher.find()) {
            return "行 " + matcher.group(1) + ", 列 " + matcher.group(2);
        }
        return "未知位置";
    }
    
    /**
     * 保存配置到 autotest.yaml
     * 
     * @param scriptId 脚本 ID
     * @param config 配置对象
     */
    public void saveConfig(Long scriptId, ScriptConfig config) {
        Path scriptDir = Paths.get(scriptsPath, scriptId.toString());
        Path configPath = scriptDir.resolve(CONFIG_FILE);
        
        try {
            // 确保目录存在
            if (!Files.exists(scriptDir)) {
                Files.createDirectories(scriptDir);
            }
            
            yamlMapper.writeValue(configPath.toFile(), config);
            log.info("成功保存配置文件：{}", configPath);
        } catch (IOException e) {
            log.error("保存配置文件失败：{}", configPath, e);
        }
    }
    
    /**
     * 从数据库生成配置对象
     * 
     * @param scriptId 脚本 ID
     * @return 配置对象
     */
    public ScriptConfig generateConfigFromDatabase(Long scriptId) {
        Script script = scriptMapper.selectById(scriptId);
        if (script == null) {
            return null;
        }
        
        ScriptVersion version = scriptVersionMapper.selectOne(
            new LambdaQueryWrapper<ScriptVersion>()
                .eq(ScriptVersion::getScriptId, scriptId)
                .eq(ScriptVersion::getVersion, script.getCurrentVersion())
        );
        
        if (version == null) {
            return null;
        }
        
        ScriptConfig config = new ScriptConfig();
        config.setName(script.getName());
        config.setDescription(script.getDescription());
        config.setType(script.getScriptType());
        config.setCategory(script.getTestCategory());
        config.setTimeout(script.getDefaultTimeout());
        
        // 转换参数
        if (version.getParameters() != null && !version.getParameters().isEmpty()) {
            List<ScriptConfig.ParameterConfig> params = new ArrayList<>();
            for (Map<String, Object> param : version.getParameters()) {
                ScriptConfig.ParameterConfig pc = new ScriptConfig.ParameterConfig();
                pc.setName((String) param.get("name"));
                pc.setType((String) param.get("type"));
                pc.setDefaultValue(param.get("default"));
                pc.setDescription((String) param.get("description"));
                params.add(pc);
            }
            config.setParameters(params);
        }
        
        // 设置步骤配置（需要转换类型）
        if (version.getSteps() != null && !version.getSteps().isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> stepsMap = (Map<String, Object>) version.getSteps();
            config.setSteps(stepsMap);
        }
        
        // 设置资源配置
        List<ScriptResource> resources = scriptResourceMapper.selectList(
            new LambdaQueryWrapper<ScriptResource>()
                .eq(ScriptResource::getScriptId, scriptId)
                .orderByAsc(ScriptResource::getUploadOrder)
        );
        
        if (!resources.isEmpty()) {
            List<ScriptConfig.ResourceConfig> resourceConfigs = new ArrayList<>();
            for (ScriptResource sr : resources) {
                ScriptConfig.ResourceConfig rc = new ScriptConfig.ResourceConfig();
                rc.setResourceId(sr.getResourceId());
                rc.setTargetPath(sr.getTargetPath());
                rc.setPermissions(sr.getPermissions());
                rc.setOrder(sr.getUploadOrder());
                
                // 查询资源文件的 MD5 值，支持跨项目导入
                ResourceFile resourceFile = resourceFileMapper.selectById(sr.getResourceId());
                if (resourceFile != null && resourceFile.getChecksum() != null) {
                    rc.setResourceMd5(resourceFile.getChecksum());
                }
                
                resourceConfigs.add(rc);
            }
            config.setResources(resourceConfigs);
        }
        
        return config;
    }
    
    /**
     * 自动生成默认配置文件（如果不存在）
     * 
     * @param scriptId 脚本 ID
     * @return 生成的配置对象
     */
    public ScriptConfig generateDefaultConfig(Long scriptId) {
        ScriptConfig config = generateConfigFromDatabase(scriptId);
        if (config != null) {
            saveConfig(scriptId, config);
        }
        return config;
    }
    
    /**
     * 应用配置到脚本实体（上传时使用）
     * 
     * @param config 配置对象
     * @param script 脚本实体
     * @param version 脚本版本实体
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyConfigToScript(ScriptConfig config, Script script, ScriptVersion version) {
        if (config == null) {
            return;
        }
        
        // 应用基本信息
        if (config.getName() != null) {
            script.setName(config.getName());
        }
        if (config.getDescription() != null) {
            script.setDescription(config.getDescription());
        }
        if (config.getType() != null) {
            script.setScriptType(config.getType());
        }
        if (config.getCategory() != null) {
            script.setTestCategory(config.getCategory());
        }
        if (config.getTimeout() != null) {
            script.setDefaultTimeout(config.getTimeout());
        }
        
        // 应用参数配置
        if (config.getParameters() != null && !config.getParameters().isEmpty()) {
            List<Map<String, Object>> params = new ArrayList<>();
            for (ScriptConfig.ParameterConfig pc : config.getParameters()) {
                Map<String, Object> param = new LinkedHashMap<>();
                param.put("name", pc.getName());
                param.put("type", pc.getType() != null ? pc.getType() : "string");
                param.put("default", pc.getDefaultValue());
                param.put("description", pc.getDescription());
                params.add(param);
            }
            version.setParameters(params);
        }
        
        // 应用步骤配置
        if (config.getSteps() != null && !config.getSteps().isEmpty()) {
            version.setSteps(config.getSteps());
        }
        
        // 应用资源配置（从 autotest.yaml 解析）
        if (config.getResources() != null && !config.getResources().isEmpty()) {
            applyResources(config.getResources(), script.getId());
        }
        
        log.info("已应用配置到脚本：{}", script.getName());
    }
    
    /**
     * 应用资源配置到脚本
     * 
     * @param resources 资源配置列表
     * @param scriptId 脚本 ID
     */
    @SuppressWarnings("unchecked")
    private void applyResources(List<ScriptConfig.ResourceConfig> resources, Long scriptId) {
        if (resources == null || resources.isEmpty()) {
            return;
        }
        
        log.info("处理脚本资源配置：scriptId={}, 资源数量={}", scriptId, resources.size());
        
        // 先删除旧的资源配置
        scriptResourceMapper.delete(new LambdaQueryWrapper<ScriptResource>()
                .eq(ScriptResource::getScriptId, scriptId));
        
        // 添加新的资源配置
        for (ScriptConfig.ResourceConfig rc : resources) {
            Long resourceId = null;
            
            // 优先使用 resourceMd5 查找（跨项目一致性更好）
            if (rc.getResourceMd5() != null && !rc.getResourceMd5().isEmpty()) {
                ResourceFile resourceFile = resourceFileMapper.selectOne(
                    new LambdaQueryWrapper<ResourceFile>()
                        .eq(ResourceFile::getChecksum, rc.getResourceMd5())
                );
                if (resourceFile != null) {
                    resourceId = resourceFile.getId();
                    log.info("通过 MD5 找到资源文件：md5={}, resourceId={}", rc.getResourceMd5(), resourceId);
                } else {
                    log.warn("资源文件不存在：md5={}", rc.getResourceMd5());
                }
            }
            
            // 如果 MD5 查找失败，再尝试使用 resourceId
            if (resourceId == null && rc.getResourceId() != null) {
                resourceId = rc.getResourceId();
                log.info("使用配置中的 resourceId: {}", resourceId);
            }
            
            if (resourceId == null) {
                log.warn("跳过无效资源配置：resourceId 和 resourceMd5 都为空或查找失败");
                continue;
            }
            
            // 验证资源文件是否存在
            ResourceFile resourceFile = resourceFileMapper.selectById(resourceId);
            if (resourceFile == null) {
                log.warn("资源文件不存在：resourceId={}", resourceId);
                continue;
            }
            
            // 创建资源关联
            ScriptResource sr = new ScriptResource();
            sr.setScriptId(scriptId);
            sr.setResourceId(resourceId);
            sr.setTargetPath(rc.getTargetPath() != null ? rc.getTargetPath() : "/tmp");
            sr.setPermissions(rc.getPermissions() != null ? rc.getPermissions() : "644");
            sr.setUploadOrder(rc.getOrder() != null ? rc.getOrder() : 0);
            
            scriptResourceMapper.insert(sr);
            log.info("添加资源关联：scriptId={}, resourceId={}, targetPath={}", 
                    scriptId, resourceId, rc.getTargetPath());
        }
    }
    
    /**
     * 同步更新配置文件（当通过 API 修改配置时调用）
     * 
     * @param scriptId 脚本 ID
     */
    public void syncConfig(Long scriptId) {
        ScriptConfig config = generateConfigFromDatabase(scriptId);
        if (config != null) {
            saveConfig(scriptId, config);
            log.info("已同步配置文件：scriptId={}", scriptId);
        }
    }
    
    /**
     * 检查配置文件是否存在
     * 
     * @param scriptId 脚本 ID
     * @return 是否存在
     */
    public boolean configExists(Long scriptId) {
        Path configPath = Paths.get(scriptsPath, scriptId.toString(), CONFIG_FILE);
        return Files.exists(configPath);
    }
    
    /**
     * 删除配置文件
     * 
     * @param scriptId 脚本 ID
     */
    public void deleteConfig(Long scriptId) {
        Path configPath = Paths.get(scriptsPath, scriptId.toString(), CONFIG_FILE);
        try {
            Files.deleteIfExists(configPath);
            log.info("已删除配置文件：{}", configPath);
        } catch (IOException e) {
            log.warn("删除配置文件失败：{}", configPath, e);
        }
    }
}
