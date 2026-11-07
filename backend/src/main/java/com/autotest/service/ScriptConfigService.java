package com.autotest.service;

import com.autotest.config.PackageSchema;
import com.autotest.config.ScriptConfig;
import com.autotest.dto.ConfigIssue;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

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

    /**
     * scripts.tags 列存 JSON（前端 JSON.parse/JSON.stringify 读写），标签序列化必须走这个 mapper 而不是 yamlMapper
     */
    private final ObjectMapper jsonMapper = new ObjectMapper();
    
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
    @SuppressWarnings("unchecked")
    public ConfigParseResult parseConfigFromTempWithDetails(String tempPath) {
        Path configPath = Paths.get(tempPath, CONFIG_FILE);
        
        // 文件不存在
        if (!Files.exists(configPath)) {
            log.debug("临时目录中没有配置文件：{}", configPath);
            return ConfigParseResult.fileNotFound();
        }
        
        try {
            // 先读取为 JsonNode，以便处理 steps 的多种格式
            JsonNode rootNode = yamlMapper.readTree(configPath.toFile());
            ScriptConfig config = new ScriptConfig();
            
            // 解析基本字段
            if (rootNode.has("name")) {
                config.setName(rootNode.get("name").asText());
            }
            if (rootNode.has("description")) {
                config.setDescription(rootNode.get("description").asText());
            }
            if (rootNode.has("type")) {
                config.setType(rootNode.get("type").asText());
            }
            if (rootNode.has("category")) {
                config.setCategory(rootNode.get("category").asText());
            }
            if (rootNode.has("timeout")) {
                config.setTimeout(rootNode.get("timeout").asInt());
            }
            
            // 解析 tags
            if (rootNode.has("tags") && rootNode.get("tags").isArray()) {
                List<ScriptConfig.TagConfig> tags = new ArrayList<>();
                for (JsonNode tagNode : rootNode.get("tags")) {
                    ScriptConfig.TagConfig tc = new ScriptConfig.TagConfig();
                    if (tagNode.has("key")) tc.setKey(tagNode.get("key").asText());
                    if (tagNode.has("value")) tc.setValue(tagNode.get("value").asText());
                    tags.add(tc);
                }
                config.setTags(tags);
            }
            
            // 解析 parameters
            if (rootNode.has("parameters") && rootNode.get("parameters").isArray()) {
                List<ScriptConfig.ParameterConfig> params = new ArrayList<>();
                for (JsonNode paramNode : rootNode.get("parameters")) {
                    ScriptConfig.ParameterConfig pc = new ScriptConfig.ParameterConfig();
                    if (paramNode.has("name")) pc.setName(paramNode.get("name").asText());
                    if (paramNode.has("type")) pc.setType(paramNode.get("type").asText());
                    JsonNode defaultNode = paramNode.get("default");
                    if (defaultNode == null || defaultNode.isNull()) {
                        JsonNode legacyNode = paramNode.get("defaultValue");
                        if (legacyNode != null && !legacyNode.isNull()) {
                            defaultNode = legacyNode;
                        }
                    }
                    if (defaultNode != null) {
                        if (defaultNode.isTextual()) {
                            pc.setDefaultValue(defaultNode.asText());
                        } else if (defaultNode.isNumber()) {
                            pc.setDefaultValue(defaultNode.numberValue());
                        } else if (defaultNode.isBoolean()) {
                            pc.setDefaultValue(defaultNode.asBoolean());
                        } else {
                            pc.setDefaultValue(defaultNode.asText());
                        }
                    }
                    if (paramNode.has("description")) pc.setDescription(paramNode.get("description").asText());
                    if (paramNode.has("displayName")) pc.setDisplayName(paramNode.get("displayName").asText());
                    if (paramNode.has("required") && paramNode.get("required").isBoolean()) {
                        pc.setRequired(paramNode.get("required").asBoolean());
                    }
                    if (paramNode.has("options") && paramNode.get("options").isArray()) {
                        pc.setOptions(parseOptions(paramNode.get("options")));
                    }
                    params.add(pc);
                }
                config.setParameters(params);
            }
            
            // 解析 steps - 支持 Object 和 Array 两种格式
            if (rootNode.has("steps")) {
                JsonNode stepsNode = rootNode.get("steps");
                Map<String, Object> stepsMap = new LinkedHashMap<>();
                
                if (stepsNode.isObject()) {
                    // Object 格式（当前支持的格式）
                    // 直接转换为 Map
                    stepsMap = yamlMapper.treeToValue(stepsNode, Map.class);
                    log.debug("解析 steps 为 Object 格式，共 {} 个步骤", stepsMap.size());
                    
                } else if (stepsNode.isArray()) {
                    // Array 格式（用户容易误用的格式）
                    // 转换为 Map 格式，使用 name 字段作为 key
                    log.info("检测到 steps 为数组格式，自动转换为对象格式");
                    int index = 1;
                    for (JsonNode stepNode : stepsNode) {
                        String stepName;
                        if (stepNode.has("name") && !stepNode.get("name").asText().isEmpty()) {
                            stepName = stepNode.get("name").asText();
                        } else {
                            // 如果没有 name，自动生成 step_1, step_2, ...
                            stepName = "step_" + index;
                        }
                        
                        // 将步骤转换为 Map
                        Map<String, Object> stepConfig = yamlMapper.treeToValue(stepNode, Map.class);
                        // 移除 name 字段（它是 key，不需要在 value 中）
                        if (stepConfig != null) {
                            stepConfig.remove("name");
                        }
                        
                        stepsMap.put(stepName, stepConfig);
                        index++;
                    }
                    log.debug("数组格式 steps 转换完成，共 {} 个步骤", stepsMap.size());
                }
                
                config.setSteps(stepsMap);
            }
            
            // 解析 resources
            if (rootNode.has("resources") && rootNode.get("resources").isArray()) {
                List<ScriptConfig.ResourceConfig> resources = new ArrayList<>();
                for (JsonNode resNode : rootNode.get("resources")) {
                    ScriptConfig.ResourceConfig rc = new ScriptConfig.ResourceConfig();
                    if (resNode.has("resourceId")) {
                        if (resNode.get("resourceId").isNumber()) {
                            rc.setResourceId(resNode.get("resourceId").asLong());
                        }
                    }
                    if (resNode.has("resourceMd5")) {
                        rc.setResourceMd5(resNode.get("resourceMd5").asText());
                    }
                    if (resNode.has("targetPath")) {
                        rc.setTargetPath(resNode.get("targetPath").asText());
                    }
                    if (resNode.has("permissions")) {
                        rc.setPermissions(resNode.get("permissions").asText());
                    }
                    if (resNode.has("order")) {
                        rc.setOrder(resNode.get("order").asInt());
                    }
                    resources.add(rc);
                }
                config.setResources(resources);
            }
            
            log.info("成功解析临时配置文件：{}", configPath);
            
            // 验证配置并收集警告
            List<String> warnings = validateConfig(config);
            ConfigParseResult result = warnings.isEmpty()
                    ? ConfigParseResult.success(config)
                    : ConfigParseResult.successWithWarnings(config, warnings);
            
            // 解析是逐键手工读取的，写错的键既不会报错也不会生效，所以必须对着原始树做一遍形状校验
            collectSchemaIssues(rootNode, configPath.getParent(), result);
            
            return result;
            
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
        
        // 步骤相关的检查在 collectSchemaIssues 里，那里能看到原始键、也能对照包里的文件清单
        return warnings;
    }
    
    // ==================== 包形状校验 ====================
    
    /**
     * 校验 autotest.yaml 的形状并把问题写进 result。
     * 必须用原始 JsonNode 做：解析是逐键读取的，写错的键到 ScriptConfig 里已经没了，
     * 光看模型永远发现不了"未知键"和"required 写成了字符串"这类问题。
     * 会改变执行结果的算 validationErrors，纯风格建议继续走 validateConfig 的 warnings。
     */
    private void collectSchemaIssues(JsonNode root, Path packageDir, ConfigParseResult result) {
        if (!root.isObject()) {
            result.addValidationError(new ConfigIssue("ROOT_NOT_OBJECT", CONFIG_FILE,
                    "autotest.yaml 顶层必须是「键: 值」映射", null));
            return;
        }
        checkUnknownKeys(root, PackageSchema.TOP_LEVEL, "", result);
        checkTypeAndTimeout(root, result);
        checkParameters(root, result);
        checkSteps(root, packageDir, result);
        checkResources(root, result);
        checkTags(root, result);
        checkLineEndings(packageDir, result);
    }
    
    /**
     * 未知键检查。未知键会被静默丢弃，而且下次保存脚本时连注释一起从包源码里抹掉，所以按错误处理
     */
    private void checkUnknownKeys(JsonNode node, Set<String> allowed, String path, ConfigParseResult result) {
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String key = names.next();
            if (allowed.contains(key)) {
                continue;
            }
            String where = path.isEmpty() ? "autotest.yaml 顶层" : "「" + path + "」里";
            String near = PackageSchema.nearest(key, allowed);
            result.addValidationError(new ConfigIssue("UNKNOWN_KEY",
                    path.isEmpty() ? key : path + "." + key,
                    where + "没有 '" + key + "' 这个键，平台会静默丢掉它",
                    "合法键：" + String.join(", ", allowed)
                            + (near == null ? "" : "；你是不是想写 '" + near + "'")));
        }
    }
    
    private void checkTypeAndTimeout(JsonNode root, ConfigParseResult result) {
        JsonNode type = root.get("type");
        if (type != null && (!type.isTextual() || !PackageSchema.SCRIPT_TYPES.contains(type.asText().trim()))) {
            result.addValidationError(new ConfigIssue("TYPE_INVALID", "type",
                    "type 只能是 " + PackageSchema.SCRIPT_TYPES + "，收到：" + type.asText(),
                    "其它取值不会被识别，执行时会按 shell 处理"));
        }
        JsonNode timeout = root.get("timeout");
        if (timeout != null && (!timeout.isNumber() || timeout.asInt() <= 0)) {
            result.addValidationError(new ConfigIssue("TIMEOUT_INVALID", "timeout",
                    "timeout 必须是正整数（单位秒），收到：" + timeout.asText(),
                    "任务的 timeout 才是毫秒；不想要默认超时就别写这个键"));
        }
    }
    
    private void checkParameters(JsonNode root, ConfigParseResult result) {
        JsonNode params = root.get("parameters");
        if (params == null) {
            return;
        }
        if (!params.isArray()) {
            result.addValidationError(new ConfigIssue("PARAMETERS_NOT_ARRAY", "parameters",
                    "parameters 必须是数组", "每个元素一个参数定义"));
            return;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < params.size(); i++) {
            JsonNode param = params.get(i);
            String path = "parameters[" + i + "]";
            if (!param.isObject()) {
                result.addValidationError(new ConfigIssue("PARAMETER_NOT_OBJECT", path,
                    path + " 不是映射", "写成 {name: THREADS, type: number, default: 4} 这样"));
                continue;
            }
            checkUnknownKeys(param, PackageSchema.PARAMETER, path, result);
            String name = asText(param.get("name"));
            if (name.isEmpty()) {
                result.addValidationError(new ConfigIssue("PARAMETER_NAME_MISSING", path + ".name",
                        path + " 没有 name", "name 就是脚本里读到的环境变量名"));
            } else if (!seen.add(name)) {
                result.addValidationError(new ConfigIssue("PARAMETER_NAME_DUPLICATED", path + ".name",
                        "参数名 " + name + " 重复", "注入环境变量时后者会覆盖前者，去掉一个"));
            }
            JsonNode required = param.get("required");
            if (required != null && !required.isBoolean()) {
                result.addValidationError(new ConfigIssue("PARAMETER_REQUIRED_NOT_BOOLEAN", path + ".required",
                        path + " 的 required 必须是布尔值，收到：" + required.asText(),
                        "加引号的 \"true\" 会被忽略，等于这个参数不必填"));
            }
            checkOptions(param, path, result);
        }
    }
    
    private void checkOptions(JsonNode param, String path, ConfigParseResult result) {
        JsonNode options = param.get("options");
        if (options == null) {
            return;
        }
        if (!options.isArray()) {
            result.addValidationError(new ConfigIssue("OPTIONS_NOT_ARRAY", path + ".options",
                    path + " 的 options 必须是数组", "[{label: 快速, value: fast}] 或裸字符串列表"));
            return;
        }
        for (int i = 0; i < options.size(); i++) {
            JsonNode option = options.get(i);
            String optionPath = path + ".options[" + i + "]";
            if (option.isTextual()) {
                if (option.asText().trim().isEmpty()) {
                    result.addValidationError(emptyOption(optionPath));
                }
                continue;
            }
            if (!option.isObject()) {
                result.addValidationError(new ConfigIssue("OPTIONS_NOT_ARRAY", optionPath,
                        optionPath + " 既不是字符串也不是 {label, value}", "改成 {label: 展示名, value: 提交值}"));
                continue;
            }
            checkUnknownKeys(option, PackageSchema.OPTION, optionPath, result);
            JsonNode valueNode = option.get("value");
            boolean declaresValue = valueNode != null && !valueNode.isNull();
            String value = declaresValue ? valueNode.asText().trim() : "";
            // value 为 null 时后端回落到 label，那是合法项；显式写成空串才是"声明了白名单却没有合法值"
            if (declaresValue && value.isEmpty()
                    || !declaresValue && asText(option.get("label")).isEmpty()) {
                result.addValidationError(emptyOption(optionPath));
            }
        }
    }
    
    private ConfigIssue emptyOption(String path) {
        return new ConfigIssue("OPTION_VALUE_EMPTY", path,
                "options 里有一项取不到提交值",
                "一旦声明了 options，白名单外的取值全被拒；这项等于让所有取值都被拒");
    }
    
    private void checkSteps(JsonNode root, Path packageDir, ConfigParseResult result) {
        JsonNode stepsNode = root.get("steps");
        if (stepsNode == null) {
            result.addValidationError(new ConfigIssue("STEPS_MISSING", "steps",
                    "没有 steps，任务创建出来不会执行任何步骤",
                    "至少写一个：steps: { step_1: { script: main.sh } }"));
            return;
        }
        
        Map<String, JsonNode> stepMap = new LinkedHashMap<>();
        if (stepsNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = stepsNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                stepMap.put(field.getKey(), field.getValue());
            }
        } else if (stepsNode.isArray()) {
            result.addWarning("steps 写成了数组，平台按 name（没有 name 时 step_1、step_2…）转成映射；推荐直接写映射");
            for (int i = 0; i < stepsNode.size(); i++) {
                JsonNode step = stepsNode.get(i);
                String name = asText(step.get("name"));
                stepMap.put(name.isEmpty() ? "step_" + (i + 1) : name, step);
            }
        } else {
            result.addValidationError(new ConfigIssue("STEPS_NOT_MAP", "steps",
                    "steps 必须是「步骤名 -> 步骤配置」的映射", null));
            return;
        }
        
        Set<String> packageFiles = listPackageFiles(packageDir);
        // 数组写法里 name 就是步骤名，转成映射时平台会把它从配置体里摘掉，这里不能算非法键
        Set<String> stepKeys = stepsNode.isArray() ? withStepName() : PackageSchema.STEP;
        Map<String, List<String>> deps = new LinkedHashMap<>();
        List<String> collectors = new ArrayList<>();
        int executable = 0;
        for (Map.Entry<String, JsonNode> entry : stepMap.entrySet()) {
            String stepName = entry.getKey();
            JsonNode step = entry.getValue();
            if (PackageSchema.META_STEP.equals(stepName)) {
                continue;
            }
            executable++;
            if (!step.isObject()) {
                result.addValidationError(new ConfigIssue("STEP_NOT_OBJECT", "steps." + stepName,
                        "步骤 " + stepName + " 的配置不是映射", "steps." + stepName + ": { script: main.sh }"));
                continue;
            }
            checkUnknownKeys(step, stepKeys, "steps." + stepName, result);
            if (isCollector(step)) {
                collectors.add(stepName);
            }
            checkStepScript(step, stepName, packageFiles, result);
            deps.put(stepName, collectDependencies(step, stepName, stepMap.keySet(), result));
        }
        
        if (executable == 0) {
            result.addValidationError(new ConfigIssue("STEPS_EMPTY", "steps",
                    "steps 里只有 _meta，没有任何会执行的步骤", "去掉 _meta 后至少还要有一个步骤"));
            return;
        }
        // 界面是单选，写不出这种形状；手搓 YAML 能，而后果是结果行无法归因
        if (collectors.size() > 1) {
            result.addValidationError(new ConfigIssue("RESULT_COLLECTOR_MULTIPLE", "steps",
                    "有 " + collectors.size() + " 个步骤开了结果收集: " + String.join(", ", collectors)
                            + "；每台服务器会插入多行 test_results，而这张表没有步骤归属列，事后分不清哪行来自哪步",
                    "只让真正产出测试结果的那一步 resultParser: true，其余步骤删掉 resultParser/resultCollector"));
        }
        reportDependencyCycle(deps, result);
    }
    
    /** resultParser 或 resultCollector 任一为 true 就算开了收集（执行时二者是或的关系） */
    private boolean isCollector(JsonNode step) {
        return truthy(step.get("resultParser")) || truthy(step.get("resultCollector"));
    }
    
    private boolean truthy(JsonNode node) {
        return node != null && node.isBoolean() && node.asBoolean();
    }
    
    private Set<String> withStepName() {
        Set<String> keys = new LinkedHashSet<>(PackageSchema.STEP);
        keys.add("name");
        return keys;
    }
    
    private void checkStepScript(JsonNode step, String stepName, Set<String> packageFiles, ConfigParseResult result) {
        String script = asText(step.get("script"));
        String path = "steps." + stepName + ".script";
        if (script.isEmpty()) {
            result.addWarning("步骤 " + stepName + " 未配置 script，执行时从包文件里自动挑入口脚本");
            return;
        }
        if (isInlineCommand(script)) {
            return;
        }
        if (packageFiles.contains(normalizeRelative(script))) {
            return;
        }
        result.addValidationError(new ConfigIssue("STEP_SCRIPT_NOT_FOUND", path,
                "包里没有 " + script + "，这个值又不像命令，执行时会被当成 shell 命令跑（exit 127）",
                packageFiles.isEmpty() ? "包里只有 autotest.yaml" : "包内文件：" + preview(packageFiles)));
    }
    
    private List<String> collectDependencies(JsonNode step, String stepName, Set<String> stepNames,
                                             ConfigParseResult result) {
        JsonNode dependsOn = step.get("dependsOn");
        List<String> deps = new ArrayList<>();
        if (dependsOn == null || dependsOn.isNull()) {
            return deps;
        }
        if (!dependsOn.isArray()) {
            result.addValidationError(new ConfigIssue("DEPENDS_ON_NOT_ARRAY", "steps." + stepName + ".dependsOn",
                    "dependsOn 必须是步骤名数组", "dependsOn: [step_1]"));
            return deps;
        }
        for (JsonNode dep : dependsOn) {
            String depName = asText(dep);
            if (depName.isEmpty()) {
                continue;
            }
            deps.add(depName);
            if (depName.equals(stepName)) {
                result.addValidationError(new ConfigIssue("DEPENDS_ON_SELF", "steps." + stepName + ".dependsOn",
                        "步骤 " + stepName + " 依赖自己", "删掉这个依赖"));
            } else if (!stepNames.contains(depName)) {
                result.addValidationError(new ConfigIssue("DEPENDS_ON_UNKNOWN", "steps." + stepName + ".dependsOn",
                        "步骤 " + stepName + " 依赖的 " + depName + " 不存在",
                        "这个步骤会一直等下去，整个任务不执行；现有步骤：" + preview(stepNames)));
            }
        }
        return deps;
    }
    
    /**
     * 成环时任务在执行阶段才报「检测到循环依赖，任务终止」，导入阶段先挡住
     */
    private void reportDependencyCycle(Map<String, List<String>> deps, ConfigParseResult result) {
        Set<String> visited = new HashSet<>();
        Set<String> stack = new LinkedHashSet<>();
        if (hasCycle(deps, visited, stack)) {
            result.addValidationError(new ConfigIssue("DEPENDENCY_CYCLE", "steps",
                    "步骤依赖成环：" + String.join(" -> ", stack),
                    "任务会直接终止，去掉其中一个 dependsOn"));
        }
    }
    
    private boolean hasCycle(Map<String, List<String>> deps, Set<String> visited, Set<String> stack) {
        for (String node : deps.keySet()) {
            if (detectCycle(node, deps, visited, stack)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean detectCycle(String node, Map<String, List<String>> deps, Set<String> visited, Set<String> stack) {
        if (stack.contains(node)) {
            return true;
        }
        if (visited.contains(node)) {
            return false;
        }
        visited.add(node);
        stack.add(node);
        for (String dep : deps.getOrDefault(node, List.of())) {
            if (deps.containsKey(dep) && detectCycle(dep, deps, visited, stack)) {
                return true;
            }
        }
        stack.remove(node);
        return false;
    }
    
    private void checkResources(JsonNode root, ConfigParseResult result) {
        JsonNode resources = root.get("resources");
        if (resources == null) {
            return;
        }
        if (!resources.isArray()) {
            result.addValidationError(new ConfigIssue("RESOURCES_NOT_ARRAY", "resources",
                    "resources 必须是数组", "[{resourceMd5: ..., targetPath: ...}]"));
            return;
        }
        for (int i = 0; i < resources.size(); i++) {
            JsonNode resource = resources.get(i);
            String path = "resources[" + i + "]";
            if (!resource.isObject()) {
                result.addValidationError(new ConfigIssue("RESOURCE_NOT_OBJECT", path,
                        path + " 不是映射", null));
                continue;
            }
            checkUnknownKeys(resource, PackageSchema.RESOURCE, path, result);
            if (resource.get("resourceMd5") == null && resource.get("resourceId") == null) {
                result.addValidationError(new ConfigIssue("RESOURCE_LOCATOR_MISSING", path,
                        path + " 既没有 resourceMd5 也没有 resourceId",
                        "平台靠这两个字段去资源库里找二进制，缺一个都关联不上"));
            }
        }
    }
    
    private void checkTags(JsonNode root, ConfigParseResult result) {
        JsonNode tags = root.get("tags");
        if (tags == null) {
            return;
        }
        if (!tags.isArray()) {
            result.addValidationError(new ConfigIssue("TAGS_NOT_ARRAY", "tags",
                    "tags 必须是 [{key, value}] 数组，收到：" + tags.asText(),
                    "形状不对时标签会被整段丢掉，界面上看不到也搜不到"));
            return;
        }
        for (int i = 0; i < tags.size(); i++) {
            JsonNode tag = tags.get(i);
            String path = "tags[" + i + "]";
            if (!tag.isObject()) {
                result.addValidationError(new ConfigIssue("TAG_NOT_OBJECT", path,
                        path + " 不是映射", "写成 {key: os, value: kylin}"));
                continue;
            }
            checkUnknownKeys(tag, PackageSchema.TAG, path, result);
        }
    }
    
    /**
     * 平台全链路不做换行归一化，Windows 上写出的 CRLF 脚本到 Linux 上就是
     * $'\\r': command not found，导入时先挡住比执行时炸更好
     */
    private void checkLineEndings(Path packageDir, ConfigParseResult result) {
        Set<String> crlfFiles = collectCrlfFiles(packageDir);
        if (crlfFiles.isEmpty()) {
            return;
        }
        result.addValidationError(new ConfigIssue("CRLF_SCRIPT", "steps",
                "包里有 " + crlfFiles.size() + " 个脚本文件是 CRLF 换行：" + preview(crlfFiles),
                "Linux 上会报 $'\\r': command not found；把这些文件存成 LF 再导入"));
    }
    
    // ==================== 校验用的读文件辅助 ====================
    
    private Set<String> listPackageFiles(Path packageDir) {
        Set<String> files = new LinkedHashSet<>();
        for (Path path : walkPackage(packageDir)) {
            files.add(relative(packageDir, path));
        }
        return files;
    }
    
    private Set<String> collectCrlfFiles(Path packageDir) {
        Set<String> crlf = new LinkedHashSet<>();
        for (Path path : walkPackage(packageDir)) {
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            if (!PackageSchema.LF_ONLY_SUFFIXES.stream().anyMatch(name::endsWith)) {
                continue;
            }
            try {
                if (new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1).contains("\r\n")) {
                    crlf.add(relative(packageDir, path));
                }
            } catch (IOException e) {
                log.warn("读取包文件失败，跳过换行检查：{}", path);
            }
        }
        return crlf;
    }
    
    private List<Path> walkPackage(Path packageDir) {
        List<Path> files = new ArrayList<>();
        if (packageDir == null || !Files.isDirectory(packageDir)) {
            return files;
        }
        try (Stream<Path> stream = Files.walk(packageDir)) {
            stream.filter(Files::isRegularFile).forEach(files::add);
        } catch (IOException e) {
            log.warn("遍历包目录失败：{}", packageDir);
        }
        return files;
    }
    
    private String relative(Path base, Path file) {
        return base.relativize(file).toString().replace('\\', '/');
    }
    
    private String normalizeRelative(String script) {
        String cleaned = script.trim().replace('\\', '/');
        while (cleaned.startsWith("./")) {
            cleaned = cleaned.substring(2);
        }
        return cleaned;
    }
    
    /**
     * 带空格/制表符、或者连扩展名和目录都没有的值按命令处理（执行阶段就是这么分流的：
     * script 与包内文件路径不完全相等就走命令分支）
     */
    private boolean isInlineCommand(String script) {
        String cleaned = script.trim();
        return cleaned.contains(" ") || cleaned.contains("\t")
                || !cleaned.contains(".") && !cleaned.contains("/");
    }
    
    private String asText(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText().trim();
    }
    
    private String preview(Collection<String> values) {
        List<String> list = new ArrayList<>(values);
        int limit = 15;
        if (list.size() <= limit) {
            return String.join(", ", list);
        }
        return String.join(", ", list.subList(0, limit)) + " …共 " + list.size() + " 项";
    }
    
    /**
     * 提取可读的错误信息
     */
    private String extractReadableErrorMessage(JsonMappingException e) {        String message = e.getOriginalMessage();
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
     * 就地改写 autotest.yaml 的顶层 name，只动这一行。
     * 上传创建走的是原样复制文件，不复用 syncConfig：那会用库里的 default_timeout 盖掉包内声明的
     * timeout，还会丢掉注释。
     * 
     * @param scriptId 脚本 ID
     * @param name 界面上的脚本名
     */
    public void alignConfigName(Long scriptId, String name) {
        if (scriptId == null || name == null || name.isBlank()) {
            return;
        }
        
        Path configPath = Paths.get(scriptsPath, scriptId.toString()).resolve(CONFIG_FILE);
        if (!Files.exists(configPath)) {
            return;
        }
        
        try {
            String raw = Files.readString(configPath);
            String nameLine = yamlMapper.writeValueAsString(Collections.singletonMap("name", name)).trim();
            String updated;
            
            java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("(?m)^name:[^\\r\\n]*").matcher(raw);
            if (matcher.find()) {
                if (nameLine.equals(matcher.group())) {
                    return;
                }
                updated = raw.substring(0, matcher.start()) + nameLine + raw.substring(matcher.end());
            } else {
                String eol = raw.contains("\r\n") ? "\r\n" : "\n";
                String[] lines = raw.split("(?<=\\R)");
                int insertAt = 0;
                while (insertAt < lines.length && isConfigHeaderNoise(lines[insertAt])) {
                    insertAt++;
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < insertAt; i++) {
                    sb.append(lines[i]);
                }
                sb.append(nameLine).append(eol);
                for (int i = insertAt; i < lines.length; i++) {
                    sb.append(lines[i]);
                }
                updated = sb.toString();
            }
            
            Files.writeString(configPath, updated);
            log.info("已对齐 autotest.yaml 的 name：{} -> {}", configPath, name);
        } catch (IOException e) {
            log.warn("对齐 autotest.yaml 的 name 失败：{}，{}", configPath, e.getMessage());
        }
    }
    
    /**
     * name 缺失时插入位置的跳过项：YAML 文档分隔符与注释，不能越过第一个真实字段
     */
    private boolean isConfigHeaderNoise(String line) {
        String trimmed = line.trim();
        return trimmed.isEmpty() || trimmed.startsWith("#") || "---".equals(trimmed);
    }
    
    /**
     * 把包里 autotest.yaml 声明的 timeout 落到 scripts.default_timeout（单位是秒）。
     * 只在调用方没给值时生效：不补这一步，库里就是列默认值 3600，之后任意一次保存都会由
     * syncConfig 用 3600 反写掉包里的声明；任务没配超时时，执行阶段读的就是这个字段（步骤自身没有超时可配）。
     * 
     * @param script 刚创建的脚本，字段会被就地更新
     * @return 实际写入的秒数，没写入时返回 null
     */
    public Integer applyDeclaredTimeout(Script script) {
        if (script == null || script.getId() == null || script.getDefaultTimeout() != null) {
            return null;
        }
        
        ScriptConfig config = parseConfig(script.getId());
        if (config == null || config.getTimeout() == null || config.getTimeout() <= 0) {
            return null;
        }
        
        script.setDefaultTimeout(config.getTimeout());
        scriptMapper.updateById(script);
        log.info("已按包内声明设置默认超时：scriptId={}, timeout={}s", script.getId(), config.getTimeout());
        return config.getTimeout();
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
        
        // 转换标签
        if (script.getTags() != null && !script.getTags().isEmpty()) {
            try {
                List<ScriptConfig.TagConfig> tags = jsonMapper.readValue(
                    script.getTags(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<ScriptConfig.TagConfig>>() {}
                );
                config.setTags(tags);
            } catch (Exception e) {
                log.warn("解析脚本标签失败：scriptId={}, tags={}", scriptId, script.getTags(), e);
            }
        }
        
        // 转换参数
        if (version.getParameters() != null && !version.getParameters().isEmpty()) {
            List<ScriptConfig.ParameterConfig> params = new ArrayList<>();
            for (Map<String, Object> param : version.getParameters()) {
                ScriptConfig.ParameterConfig pc = new ScriptConfig.ParameterConfig();
                pc.setName((String) param.get("name"));
                pc.setType((String) param.get("type"));
                pc.setDefaultValue(PackageSchema.declaredDefault(param));
                pc.setDescription((String) param.get("description"));
                pc.setDisplayName((String) param.get("displayName"));
                if (param.get("required") instanceof Boolean) {
                    pc.setRequired((Boolean) param.get("required"));
                }
                pc.setOptions(toOptionConfigs(param.get("options")));
                params.add(pc);
            }
            config.setParameters(params);
        }
        
        // 设置步骤配置（需要转换类型），并为步骤资源添加 MD5 值
        if (version.getSteps() != null && !version.getSteps().isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> stepsMap = (Map<String, Object>) version.getSteps();
            
            // 为每个步骤的资源添加 resourceMd5
            for (Map.Entry<String, Object> entry : stepsMap.entrySet()) {
                if ("_meta".equals(entry.getKey())) continue;
                
                if (entry.getValue() instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> stepConfig = (Map<String, Object>) entry.getValue();
                    
                    Object resourcesObj = stepConfig.get("resources");
                    if (resourcesObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> stepResources = (List<Map<String, Object>>) resourcesObj;
                        
                        for (Map<String, Object> res : stepResources) {
                            Object resourceIdObj = res.get("resourceId");
                            if (resourceIdObj instanceof Number) {
                                Long resourceId = ((Number) resourceIdObj).longValue();
                                ResourceFile resourceFile = resourceFileMapper.selectById(resourceId);
                                if (resourceFile != null && resourceFile.getChecksum() != null) {
                                    res.put("resourceMd5", resourceFile.getChecksum());
                                }
                            }
                        }
                    }
                }
            }
            
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
     * 标签列表转 scripts.tags 列的 JSON 字符串
     *
     * @param tags 配置中的标签，null 表示未声明
     * @return JSON 字符串；未声明标签或序列化失败返回 null，由调用方决定是否覆盖
     */
    public String tagsToJson(List<ScriptConfig.TagConfig> tags) {
        if (tags == null) {
            return null;
        }
        try {
            return jsonMapper.writeValueAsString(tags);
        } catch (Exception e) {
            log.warn("序列化标签失败：{}", tags, e);
            return null;
        }
    }

    /**
     * 参数定义列表转 script_versions.parameters 列的元素形状。
     * name/type/default/description 恒定写出；displayName/required/options 仅在 yaml 真声明时写出，
     * 这样没用到这些字段的包，存储形状与历史保持一致。
     *
     * @param params 配置中的参数定义
     * @return 可直接落库的 Map 列表
     */
    public List<Map<String, Object>> toParamMaps(List<ScriptConfig.ParameterConfig> params) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (params == null) {
            return result;
        }
        for (ScriptConfig.ParameterConfig pc : params) {
            Map<String, Object> param = new LinkedHashMap<>();
            param.put("name", pc.getName());
            param.put("type", pc.getType() != null ? pc.getType() : "string");
            param.put("default", pc.getDefaultValue());
            param.put("description", pc.getDescription());
            if (pc.getDisplayName() != null) {
                param.put("displayName", pc.getDisplayName());
            }
            if (pc.getRequired() != null) {
                param.put("required", pc.getRequired());
            }
            if (pc.getOptions() != null && !pc.getOptions().isEmpty()) {
                List<Map<String, Object>> options = new ArrayList<>();
                for (ScriptConfig.OptionConfig opt : pc.getOptions()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("label", opt.getLabel());
                    item.put("value", opt.getValue());
                    options.add(item);
                }
                param.put("options", options);
            }
            result.add(param);
        }
        return result;
    }

    /**
     * 解析 options：支持 {label, value} 对象与裸字符串两种写法
     */
    private List<ScriptConfig.OptionConfig> parseOptions(JsonNode optionsNode) {
        List<ScriptConfig.OptionConfig> options = new ArrayList<>();
        for (JsonNode item : optionsNode) {
            ScriptConfig.OptionConfig option = new ScriptConfig.OptionConfig();
            if (item.isObject()) {
                String label = item.has("label") ? item.get("label").asText() : null;
                String value = item.has("value") ? item.get("value").asText() : null;
                option.setValue(value != null && !value.isEmpty() ? value : label);
                option.setLabel(label != null && !label.isEmpty() ? label : value);
            } else {
                option.setValue(item.asText());
                option.setLabel(item.asText());
            }
            if (option.getValue() != null && !option.getValue().isEmpty()) {
                options.add(option);
            }
        }
        return options;
    }

    /**
     * 数据库 JSON 列里的 options（对象或裸字符串列表）转回配置模型
     */
    private List<ScriptConfig.OptionConfig> toOptionConfigs(Object optionsObj) {
        if (!(optionsObj instanceof List)) {
            return null;
        }
        List<ScriptConfig.OptionConfig> options = new ArrayList<>();
        for (Object item : (List<?>) optionsObj) {
            ScriptConfig.OptionConfig option = new ScriptConfig.OptionConfig();
            if (item instanceof Map) {
                Object label = ((Map<?, ?>) item).get("label");
                Object value = ((Map<?, ?>) item).get("value");
                option.setValue(value != null ? String.valueOf(value) : (label != null ? String.valueOf(label) : null));
                option.setLabel(label != null ? String.valueOf(label) : option.getValue());
            } else if (item != null) {
                option.setValue(String.valueOf(item));
                option.setLabel(String.valueOf(item));
            } else {
                continue;
            }
            if (option.getValue() != null && !option.getValue().isEmpty()) {
                options.add(option);
            }
        }
        return options.isEmpty() ? null : options;
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
        
        // 应用标签配置
        String tagsJson = tagsToJson(config.getTags());
        script.setTags(tagsJson != null ? tagsJson : "[]");
        
        // 应用参数配置
        if (config.getParameters() != null && !config.getParameters().isEmpty()) {
            version.setParameters(toParamMaps(config.getParameters()));
        }
        
        // 应用步骤配置（处理步骤资源中的 resourceMd5）
        if (config.getSteps() != null && !config.getSteps().isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> stepsMap = (Map<String, Object>) config.getSteps();
            
            // 处理每个步骤的资源
            for (Map.Entry<String, Object> entry : stepsMap.entrySet()) {
                if ("_meta".equals(entry.getKey())) continue;
                
                if (entry.getValue() instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> stepConfig = (Map<String, Object>) entry.getValue();
                    
                    Object resourcesObj = stepConfig.get("resources");
                    if (resourcesObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> stepResources = (List<Map<String, Object>>) resourcesObj;
                        
                        for (Map<String, Object> res : stepResources) {
                            // 优先使用 resourceMd5 查找（跨项目一致性更好）
                            String resourceMd5 = (String) res.get("resourceMd5");
                            Long resourceId = null;
                            
                            if (resourceMd5 != null && !resourceMd5.isEmpty()) {
                                ResourceFile resourceFile = resourceFileMapper.selectOne(
                                    new LambdaQueryWrapper<ResourceFile>()
                                        .eq(ResourceFile::getChecksum, resourceMd5)
                                );
                                if (resourceFile != null) {
                                    resourceId = resourceFile.getId();
                                    log.info("步骤资源通过 MD5 找到：md5={}, resourceId={}, step={}", 
                                            resourceMd5, resourceId, entry.getKey());
                                } else {
                                    log.warn("步骤资源文件不存在：md5={}, step={}", resourceMd5, entry.getKey());
                                }
                            }
                            
                            // 如果 MD5 查找失败，再尝试使用 resourceId
                            if (resourceId == null && res.get("resourceId") instanceof Number) {
                                resourceId = ((Number) res.get("resourceId")).longValue();
                                log.info("步骤资源使用配置中的 resourceId: {}, step={}", resourceId, entry.getKey());
                            }
                            
                            // 更新 resourceId
                            if (resourceId != null) {
                                res.put("resourceId", resourceId);
                            }
                        }
                    }
                }
            }
            
            version.setSteps(stepsMap);
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
