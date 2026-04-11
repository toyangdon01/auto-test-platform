package com.autotest.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 输出解析规则请求
 *
 * @author auto-test-platform
 */
@Data
public class ParseRuleRequest {

    /**
     * 解析类型: regex/json/keyword
     */
    private String parseType;

    /**
     * 解析规则配置
     */
    private ParseConfig config;

    /**
     * 成功判断规则
     */
    private SuccessRule successRule;

    /**
     * 失败判断规则
     */
    private FailRule failRule;

    @Data
    public static class ParseConfig {
        /**
         * 正则表达式模式列表
         */
        private List<RegexPattern> regexPatterns;

        /**
         * JSON Path 配置
         */
        private List<JsonPathConfig> jsonPaths;

        /**
         * 关键字配置
         */
        private List<KeywordConfig> keywords;
    }

    @Data
    public static class RegexPattern {
        /**
         * 指标名称
         */
        private String metricName;

        /**
         * 正则表达式
         */
        private String pattern;

        /**
         * 提取组索引
         */
        private Integer groupIndex;

        /**
         * 单位
         */
        private String unit;
    }

    @Data
    public static class JsonPathConfig {
        /**
         * 指标名称
         */
        private String metricName;

        /**
         * JSON Path
         */
        private String path;

        /**
         * 单位
         */
        private String unit;
    }

    @Data
    public static class KeywordConfig {
        /**
         * 关键字
         */
        private String keyword;

        /**
         * 匹配后的值
         */
        private String value;

        /**
         * 类型: success/fail/metric
         */
        private String type;
    }

    @Data
    public static class SuccessRule {
        /**
         * 匹配方式: any/all/regex
         */
        private String matchType;

        /**
         * 成功关键字列表
         */
        private List<String> keywords;

        /**
         * 成功正则
         */
        private String pattern;

        /**
         * 退出码
         */
        private List<Integer> exitCodes;
    }

    @Data
    public static class FailRule {
        /**
         * 匹配方式: any/all/regex
         */
        private String matchType;

        /**
         * 失败关键字列表
         */
        private List<String> keywords;

        /**
         * 失败正则
         */
        private String pattern;
    }
}
