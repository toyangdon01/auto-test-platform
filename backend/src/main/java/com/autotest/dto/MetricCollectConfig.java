package com.autotest.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 指标采集配置
 */
@Data
public class MetricCollectConfig {

    /**
     * 是否启用采集
     */
    private Boolean enabled = true;

    /**
     * 采集频率：1s/5s/10s/30s/1min
     */
    private String frequency = "5s";

    /**
     * CPU 采集配置
     */
    private MetricCategoryConfig cpu;

    /**
     * 内存采集配置
     */
    private MetricCategoryConfig memory;

    /**
     * 磁盘采集配置
     */
    private DiskMetricConfig disk;

    /**
     * 网络采集配置
     */
    private NetworkMetricConfig network;

    /**
     * 自定义指标
     */
    private List<CustomMetricConfig> customMetrics;

    /**
     * 指标分类配置
     */
    @Data
    public static class MetricCategoryConfig {
        private Boolean enabled = true;
        private List<String> metrics;
    }

    /**
     * 磁盘指标配置（支持设备指定）
     */
    @Data
    public static class DiskMetricConfig extends MetricCategoryConfig {
        private List<String> devices;
    }

    /**
     * 网络指标配置（支持网卡指定）
     */
    @Data
    public static class NetworkMetricConfig extends MetricCategoryConfig {
        private List<String> interfaces;
    }

    /**
     * 自定义指标配置
     */
    @Data
    public static class CustomMetricConfig {
        private String name;
        private String command;
        private String unit;
        private String description;
    }

    /**
     * 从 Map 解析配置
     */
    @SuppressWarnings("unchecked")
    public static MetricCollectConfig fromMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }

        MetricCollectConfig config = new MetricCollectConfig();
        config.setEnabled((Boolean) map.getOrDefault("enabled", true));
        config.setFrequency((String) map.getOrDefault("frequency", "5s"));

        // CPU 配置
        Map<String, Object> cpuMap = (Map<String, Object>) map.get("cpu");
        if (cpuMap != null) {
            MetricCategoryConfig cpuConfig = new MetricCategoryConfig();
            cpuConfig.setEnabled((Boolean) cpuMap.getOrDefault("enabled", true));
            cpuConfig.setMetrics((List<String>) cpuMap.get("metrics"));
            config.setCpu(cpuConfig);
        }

        // 内存配置
        Map<String, Object> memoryMap = (Map<String, Object>) map.get("memory");
        if (memoryMap != null) {
            MetricCategoryConfig memoryConfig = new MetricCategoryConfig();
            memoryConfig.setEnabled((Boolean) memoryMap.getOrDefault("enabled", true));
            memoryConfig.setMetrics((List<String>) memoryMap.get("metrics"));
            config.setMemory(memoryConfig);
        }

        // 磁盘配置
        Map<String, Object> diskMap = (Map<String, Object>) map.get("disk");
        if (diskMap != null) {
            DiskMetricConfig diskConfig = new DiskMetricConfig();
            diskConfig.setEnabled((Boolean) diskMap.getOrDefault("enabled", true));
            diskConfig.setMetrics((List<String>) diskMap.get("metrics"));
            diskConfig.setDevices((List<String>) diskMap.get("devices"));
            config.setDisk(diskConfig);
        }

        // 网络配置
        Map<String, Object> networkMap = (Map<String, Object>) map.get("network");
        if (networkMap != null) {
            NetworkMetricConfig networkConfig = new NetworkMetricConfig();
            networkConfig.setEnabled((Boolean) networkMap.getOrDefault("enabled", true));
            networkConfig.setMetrics((List<String>) networkMap.get("metrics"));
            networkConfig.setInterfaces((List<String>) networkMap.get("interfaces"));
            config.setNetwork(networkConfig);
        }

        // 自定义指标
        List<Map<String, Object>> customMetricsList = (List<Map<String, Object>>) map.get("customMetrics");
        if (customMetricsList != null) {
            config.setCustomMetrics(customMetricsList.stream()
                .map(m -> {
                    CustomMetricConfig custom = new CustomMetricConfig();
                    custom.setName((String) m.get("name"));
                    custom.setCommand((String) m.get("command"));
                    custom.setUnit((String) m.get("unit"));
                    custom.setDescription((String) m.get("description"));
                    return custom;
                })
                .toList());
        }

        return config;
    }
}
