# 自动化测试平台 - 部署初始化指南

## 部署前准备

### 1. 环境要求

- Java 17+ (OpenJDK/Temurin)
- SQLite 3.38+ (嵌入式，无需单独安装)
- 足够的磁盘空间（用于脚本存储、报告存储）

### 2. 目录创建

首次部署前，需要创建以下目录：

```bash
# Windows
mkdir C:\data\auto-test\scripts
mkdir C:\data\auto-test\reports
mkdir C:\data\auto-test\temp
mkdir C:\data\auto-test\results

# Linux
mkdir -p /data/auto-test/scripts
mkdir -p /data/auto-test/reports
mkdir -p /data/auto-test/temp
mkdir -p /data/auto-test/results
```

### 3. 配置调整

根据实际部署环境，修改 `application.yml` 中的存储路径：

```yaml
autotest:
  storage:
    scripts-path: /data/auto-test/scripts    # Linux
    reports-path: /data/auto-test/reports
    temp-path: /data/auto-test/temp
    results-path: /data/auto-test/results
```

## 数据库初始化

### 自动初始化（推荐）

首次启动时，Spring Boot 会自动执行：

1. `schema.sql` - 创建 18 张数据表
2. `data.sql` - 插入系统默认配置

数据库文件位置：`~/.autotest/test_platform.db`

### 手动初始化（可选）

如果需要手动初始化数据库：

```bash
# Windows
sqlite3 %USERPROFILE%\.autotest\test_platform.db < schema.sql
sqlite3 %USERPROFILE%\.autotest\test_platform.db < data.sql

# Linux
sqlite3 ~/.autotest/test_platform.db < schema.sql
sqlite3 ~/.autotest/test_platform.db < data.sql
```

## 初始化数据说明

### 系统配置（system_config）

| Key | Value | 说明 |
|-----|-------|------|
| ssh_timeout | 60000 | SSH 连接超时（毫秒） |
| exec_timeout | 86400 | SSH 命令执行超时（秒） |
| max_parallel_tasks | 10 | 最大并行任务数 |
| log_keep_days | 30 | 日志保留天数 |
| report_keep_days | 90 | 报告保留天数 |
| metric_keep_days | 180 | 指标保留天数 |
| metric_collect_interval | 5 | 指标采集间隔（秒） |
| platform_version | 2.0.0 | 平台版本号 |

### 指标定义（metric_definitions）

内置 7 个常用指标定义：

- cpu_usage - CPU 使用率
- memory_usage - 内存使用率
- disk_usage - 磁盘使用率
- network_bandwidth - 网络带宽
- disk_iops - 磁盘 IOPS
- response_time - 响应时间
- throughput - 吞吐量

### 结果解析规则（result_rules）

内置 3 种解析规则：

- json_parser - JSON 格式解析
- kv_parser - Key-Value 格式解析
- regex_parser - 正则表达式解析

## 部署后验证

### 1. 检查数据库

```bash
sqlite3 ~/.autotest/test_platform.db "SELECT name FROM sqlite_master WHERE type='table';"
```

应显示 18 张表。

### 2. 检查系统配置

```bash
sqlite3 ~/.autotest/test_platform.db "SELECT * FROM system_config;"
```

应显示 8 条配置记录。

### 3. 访问平台

- 前端：http://localhost:8080
- API：http://localhost:8080/api/v1
- Swagger：http://localhost:8080/api/v1/swagger-ui.html

## 注意事项

1. **重复启动**：schema.sql 使用 `CREATE TABLE IF NOT EXISTS`，data.sql 使用 `INSERT OR IGNORE`，安全支持重复执行

2. **数据备份**：SQLite 数据库文件可直接复制备份

3. **数据库迁移**：从 PostgreSQL 迁移的数据需使用单独的迁移工具（已完成）

4. **重新初始化**：如需重新初始化，删除 `~/.autotest/test_platform.db` 后重启服务

## 文件清单

| 文件 | 说明 |
|------|------|
| schema.sql | 数据库表结构（18 张表） |
| data.sql | 初始化数据（配置、指标定义、解析规则） |
| application.yml | 应用配置（存储路径、超时等） |