package com.autotest.controller;

import com.autotest.common.ApiResponse;
import com.autotest.entity.Task;
import com.autotest.entity.TestResult;
import com.autotest.mapper.ServerMapper;
import com.autotest.mapper.ScriptMapper;
import com.autotest.mapper.TaskMapper;
import com.autotest.mapper.TestResultMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    @Autowired
    private ServerMapper serverMapper;

    @Autowired
    private ScriptMapper scriptMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private TestResultMapper testResultMapper;

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();

        // 服务器总数
        Long serverCount = serverMapper.selectCount(null);
        stats.put("serverCount", serverCount);

        // 脚本总数
        Long scriptCount = scriptMapper.selectCount(null);
        stats.put("scriptCount", scriptCount);

        // 执行中任务数
        Long runningTasks = taskMapper.selectCount(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Task>()
                .eq("status", "running")
        );
        stats.put("runningTasks", runningTasks);

        // 成功率计算
        Long totalResults = testResultMapper.selectCount(null);
        Long passedResults = testResultMapper.selectCount(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TestResult>()
                .eq("result", "pass")
        );
        double successRate = totalResults > 0 ? (passedResults * 100.0 / totalResults) : 0;
        stats.put("successRate", Math.round(successRate * 10) / 10.0); // 保留一位小数

        return ApiResponse.success(stats);
    }

    @GetMapping("/recent-tasks")
    public ApiResponse<List<Task>> getRecentTasks() {
        // 获取最近 5 个任务
        List<Task> tasks = taskMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Task>()
                .orderByDesc("created_at")
                .last("LIMIT 5")
        );
        return ApiResponse.success(tasks);
    }
}
