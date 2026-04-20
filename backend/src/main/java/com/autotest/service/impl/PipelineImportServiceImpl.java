package com.autotest.service.impl;

import com.autotest.dto.pipeline.PipelineYamlConfig;
import com.autotest.dto.pipeline.ServerYamlConfig;
import com.autotest.dto.pipeline.TaskYamlConfig;
import com.autotest.entity.Pipeline;
import com.autotest.entity.PipelineTask;
import com.autotest.entity.Script;
import com.autotest.entity.Server;
import com.autotest.exception.BusinessException;
import com.autotest.mapper.ScriptMapper;
import com.autotest.mapper.ServerMapper;
import com.autotest.service.PipelineImportService;
import com.autotest.service.PipelineService;
import com.autotest.service.ServerService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Pipeline 导入服务实现
 *
 * @author auto-test-platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineImportServiceImpl implements PipelineImportService {

    private final PipelineService pipelineService;
    private final ServerService serverService;
    private final ServerMapper serverMapper;
    private final ScriptMapper scriptMapper;
    private final ObjectMapper jsonMapper = new ObjectMapper();
    
    // YAML mapper 配置：忽略 null 值，不输出文档分隔符
    private final ObjectMapper yamlMapper = new ObjectMapper(
        new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
    );

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Pipeline importFromYaml(String yamlContent) {
        // 1. 解析 YAML
        PipelineYamlConfig config;
        try {
            config = yamlMapper.readValue(yamlContent, PipelineYamlConfig.class);
        } catch (Exception e) {
            throw new BusinessException("YAML 解析失败: " + e.getMessage());
        }

        // 2. 校验必填字段
        validateConfig(config);

        // 3. 处理服务器：名称 → ID 映射
        Map<String, Long> serverNameToId = processServers(config.getServers());

        // 4. 处理脚本：名称 → ID 映射
        Map<String, Long> scriptNameToId = processScripts(config.getTasks());

        // 5. 构造 Pipeline 实体
        Pipeline pipeline = new Pipeline();
        pipeline.setName(config.getName());
        pipeline.setDescription(config.getDescription());
        pipeline.setMaxParallel(config.getMaxParallel() != null ? config.getMaxParallel() : 5);
        pipeline.setEnabled(true);

        // 6. 构造 PipelineTask 列表
        List<PipelineTask> tasks = buildPipelineTasks(config.getTasks(), serverNameToId, scriptNameToId);

        // 7. 调用现有服务创建
        return pipelineService.createPipeline(pipeline, tasks);
    }

    /**
     * 校验配置
     */
    private void validateConfig(PipelineYamlConfig config) {
        if (config.getName() == null || config.getName().isEmpty()) {
            throw new BusinessException("编排名称不能为空");
        }
        if (config.getTasks() == null || config.getTasks().isEmpty()) {
            throw new BusinessException("任务列表不能为空");
        }

        // 检查任务名称唯一性
        Set<String> taskNames = new HashSet<>();
        for (TaskYamlConfig task : config.getTasks()) {
            if (task.getName() == null || task.getName().isEmpty()) {
                throw new BusinessException("任务名称不能为空");
            }
            if (taskNames.contains(task.getName())) {
                throw new BusinessException("任务名称重复: " + task.getName());
            }
            taskNames.add(task.getName());
        }
    }

    /**
     * 处理服务器配置
     * - 按 name 匹配：存在则更新，不存在则创建
     * - 返回：名称 → ID 映射
     */
    private Map<String, Long> processServers(List<ServerYamlConfig> configs) {
        Map<String, Long> result = new HashMap<>();

        if (configs == null || configs.isEmpty()) {
            return result;
        }

        for (ServerYamlConfig config : configs) {
            String name = config.getName();
            if (name == null || name.isEmpty()) {
                throw new BusinessException("服务器名称不能为空");
            }
            if (config.getHost() == null || config.getHost().isEmpty()) {
                throw new BusinessException("服务器地址不能为空: " + name);
            }
            if (config.getUsername() == null || config.getUsername().isEmpty()) {
                throw new BusinessException("服务器用户名不能为空: " + name);
            }
            if (config.getAuthSecret() == null || config.getAuthSecret().isEmpty()) {
                throw new BusinessException("服务器认证密钥不能为空: " + name);
            }

            // 按 name 查找是否已存在
            Server existing = serverMapper.selectOne(
                new LambdaQueryWrapper<Server>().eq(Server::getName, name)
            );

            Long serverId;
            if (existing != null) {
                // 已存在：更新
                serverId = existing.getId();
                serverService.updateServer(serverId, config.toCreateRequest());
                log.info("更新服务器: {} (ID: {})", name, serverId);
            } else {
                // 不存在：创建
                Server created = serverService.createServer(config.toCreateRequest());
                serverId = created.getId();
                log.info("创建服务器: {} (ID: {})", name, serverId);
            }

            result.put(name, serverId);
        }

        return result;
    }

    /**
     * 处理脚本引用
     * - 按 name 精确匹配
     * - 不存在则报错
     * - 返回：名称 → ID 映射
     */
    private Map<String, Long> processScripts(List<TaskYamlConfig> tasks) {
        Map<String, Long> result = new HashMap<>();

        for (TaskYamlConfig task : tasks) {
            if (task.getScript() != null && !task.getScript().isEmpty()) {
                String scriptName = task.getScript();

                // 如果已经处理过，跳过
                if (result.containsKey(scriptName)) {
                    continue;
                }

                // 按 name 精确匹配
                Script script = scriptMapper.selectOne(
                    new LambdaQueryWrapper<Script>().eq(Script::getName, scriptName)
                );

                if (script == null) {
                    throw new BusinessException("脚本不存在: " + scriptName);
                }

                result.put(scriptName, script.getId());
                log.info("找到脚本: {} (ID: {})", scriptName, script.getId());
            }
        }

        return result;
    }

    /**
     * 构建 PipelineTask 列表
     */
    private List<PipelineTask> buildPipelineTasks(
            List<TaskYamlConfig> taskConfigs,
            Map<String, Long> serverNameToId,
            Map<String, Long> scriptNameToId) {

        List<PipelineTask> tasks = new ArrayList<>();
        int orderNum = 1;

        for (TaskYamlConfig config : taskConfigs) {
            PipelineTask task = new PipelineTask();
            task.setName(config.getName());
            task.setOrderNum(orderNum++);
            task.setEnabled(true);

            // 脚本ID
            if (config.getScriptId() != null) {
                task.setScriptId(config.getScriptId());
            } else if (config.getScript() != null) {
                Long scriptId = scriptNameToId.get(config.getScript());
                if (scriptId == null) {
                    throw new BusinessException("脚本不存在: " + config.getScript());
                }
                task.setScriptId(scriptId);
            } else {
                throw new BusinessException("任务缺少脚本引用: " + config.getName());
            }

            // 超时时间（秒 → 毫秒）
            if (config.getTimeout() != null) {
                task.setTimeout(config.getTimeout().longValue() * 1000);
            }

            // 依赖任务
            if (config.getDependsOn() != null && !config.getDependsOn().isEmpty()) {
                task.setDependsOn(toJsonString(config.getDependsOn()));
            }

            // 步骤服务器映射
            if (config.getStepServerMapping() != null && !config.getStepServerMapping().isEmpty()) {
                Map<String, List<Long>> mapping = convertStepServerMapping(
                    config.getStepServerMapping(), serverNameToId);
                task.setStepServerMapping(toJsonString(mapping));
                // serverIds 从 stepServerMapping 中自动提取
                Set<Long> allServerIds = new HashSet<>();
                for (List<Long> serverList : mapping.values()) {
                    allServerIds.addAll(serverList);
                }
                task.setServerIds(toJsonString(new ArrayList<>(allServerIds)));
            }

            // 共享参数
            if (config.getSharedParams() != null && !config.getSharedParams().isEmpty()) {
                task.setSharedParams(toJsonString(config.getSharedParams()));
            }

            // 步骤参数
            if (config.getStepParams() != null && !config.getStepParams().isEmpty()) {
                task.setStepParams(toJsonString(config.getStepParams()));
            }

            tasks.add(task);
        }

        return tasks;
    }

    /**
     * 转换服务器引用为ID列表
     * 支持名称和ID混合使用
     */
    private List<Long> convertToServerIds(List<Object> refs, Map<String, Long> serverNameToId) {
        List<Long> result = new ArrayList<>();

        for (Object ref : refs) {
            if (ref instanceof Number) {
                // 数字，直接作为ID
                result.add(((Number) ref).longValue());
            } else if (ref instanceof String) {
                // 字符串，作为名称查找
                String name = (String) ref;
                Long id = serverNameToId.get(name);
                if (id == null) {
                    throw new BusinessException("服务器不存在: " + name);
                }
                result.add(id);
            }
        }

        return result;
    }

    /**
     * 转换步骤服务器映射
     */
    private Map<String, List<Long>> convertStepServerMapping(
            Map<String, List<Object>> mapping,
            Map<String, Long> serverNameToId) {

        Map<String, List<Long>> result = new HashMap<>();

        for (Map.Entry<String, List<Object>> entry : mapping.entrySet()) {
            List<Long> serverIds = convertToServerIds(entry.getValue(), serverNameToId);
            result.put(entry.getKey(), serverIds);
        }

        return result;
    }

    /**
     * 对象转 JSON 字符串
     */
    private String toJsonString(Object obj) {
        try {
            return jsonMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new BusinessException("JSON 序列化失败: " + e.getMessage());
        }
    }

    @Override
    public String exportToYaml(Long pipelineId) {
        Pipeline pipeline = pipelineService.getPipelineById(pipelineId);
        if (pipeline == null) {
            throw new BusinessException("Pipeline 不存在");
        }

        List<PipelineTask> tasks = pipelineService.getPipelineTasks(pipelineId);

        PipelineYamlConfig config = new PipelineYamlConfig();
        config.setName(pipeline.getName());
        // 只设置非 null 值
        if (pipeline.getDescription() != null && !pipeline.getDescription().isEmpty()) {
            config.setDescription(pipeline.getDescription());
        }
        if (pipeline.getMaxParallel() != null && pipeline.getMaxParallel() != 5) {
            config.setMaxParallel(pipeline.getMaxParallel());
        }
        config.setTasks(convertTasksToConfig(tasks));

        try {
            // 配置忽略 null 值
            yamlMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            return yamlMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new BusinessException("YAML 序列化失败: " + e.getMessage());
        }
    }

    /**
     * 转换 PipelineTask 列表为配置
     */
    private List<TaskYamlConfig> convertTasksToConfig(List<PipelineTask> tasks) {
        // 获取所有脚本信息用于导出脚本名称
        Map<Long, String> scriptIdToName = new HashMap<>();
        for (PipelineTask task : tasks) {
            if (!scriptIdToName.containsKey(task.getScriptId())) {
                Script script = scriptMapper.selectById(task.getScriptId());
                if (script != null) {
                    scriptIdToName.put(task.getScriptId(), script.getName());
                }
            }
        }

        return tasks.stream().map(task -> {
            TaskYamlConfig config = new TaskYamlConfig();
            config.setName(task.getName());
            
            // 导出脚本名称而不是 ID
            String scriptName = scriptIdToName.get(task.getScriptId());
            if (scriptName != null) {
                config.setScript(scriptName);
            } else {
                config.setScriptId(task.getScriptId());
            }
            
            // 超时时间（毫秒 → 秒）
            if (task.getTimeout() != null) {
                config.setTimeout(task.getTimeout().intValue() / 1000);
            }

            // 依赖任务
            if (task.getDependsOn() != null && !task.getDependsOn().isEmpty()) {
                try {
                    config.setDependsOn(jsonMapper.readValue(task.getDependsOn(), 
                        jsonMapper.getTypeFactory().constructCollectionType(List.class, String.class)));
                } catch (Exception e) {
                    log.warn("解析 dependsOn 失败", e);
                }
            }
            
            // 步骤服务器映射
            if (task.getStepServerMapping() != null && !task.getStepServerMapping().isEmpty()) {
                try {
                    Map<String, List<Object>> mapping = jsonMapper.readValue(
                        task.getStepServerMapping(), 
                        jsonMapper.getTypeFactory().constructMapType(Map.class, String.class, List.class));
                    config.setStepServerMapping(mapping);
                } catch (Exception e) {
                    log.warn("解析 stepServerMapping 失败", e);
                }
            }
            
            // 共享参数
            if (task.getSharedParams() != null && !task.getSharedParams().isEmpty()) {
                try {
                    config.setSharedParams(jsonMapper.readValue(task.getSharedParams(), 
                        jsonMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)));
                } catch (Exception e) {
                    log.warn("解析 sharedParams 失败", e);
                }
            }
            
            // 步骤参数
            if (task.getStepParams() != null && !task.getStepParams().isEmpty()) {
                try {
                    config.setStepParams(jsonMapper.readValue(task.getStepParams(), 
                        jsonMapper.getTypeFactory().constructMapType(Map.class, String.class, Map.class)));
                } catch (Exception e) {
                    log.warn("解析 stepParams 失败", e);
                }
            }

            return config;
        }).collect(Collectors.toList());
    }
}
