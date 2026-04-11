-- ============================================================
-- 自动化测试管理平台 - 初始化数据脚本
-- 版本：v2.0.0
-- 数据库：SQLite 3.38+
-- 更新日期：2026-04-10
-- 说明：首次部署时初始化系统配置
-- ============================================================

-- ============================================================
-- 系统配置
-- ============================================================

-- SSH 连接超时（毫秒）
INSERT OR IGNORE INTO system_config (key, value, description, updated_at)
VALUES ('ssh_timeout', '60000', 'SSH 连接超时时间（毫秒）', datetime('now', 'localtime'));

-- SSH 执行超时（秒）
INSERT OR IGNORE INTO system_config (key, value, description, updated_at)
VALUES ('exec_timeout', '86400', 'SSH 命令执行超时时间（秒）', datetime('now', 'localtime'));

-- 最大并行任务数
INSERT OR IGNORE INTO system_config (key, value, description, updated_at)
VALUES ('max_parallel_tasks', '10', '最大并行执行任务数', datetime('now', 'localtime'));

-- 日志保留天数
INSERT OR IGNORE INTO system_config (key, value, description, updated_at)
VALUES ('log_keep_days', '30', '任务日志保留天数', datetime('now', 'localtime'));

-- 报告保留天数
INSERT OR IGNORE INTO system_config (key, value, description, updated_at)
VALUES ('report_keep_days', '90', '测试报告保留天数', datetime('now', 'localtime'));

-- 指标保留天数
INSERT OR IGNORE INTO system_config (key, value, description, updated_at)
VALUES ('metric_keep_days', '180', '性能指标保留天数', datetime('now', 'localtime'));

-- 默认指标采集间隔（秒）
INSERT OR IGNORE INTO system_config (key, value, description, updated_at)
VALUES ('metric_collect_interval', '5', '默认指标采集间隔（秒）', datetime('now', 'localtime'));

-- 平台版本
INSERT OR IGNORE INTO system_config (key, value, description, updated_at)
VALUES ('platform_version', '2.0.0', '平台版本号', datetime('now', 'localtime'));

-- ============================================================
-- 指标定义（内置）
-- ============================================================

-- CPU 使用率
INSERT OR IGNORE INTO metric_definitions (name, display_name, category, unit, description, data_type, comparison_mode, enabled, created_at)
VALUES ('cpu_usage', 'CPU 使用率', 'system', '%', 'CPU 使用率百分比', 'double', 'lower_better', 1, datetime('now', 'localtime'));

-- 内存使用率
INSERT OR IGNORE INTO metric_definitions (name, display_name, category, unit, description, data_type, comparison_mode, enabled, created_at)
VALUES ('memory_usage', '内存使用率', 'system', '%', '内存使用率百分比', 'double', 'lower_better', 1, datetime('now', 'localtime'));

-- 磁盘使用率
INSERT OR IGNORE INTO metric_definitions (name, display_name, category, unit, description, data_type, comparison_mode, enabled, created_at)
VALUES ('disk_usage', '磁盘使用率', 'system', '%', '磁盘使用率百分比', 'double', 'lower_better', 1, datetime('now', 'localtime'));

-- 网络带宽
INSERT OR IGNORE INTO metric_definitions (name, display_name, category, unit, description, data_type, comparison_mode, enabled, created_at)
VALUES ('network_bandwidth', '网络带宽', 'network', 'Mbps', '网络传输带宽', 'double', 'higher_better', 1, datetime('now', 'localtime'));

-- IOPS
INSERT OR IGNORE INTO metric_definitions (name, display_name, category, unit, description, data_type, comparison_mode, enabled, created_at)
VALUES ('disk_iops', '磁盘 IOPS', 'storage', 'IOPS', '磁盘每秒 IO 操作数', 'double', 'higher_better', 1, datetime('now', 'localtime'));

-- 响应时间
INSERT OR IGNORE INTO metric_definitions (name, display_name, category, unit, description, data_type, comparison_mode, enabled, created_at)
VALUES ('response_time', '响应时间', 'performance', 'ms', '操作响应时间', 'double', 'lower_better', 1, datetime('now', 'localtime'));

-- 吞吐量
INSERT OR IGNORE INTO metric_definitions (name, display_name, category, unit, description, data_type, comparison_mode, enabled, created_at)
VALUES ('throughput', '吞吐量', 'performance', 'TPS', '每秒处理事务数', 'double', 'higher_better', 1, datetime('now', 'localtime'));

-- ============================================================
-- 结果判定规则（内置）
-- ============================================================

-- JSON 格式解析规则
INSERT OR IGNORE INTO result_rules (name, description, parser_type, builtin_format, input_source, output_format, enabled, created_at)
VALUES ('json_parser', 'JSON 格式结果解析', 'builtin', 'json', 'stdout', 'json', 1, datetime('now', 'localtime'));

-- Key-Value 格式解析规则
INSERT OR IGNORE INTO result_rules (name, description, parser_type, builtin_format, input_source, output_format, enabled, created_at)
VALUES ('kv_parser', 'Key-Value 格式结果解析', 'builtin', 'kv', 'stdout', 'json', 1, datetime('now', 'localtime'));

-- 正则表达式解析规则
INSERT OR IGNORE INTO result_rules (name, description, parser_type, builtin_format, input_source, output_format, enabled, created_at)
VALUES ('regex_parser', '正则表达式结果解析', 'builtin', 'regex', 'stdout', 'json', 1, datetime('now', 'localtime'));

-- ============================================================
-- 初始化完成
-- ============================================================