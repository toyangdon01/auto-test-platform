package com.autotest.service;

import com.autotest.dto.MetricCollectConfig;
import com.autotest.entity.Metric;
import com.autotest.entity.Server;
import com.autotest.entity.Task;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 指标采集调度器
 */
@Slf4j
@Service
public class MetricScheduler {

    private final MetricCollectorService collectorService;
    private final MetricService metricService;
    private final ThreadPoolTaskScheduler taskScheduler;

    // 运行中的采集任务：taskId -> ScheduledFuture
    private final Map<Long, ScheduledFuture<?>> runningCollectors = new ConcurrentHashMap<>();
    
    // 采集任务信息：taskId -> 服务器列表
    private final Map<Long, List<Server>> taskServers = new ConcurrentHashMap<>();

    @Autowired
    public MetricScheduler(MetricCollectorService collectorService, MetricService metricService) {
        this.collectorService = collectorService;
        this.metricService = metricService;
        
        // 初始化调度器
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(50);
        scheduler.setThreadNamePrefix("metric-collector-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.initialize();
        this.taskScheduler = scheduler;
    }

    /**
     * 启动指标采集（任务开始时调用）
     */
    public void startCollection(Task task, List<Server> servers) {
        if (task.getCollectEnabled() == null || !task.getCollectEnabled()) {
            log.info("任务 {} 未启用指标采集", task.getId());
            return;
        }

        if (servers == null || servers.isEmpty()) {
            log.warn("任务 {} 没有关联服务器，无法启动采集", task.getId());
            return;
        }

        MetricCollectConfig config = MetricCollectConfig.fromMap(task.getCollectConfig());
        if (config == null || config.getEnabled() == null || !config.getEnabled()) {
            log.info("任务 {} 采集配置为禁用状态", task.getId());
            return;
        }

        long frequencyMs = parseFrequency(config.getFrequency());
        
        // 保存服务器列表
        taskServers.put(task.getId(), servers);

        // 为每个服务器启动采集任务
        for (Server server : servers) {
            String key = task.getId() + "_" + server.getId();
            
            ScheduledFuture<?> future = taskScheduler.scheduleAtFixedRate(() -> {
                try {
                    // 采集指标
                    List<Metric> metrics = collectorService.collectMetrics(server, config);
                    
                    // 设置关联信息
                    for (Metric metric : metrics) {
                        metric.setTaskId(task.getId());
                    }
                    
                    // 批量保存
                    if (!metrics.isEmpty()) {
                        metricService.saveBatch(metrics);
                        log.debug("采集任务 {} 保存 {} 条指标", key, metrics.size());
                    }
                    
                } catch (Exception e) {
                    log.warn("采集任务 {} 指标失败：{}", key, e.getMessage());
                }
            }, frequencyMs);
            
            runningCollectors.put(task.getId(), future);
            log.info("启动采集任务：{}, 服务器：{}, 频率：{}ms", task.getId(), server.getName(), frequencyMs);
        }
    }

    /**
     * 停止指标采集（任务结束时调用）
     */
    public void stopCollection(Long taskId) {
        ScheduledFuture<?> future = runningCollectors.remove(taskId);
        if (future != null) {
            future.cancel(false);
            log.info("停止采集任务：{}", taskId);
        }
        
        // 清理服务器列表
        taskServers.remove(taskId);
    }

    /**
     * 检查采集任务是否运行中
     */
    public boolean isCollecting(Long taskId) {
        return runningCollectors.containsKey(taskId);
    }

    /**
     * 获取运行中的采集任务数量
     */
    public int getRunningCount() {
        return runningCollectors.size();
    }

    /**
     * 解析频率配置
     */
    private long parseFrequency(String frequency) {
        if (frequency == null) {
            return 5000; // 默认 5 秒
        }
        
        switch (frequency) {
            case "1s": return 1000;
            case "5s": return 5000;
            case "10s": return 10000;
            case "30s": return 30000;
            case "1min": return 60000;
            default: 
                // 尝试解析数字（单位：秒）
                try {
                    int seconds = Integer.parseInt(frequency.replace("s", ""));
                    return seconds * 1000L;
                } catch (NumberFormatException e) {
                    return 5000;
                }
        }
    }

    /**
     * 获取所有运行中的采集任务信息
     */
    public Map<Long, List<String>> getRunningTasksInfo() {
        Map<Long, List<String>> info = new ConcurrentHashMap<>();
        taskServers.forEach((taskId, servers) -> {
            List<String> serverNames = servers.stream()
                .map(Server::getName)
                .toList();
            info.put(taskId, serverNames);
        });
        return info;
    }
}
