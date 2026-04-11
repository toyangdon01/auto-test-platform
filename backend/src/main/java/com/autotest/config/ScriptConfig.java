package com.autotest.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 脚本配置（对应 autotest.yaml）
 * 
 * @author auto-test-platform
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScriptConfig {
    
    /**
     * 脚本名称
     */
    private String name;
    
    /**
     * 描述
     */
    private String description;
    
    /**
     * 脚本类型: shell | python
     */
    private String type;
    
    /**
     * 测试分类
     */
    private String category;
    
    /**
     * 默认超时（秒）
     */
    private Integer timeout;
    
    /**
     * 参数配置
     */
    private List<ParameterConfig> parameters;
    
    /**
     * 步骤配置
     */
    private Map<String, Object> steps;
    
    /**
     * 资源配置
     */
    private List<ResourceConfig> resources;
    
    /**
     * 参数配置
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ParameterConfig {
        private String name;
        private String type;
        
        @JsonProperty("default")
        private Object defaultValue;  // 可以是字符串、数字等
        
        private String description;
    }
    
    /**
     * 步骤配置
     */
    @Data
    public static class StepConfig {
        private String displayName;
        private String script;
        private List<String> dependsOn;
        private Object params;
        private Boolean resultParser;
        private Boolean resultCollector;
        private Map<String, Object> startupProbe;
        private Map<String, Object> parseRule;
        private Boolean fileCollectEnabled;
        private List<Map<String, Object>> fileCollects;
        private List<Map<String, Object>> resources;
    }
    
    /**
     * 资源配置
     */
    @Data
    public static class ResourceConfig {
        private Long resourceId;
        private String targetPath;
        private String permissions;
        private Integer order;
    }
}
