package com.autotest.dto;

import lombok.Data;

import java.util.List;

/**
 * 输出收集配置
 *
 * @author auto-test-platform
 */
@Data
public class OutputCollectConfig {

    /**
     * 是否启用收集
     */
    private Boolean collectEnabled = false;

    /**
     * 收集规则列表
     */
    private List<CollectRule> collectRules;

    /**
     * 收集规则
     */
    @Data
    public static class CollectRule {
        /**
         * 规则名称
         */
        private String name;

        /**
         * 文件/目录路径（支持通配符）
         */
        private String path;

        /**
         * 类型：file / directory / pattern
         */
        private String type;

        /**
         * 是否必须存在
         */
        private Boolean required = false;

        /**
         * 最大大小限制（如 100MB）
         */
        private String maxSize;
    }
}
