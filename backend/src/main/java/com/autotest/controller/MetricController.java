package com.autotest.controller;

import com.autotest.entity.Metric;
import com.autotest.service.MetricService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 指标数据控制器
 */
@Slf4j
@RestController
@RequestMapping("/metrics")
@RequiredArgsConstructor
public class MetricController {

    private final MetricService metricService;

    /**
     * 获取任务的时序指标数据
     */
    @GetMapping("/tasks/{taskId}/timeseries")
    public Map<String, Object> getTaskMetricsTimeseries(
        @PathVariable Long taskId,
        @RequestParam(required = false) String metricType,
        @RequestParam(required = false) LocalDateTime startTime,
        @RequestParam(required = false) LocalDateTime endTime
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        try {
            LambdaQueryWrapper<Metric> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Metric::getTaskId, taskId);
            
            if (metricType != null && !metricType.isEmpty()) {
                wrapper.eq(Metric::getMetricType, metricType);
            }
            
            if (startTime != null) {
                wrapper.ge(Metric::getTimestamp, startTime);
            }
            
            if (endTime != null) {
                wrapper.le(Metric::getTimestamp, endTime);
            }
            
            wrapper.orderByAsc(Metric::getTimestamp);
            
            List<Metric> metrics = metricService.list(wrapper);
            
            // 按指标类型和名称分组
            Map<String, Map<String, List<Metric>>> grouped = metrics.stream()
                .collect(Collectors.groupingBy(
                    Metric::getMetricType,
                    Collectors.groupingBy(Metric::getMetricName)
                ));
            
            // 转换为前端友好的格式
            List<Map<String, Object>> series = new ArrayList<>();
            List<String> timestamps = new ArrayList<>();
            
            // 提取所有时间戳（去重）
            List<String> uniqueTimestamps = metrics.stream()
                .map(m -> m.getTimestamp().toString())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
            timestamps.addAll(uniqueTimestamps);
            
            // 为每个指标创建系列
            for (Map.Entry<String, Map<String, List<Metric>>> typeEntry : grouped.entrySet()) {
                String type = typeEntry.getKey();
                
                for (Map.Entry<String, List<Metric>> nameEntry : typeEntry.getValue().entrySet()) {
                    String name = nameEntry.getKey();
                    List<Metric> metricList = nameEntry.getValue();
                    
                    Map<String, Object> seriesData = new LinkedHashMap<>();
                    seriesData.put("type", type);
                    seriesData.put("name", name);
                    seriesData.put("unit", metricList.get(0).getUnit());
                    
                    List<Double> values = new ArrayList<>(uniqueTimestamps.size());
                    Map<String, Double> timeValueMap = metricList.stream()
                        .collect(Collectors.toMap(
                            m -> m.getTimestamp().toString(),
                            Metric::getValue,
                            (v1, v2) -> v1
                        ));
                    
                    for (String ts : uniqueTimestamps) {
                        values.add(timeValueMap.getOrDefault(ts, null));
                    }
                    
                    seriesData.put("data", values);
                    series.add(seriesData);
                }
            }
            
            result.put("code", 0);
            result.put("message", "success");
            result.put("data", Map.of(
                "timestamps", timestamps,
                "series", series,
                "total", metrics.size()
            ));
            
        } catch (Exception e) {
            log.error("获取时序指标数据失败", e);
            result.put("code", 500);
            result.put("message", "获取数据失败：" + e.getMessage());
            result.put("data", Map.of(
                "timestamps", new ArrayList<>(),
                "series", new ArrayList<>(),
                "total", 0
            ));
        }
        
        return result;
    }

    /**
     * 获取任务的指标统计摘要
     */
    @GetMapping("/tasks/{taskId}/summary")
    public Map<String, Object> getTaskMetricsSummary(@PathVariable Long taskId) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        try {
            LambdaQueryWrapper<Metric> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Metric::getTaskId, taskId);
            
            List<Metric> metrics = metricService.list(wrapper);
            
            // 按类型统计
            Map<String, Map<String, Map<String, Object>>> stats = new LinkedHashMap<>();
            
            for (Metric metric : metrics) {
                String type = metric.getMetricType();
                String name = metric.getMetricName();
                Double value = metric.getValue();
                
                stats.computeIfAbsent(type, k -> new LinkedHashMap<>())
                    .compute(name, (k, v) -> {
                        if (v == null) {
                            Map<String, Object> stat = new LinkedHashMap<>();
                            stat.put("count", 1);
                            stat.put("min", value);
                            stat.put("max", value);
                            stat.put("sum", value);
                            stat.put("unit", metric.getUnit());
                            return stat;
                        } else {
                            Integer count = (Integer) v.get("count");
                            Double min = (Double) v.get("min");
                            Double max = (Double) v.get("max");
                            Double sum = (Double) v.get("sum");
                            
                            v.put("count", count + 1);
                            v.put("min", Math.min(min, value));
                            v.put("max", Math.max(max, value));
                            v.put("sum", sum + value);
                            return v;
                        }
                    });
            }
            
            // 计算平均值
            for (Map<String, Map<String, Object>> typeStats : stats.values()) {
                for (Map.Entry<String, Map<String, Object>> entry : typeStats.entrySet()) {
                    Map<String, Object> stat = entry.getValue();
                    Integer count = (Integer) stat.get("count");
                    Double sum = (Double) stat.get("sum");
                    stat.put("avg", sum / count);
                }
            }
            
            result.put("code", 0);
            result.put("message", "success");
            result.put("data", Map.of(
                "totalPoints", metrics.size(),
                "byType", stats
            ));
            
        } catch (Exception e) {
            log.error("获取指标统计失败", e);
            result.put("code", 500);
            result.put("message", "获取统计失败：" + e.getMessage());
            result.put("data", Map.of(
                "totalPoints", 0,
                "byType", new LinkedHashMap<>()
            ));
        }
        
        return result;
    }

    /**
     * 获取服务器的实时指标（最新一条）
     */
    @GetMapping("/servers/{serverId}/latest")
    public Map<String, Object> getServerLatestMetrics(
        @PathVariable Long serverId,
        @RequestParam(required = false) String metricType
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        try {
            LambdaQueryWrapper<Metric> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Metric::getServerId, serverId);
            
            if (metricType != null && !metricType.isEmpty()) {
                wrapper.eq(Metric::getMetricType, metricType);
            }
            
            wrapper.orderByDesc(Metric::getTimestamp);
            wrapper.last("LIMIT 100");
            
            List<Metric> metrics = metricService.list(wrapper);
            
            result.put("code", 0);
            result.put("message", "success");
            result.put("data", metrics);
            
        } catch (Exception e) {
            log.error("获取服务器最新指标失败", e);
            result.put("code", 500);
            result.put("message", "获取数据失败：" + e.getMessage());
            result.put("data", new ArrayList<>());
        }
        
        return result;
    }
}
