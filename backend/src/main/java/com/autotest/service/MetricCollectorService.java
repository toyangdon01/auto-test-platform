package com.autotest.service;

import com.autotest.dto.MetricCollectConfig;
import com.autotest.entity.Metric;
import com.autotest.entity.Server;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 指标采集服务
 */
@Slf4j
@Service
public class MetricCollectorService {

    /**
     * 执行单次指标采集
     */
    public List<Metric> collectMetrics(Server server, MetricCollectConfig config) {
        List<Metric> metrics = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        try {
            // CPU 采集
            if (config.getCpu() != null && config.getCpu().getEnabled()) {
                metrics.addAll(collectCpuMetrics(server, config.getCpu().getMetrics(), now));
            }

            // 内存采集
            if (config.getMemory() != null && config.getMemory().getEnabled()) {
                metrics.addAll(collectMemoryMetrics(server, config.getMemory().getMetrics(), now));
            }

            // 磁盘采集
            if (config.getDisk() != null && config.getDisk().getEnabled()) {
                metrics.addAll(collectDiskMetrics(server, config.getDisk().getDevices(), 
                    config.getDisk().getMetrics(), now));
            }

            // 网络采集
            if (config.getNetwork() != null && config.getNetwork().getEnabled()) {
                metrics.addAll(collectNetworkMetrics(server, config.getNetwork().getInterfaces(), 
                    config.getNetwork().getMetrics(), now));
            }

            // 自定义指标
            if (config.getCustomMetrics() != null && !config.getCustomMetrics().isEmpty()) {
                metrics.addAll(collectCustomMetrics(server, config.getCustomMetrics(), now));
            }

        } catch (Exception e) {
            log.error("采集服务器 {} 指标失败", server.getName(), e);
        }

        return metrics;
    }

    /**
     * CPU 指标采集
     */
    private List<Metric> collectCpuMetrics(Server server, List<String> metrics, LocalDateTime timestamp) {
        List<Metric> result = new ArrayList<>();

        try {
            if (metrics == null || metrics.contains("usage_rate")) {
                String cmd = "top -bn1 | grep 'Cpu(s)' | awk '{print $2}'";
                Double value = executeAndParse(server, cmd);
                if (value != null) {
                    result.add(createMetric(server.getId(), "cpu", "usage_rate", value, "%", timestamp));
                }
            }

            if (metrics == null || metrics.contains("load_avg")) {
                String cmd = "cat /proc/loadavg | awk '{print $1, $2, $3}'";
                String output = executeCommand(server, cmd);
                if (output != null) {
                    String[] loads = output.trim().split("\\s+");
                    if (loads.length >= 3) {
                        result.add(createMetric(server.getId(), "cpu", "load_1m", 
                            Double.parseDouble(loads[0]), "", timestamp));
                        result.add(createMetric(server.getId(), "cpu", "load_5m", 
                            Double.parseDouble(loads[1]), "", timestamp));
                        result.add(createMetric(server.getId(), "cpu", "load_15m", 
                            Double.parseDouble(loads[2]), "", timestamp));
                    }
                }
            }

            if (metrics == null || metrics.contains("context_switch")) {
                String cmd = "cat /proc/stat | grep 'ctxt' | awk '{print $2}'";
                Double value = executeAndParse(server, cmd);
                if (value != null) {
                    result.add(createMetric(server.getId(), "cpu", "context_switch", value, "次/s", timestamp));
                }
            }

        } catch (Exception e) {
            log.warn("采集 CPU 指标失败：{}", e.getMessage());
        }

        return result;
    }

    /**
     * 内存指标采集
     */
    private List<Metric> collectMemoryMetrics(Server server, List<String> metrics, LocalDateTime timestamp) {
        List<Metric> result = new ArrayList<>();

        try {
            String cmd = "free -m | grep Mem";
            String output = executeCommand(server, cmd);
            if (output != null) {
                String[] parts = output.trim().split("\\s+");
                if (parts.length >= 7) {
                    long total = Long.parseLong(parts[1]);
                    long used = Long.parseLong(parts[2]);
                    long free = Long.parseLong(parts[3]);
                    long cache = Long.parseLong(parts[6]);

                    if (metrics == null || metrics.contains("used_mb")) {
                        result.add(createMetric(server.getId(), "memory", "used_mb", (double) used, "MB", timestamp));
                    }

                    if (metrics == null || metrics.contains("usage_rate")) {
                        double rate = total > 0 ? (double) used / total * 100 : 0;
                        result.add(createMetric(server.getId(), "memory", "usage_rate", rate, "%", timestamp));
                    }

                    if (metrics == null || metrics.contains("cache_mb")) {
                        result.add(createMetric(server.getId(), "memory", "cache_mb", (double) cache, "MB", timestamp));
                    }

                    if (metrics == null || metrics.contains("free_mb")) {
                        result.add(createMetric(server.getId(), "memory", "free_mb", (double) free, "MB", timestamp));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("采集内存指标失败：{}", e.getMessage());
        }

        return result;
    }

    /**
     * 磁盘指标采集
     */
    private List<Metric> collectDiskMetrics(Server server, List<String> devices, 
                                           List<String> metrics, LocalDateTime timestamp) {
        List<Metric> result = new ArrayList<>();

        try {
            // 获取磁盘 IO 统计
            String cmd = "cat /proc/diskstats";
            String output = executeCommand(server, cmd);
            if (output != null) {
                String[] lines = output.split("\n");
                for (String line : lines) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 14) {
                        String deviceName = parts[2];
                        
                        // 如果指定了设备列表，只采集指定的设备
                        if (devices != null && !devices.isEmpty() && !devices.contains(deviceName)) {
                            continue;
                        }

                        long readSectors = Long.parseLong(parts[5]);
                        long writeSectors = Long.parseLong(parts[9]);
                        
                        // 计算 IOPS 和吞吐量（需要两次采样差值，这里简化为累计值）
                        if (metrics == null || metrics.contains("read_sectors")) {
                            result.add(createMetric(server.getId(), "disk", "read_sectors_" + deviceName, 
                                (double) readSectors, "sectors", timestamp, Map.of("device", deviceName)));
                        }
                        if (metrics == null || metrics.contains("write_sectors")) {
                            result.add(createMetric(server.getId(), "disk", "write_sectors_" + deviceName, 
                                (double) writeSectors, "sectors", timestamp, Map.of("device", deviceName)));
                        }
                    }
                }
            }

            // 磁盘使用率
            if (metrics == null || metrics.contains("usage_rate")) {
                cmd = "df -h | grep -v tmpfs | grep -v Filesystem";
                output = executeCommand(server, cmd);
                if (output != null) {
                    String[] lines = output.split("\n");
                    for (String line : lines) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length >= 6) {
                            String filesystem = parts[0];
                            String usePercent = parts[4].replace("%", "");
                            try {
                                double usage = Double.parseDouble(usePercent);
                                result.add(createMetric(server.getId(), "disk", "usage_rate", usage, "%", 
                                    timestamp, Map.of("filesystem", filesystem)));
                            } catch (NumberFormatException e) {
                                // 忽略解析失败
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.warn("采集磁盘指标失败：{}", e.getMessage());
        }

        return result;
    }

    /**
     * 网络指标采集
     */
    private List<Metric> collectNetworkMetrics(Server server, List<String> interfaces, 
                                               List<String> metrics, LocalDateTime timestamp) {
        List<Metric> result = new ArrayList<>();

        try {
            String cmd = "cat /proc/net/dev";
            String output = executeCommand(server, cmd);
            if (output != null) {
                String[] lines = output.split("\n");
                for (String line : lines) {
                    if (!line.contains(":")) continue;
                    
                    String[] parts = line.split(":");
                    String iface = parts[0].trim();
                    
                    // 如果指定了网卡列表，只采集指定的网卡
                    if (interfaces != null && !interfaces.isEmpty() && !interfaces.contains(iface)) {
                        continue;
                    }
                    
                    // 跳过 lo 回环接口
                    if ("lo".equals(iface)) continue;

                    String[] stats = parts[1].trim().split("\\s+");
                    if (stats.length >= 16) {
                        long rxBytes = Long.parseLong(stats[0]);
                        long txBytes = Long.parseLong(stats[8]);

                        if (metrics == null || metrics.contains("in_bytes")) {
                            result.add(createMetric(server.getId(), "network", "in_bytes_" + iface, 
                                (double) rxBytes, "bytes", timestamp, Map.of("interface", iface)));
                        }
                        if (metrics == null || metrics.contains("out_bytes")) {
                            result.add(createMetric(server.getId(), "network", "out_bytes_" + iface, 
                                (double) txBytes, "bytes", timestamp, Map.of("interface", iface)));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("采集网络指标失败：{}", e.getMessage());
        }

        return result;
    }

    /**
     * 自定义指标采集
     */
    private List<Metric> collectCustomMetrics(Server server, 
                                              List<MetricCollectConfig.CustomMetricConfig> customMetrics, 
                                              LocalDateTime timestamp) {
        List<Metric> result = new ArrayList<>();

        for (MetricCollectConfig.CustomMetricConfig custom : customMetrics) {
            try {
                Double value = executeAndParse(server, custom.getCommand());
                if (value != null) {
                    result.add(createMetric(server.getId(), "custom", custom.getName(), 
                        value, custom.getUnit(), timestamp, Map.of("description", custom.getDescription())));
                }
            } catch (Exception e) {
                log.warn("采集自定义指标 {} 失败：{}", custom.getName(), e.getMessage());
            }
        }

        return result;
    }

    /**
     * 执行命令并解析数值
     */
    private Double executeAndParse(Server server, String cmd) {
        try {
            String output = executeCommand(server, cmd);
            if (output != null) {
                return Double.parseDouble(output.trim());
            }
        } catch (Exception e) {
            log.debug("命令执行失败：{} - {}", cmd, e.getMessage());
        }
        return null;
    }

    /**
     * 执行 SSH 命令
     */
    private String executeCommand(Server server, String cmd) {
        try {
            SshService.ExecuteResult result = SshService.executeCommand(server, cmd, null, 5000);
            if (result != null && result.getExitCode() == 0) {
                return result.getOutput();
            }
        } catch (Exception e) {
            log.debug("SSH 命令执行失败：{} - {}", cmd, e.getMessage());
        }
        return null;
    }

    /**
     * 创建指标对象
     */
    private Metric createMetric(Long serverId, String type, String name, Double value, 
                               String unit, LocalDateTime timestamp) {
        return createMetric(serverId, type, name, value, unit, timestamp, null);
    }

    /**
     * 创建指标对象（带标签）
     */
    private Metric createMetric(Long serverId, String type, String name, Double value, 
                               String unit, LocalDateTime timestamp, Map<String, Object> tags) {
        Metric metric = new Metric();
        metric.setServerId(serverId);
        metric.setMetricType(type);
        metric.setMetricName(name);
        metric.setValue(value);
        metric.setUnit(unit);
        metric.setTimestamp(timestamp);
        metric.setTags(tags);
        return metric;
    }
}
