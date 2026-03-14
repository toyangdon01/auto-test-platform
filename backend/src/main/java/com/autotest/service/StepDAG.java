package com.autotest.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 步骤依赖有向无环图
 * 用于管理多步骤任务的执行顺序和依赖关系
 */
@Slf4j
@Data
public class StepDAG {
    
    /**
     * 步骤配置: stepName -> StepConfig
     */
    private final Map<String, StepConfig> steps = new LinkedHashMap<>();
    
    /**
     * 步骤状态: stepName -> status
     */
    private final Map<String, String> status = new HashMap<>();
    
    /**
     * 步骤配置
     */
    @Data
    public static class StepConfig {
        private String name;
        private String displayName;
        private String script;
        private List<String> dependsOn;
        private boolean resultCollector;
        private Map<String, Object> params;
        private Map<String, Object> startupProbe;
    }
    
    /**
     * 添加步骤
     */
    public void addStep(String name, String displayName, String script, 
                        List<String> dependsOn, boolean resultCollector,
                        Map<String, Object> params, Map<String, Object> startupProbe) {
        StepConfig config = new StepConfig();
        config.setName(name);
        config.setDisplayName(displayName);
        config.setScript(script);
        config.setDependsOn(dependsOn != null ? dependsOn : Collections.emptyList());
        config.setResultCollector(resultCollector);
        config.setParams(params);
        config.setStartupProbe(startupProbe);
        
        steps.put(name, config);
        status.put(name, "pending");
    }
    
    /**
     * 获取可执行的步骤（所有依赖都已完成）
     */
    public List<String> getReadySteps() {
        return steps.keySet().stream()
                .filter(name -> "pending".equals(status.get(name)))
                .filter(name -> {
                    StepConfig config = steps.get(name);
                    return config.getDependsOn().stream()
                            .allMatch(dep -> "success".equals(status.get(dep)));
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 获取等待中的步骤（有依赖未完成）
     */
    public List<String> getWaitingSteps() {
        return steps.keySet().stream()
                .filter(name -> "pending".equals(status.get(name)))
                .filter(name -> {
                    StepConfig config = steps.get(name);
                    return !config.getDependsOn().isEmpty() &&
                           config.getDependsOn().stream()
                                   .anyMatch(dep -> !"success".equals(status.get(dep)));
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 标记步骤为运行中
     */
    public void markAsRunning(String stepName) {
        status.put(stepName, "running");
        log.info("Step {} marked as running", stepName);
    }
    
    /**
     * 标记步骤完成
     */
    public void markAsComplete(String stepName, boolean success) {
        status.put(stepName, success ? "success" : "failed");
        log.info("Step {} marked as {}", stepName, success ? "success" : "failed");
    }
    
    /**
     * 标记步骤为跳过
     */
    public void markAsSkipped(String stepName) {
        status.put(stepName, "skipped");
        log.info("Step {} marked as skipped", stepName);
    }
    
    /**
     * 是否还有未完成的步骤
     */
    public boolean hasPendingSteps() {
        return status.values().stream()
                .anyMatch(s -> "pending".equals(s) || "running".equals(s) || "waiting".equals(s));
    }
    
    /**
     * 是否有失败的步骤
     */
    public boolean hasFailedSteps() {
        return status.containsValue("failed");
    }
    
    /**
     * 获取所有失败的步骤
     */
    public List<String> getFailedSteps() {
        return status.entrySet().stream()
                .filter(e -> "failed".equals(e.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取依赖失败而被阻塞的步骤
     */
    public List<String> getBlockedSteps() {
        return steps.keySet().stream()
                .filter(name -> "pending".equals(status.get(name)))
                .filter(name -> {
                    StepConfig config = steps.get(name);
                    return config.getDependsOn().stream()
                            .anyMatch(dep -> "failed".equals(status.get(dep)) || 
                                           "skipped".equals(status.get(dep)));
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 获取步骤配置
     */
    public StepConfig getStepConfig(String stepName) {
        return steps.get(stepName);
    }
    
    /**
     * 获取步骤状态
     */
    public String getStepStatus(String stepName) {
        return status.get(stepName);
    }
    
    /**
     * 获取所有步骤名称（按添加顺序）
     */
    public List<String> getAllStepNames() {
        return new ArrayList<>(steps.keySet());
    }
    
    /**
     * 检测是否有循环依赖
     */
    public boolean hasCycle() {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        
        for (String stepName : steps.keySet()) {
            if (detectCycle(stepName, visited, recursionStack)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean detectCycle(String stepName, Set<String> visited, Set<String> recursionStack) {
        if (recursionStack.contains(stepName)) {
            return true;
        }
        if (visited.contains(stepName)) {
            return false;
        }
        
        visited.add(stepName);
        recursionStack.add(stepName);
        
        StepConfig config = steps.get(stepName);
        if (config != null && config.getDependsOn() != null) {
            for (String dep : config.getDependsOn()) {
                if (detectCycle(dep, visited, recursionStack)) {
                    return true;
                }
            }
        }
        
        recursionStack.remove(stepName);
        return false;
    }
    
    /**
     * 获取执行摘要
     */
    public String getSummary() {
        long pending = status.values().stream().filter(s -> "pending".equals(s)).count();
        long running = status.values().stream().filter(s -> "running".equals(s)).count();
        long success = status.values().stream().filter(s -> "success".equals(s)).count();
        long failed = status.values().stream().filter(s -> "failed".equals(s)).count();
        long skipped = status.values().stream().filter(s -> "skipped".equals(s)).count();
        
        return String.format("Steps: %d total, %d pending, %d running, %d success, %d failed, %d skipped",
                steps.size(), pending, running, success, failed, skipped);
    }
}
