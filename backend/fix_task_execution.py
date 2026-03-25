import re

file_path = r"C:\Users\Administrator\.openclaw\workspace\products\auto-test-platform\backend\src\main\java\com\autotest\service\TaskExecutionService.java"

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. 添加字段
content = content.replace(
    'private final ResourceFileMapper resourceFileMapper;',
    'private final ResourceFileMapper resourceFileMapper;\n    private final MetricScheduler metricScheduler;'
)

# 2. 添加启动采集代码
content = content.replace(
    '// 资源上传阶段',
    '''// ===== 启动指标采集 =====
            if (task.getCollectEnabled() != null && task.getCollectEnabled()) {
                List<Server> servers = taskServers.stream()
                    .map(ts -> serverMapper.selectById(ts.getServerId()))
                    .toList();
                metricScheduler.startCollection(task, servers);
                context.log("[INFO] 已启动性能指标采集");
            }

            // 资源上传阶段'''
)

# 3. 添加停止采集代码  
content = content.replace(
    '} finally {\n            runningTasks.remove(taskId);',
    '''} finally {
            // ===== 停止指标采集 =====
            if (task.getCollectEnabled() != null && task.getCollectEnabled()) {
                metricScheduler.stopCollection(taskId);
                context.log("[INFO] 已停止性能指标采集");
            }
            runningTasks.remove(taskId);'''
)

with open(file_path, 'w', encoding='utf-8', newline='') as f:
    f.write(content)

print("修改完成")
