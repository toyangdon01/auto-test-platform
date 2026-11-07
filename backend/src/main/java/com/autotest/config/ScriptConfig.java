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
     * 标签配置
     */
    private List<TagConfig> tags;
    
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

        /**
         * defaultValue 是 default 的历史别名，两者都写时以 default 为准（与 PackageSchema.declaredDefault 同一口径）
         */
        @JsonProperty(value = "defaultValue", access = JsonProperty.Access.WRITE_ONLY)
        public void setLegacyDefaultValue(Object legacyValue) {
            if (this.defaultValue == null) {
                this.defaultValue = legacyValue;
            }
        }

        private String description;

        /**
         * 界面展示名，前端按 displayName -> name 的优先级取
         */
        private String displayName;

        /**
         * 是否必填；按生效取值判定，提交值和声明的默认值都算有值
         */
        private Boolean required;

        /**
         * 可选值白名单，提交任务时校验取值范围
         */
        private List<OptionConfig> options;
    }

    /**
     * 参数可选值。autotest.yaml 里既支持 {label, value} 对象，也支持裸字符串（此时 label 与 value 同值）
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OptionConfig {
        private String label;
        private String value;
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
        /**
         * 资源文件 ID（优先使用）
         */
        private Long resourceId;
        
        /**
         * 资源文件 MD5 值（resourceId 为空时使用）
         * 支持跨项目共享脚本，避免 ID 冲突
         */
        private String resourceMd5;
        
        private String targetPath;
        private String permissions;
        private Integer order;
    }
    
    /**
     * 标签配置
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TagConfig {
        private String key;
        private String value;
    }
}
