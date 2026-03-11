-- =====================================================
-- 自动化测试管理平台 - 数据库初始化脚本
-- 数据库: test_platform
-- 版本: 1.1.0
-- 最后更新: 2026-03-11
-- =====================================================

-- 创建数据库（如果不存在）
-- CREATE DATABASE test_platform;

-- \c test_platform

-- =====================================================
-- 公共函数
-- =====================================================

-- 自动更新 updated_at 字段的触发器函数
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- 服务器管理表
-- =====================================================

-- 服务器表
CREATE TABLE IF NOT EXISTS servers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    host VARCHAR(255) NOT NULL,
    port INT DEFAULT 22,
    username VARCHAR(100),
    password VARCHAR(255),
    private_key TEXT,
    os_type VARCHAR(50),
    description TEXT,
    group_id BIGINT,
    status VARCHAR(20) DEFAULT 'active',
    tags JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_servers_status ON servers(status);
CREATE INDEX IF NOT EXISTS idx_servers_group_id ON servers(group_id);

COMMENT ON TABLE servers IS '测试服务器';
COMMENT ON COLUMN servers.name IS '服务器名称';
COMMENT ON COLUMN servers.host IS '主机地址';
COMMENT ON COLUMN servers.port IS 'SSH端口';
COMMENT ON COLUMN servers.status IS '状态：active/inactive';

-- 服务器分组表
CREATE TABLE IF NOT EXISTS server_groups (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    parent_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- 脚本管理表
-- =====================================================

-- 脚本表
CREATE TABLE IF NOT EXISTS scripts (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    test_category VARCHAR(50),
    runtime_environment VARCHAR(50) DEFAULT 'bash',
    has_deploy BOOLEAN DEFAULT FALSE,
    has_cleanup BOOLEAN DEFAULT FALSE,
    status VARCHAR(20) DEFAULT 'draft',
    current_version VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_scripts_status ON scripts(status);
CREATE INDEX IF NOT EXISTS idx_scripts_test_category ON scripts(test_category);

COMMENT ON TABLE scripts IS '测试脚本';
COMMENT ON COLUMN scripts.test_category IS '测试分类：cpu/memory/disk/network/mixed/database/middleware/java/storage/bigdata';

-- 脚本版本表
CREATE TABLE IF NOT EXISTS script_versions (
    id BIGSERIAL PRIMARY KEY,
    script_id BIGINT NOT NULL,
    version VARCHAR(50) NOT NULL,
    entry_function VARCHAR(100),
    run_content TEXT,
    deploy_content TEXT,
    cleanup_content TEXT,
    params JSONB,
    roles JSONB,
    changelog TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (script_id) REFERENCES scripts(id) ON DELETE CASCADE,
    UNIQUE(script_id, version)
);

CREATE INDEX IF NOT EXISTS idx_script_versions_script_id ON script_versions(script_id);

-- =====================================================
-- 资源文件管理表
-- =====================================================

-- 资源文件表
CREATE TABLE IF NOT EXISTS resource_files (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    file_type VARCHAR(50),
    category VARCHAR(50),
    checksum VARCHAR(32) NOT NULL,
    description TEXT,
    created_by VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(checksum)
);

CREATE INDEX IF NOT EXISTS idx_resource_files_category ON resource_files(category);
CREATE INDEX IF NOT EXISTS idx_resource_files_file_type ON resource_files(file_type);

COMMENT ON TABLE resource_files IS '资源文件表';
COMMENT ON COLUMN resource_files.checksum IS 'MD5校验值，防止重复上传';

-- 脚本资源关联表
CREATE TABLE IF NOT EXISTS script_resources (
    id BIGSERIAL PRIMARY KEY,
    script_id BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    target_path VARCHAR(255) NOT NULL DEFAULT '/tmp',
    permissions VARCHAR(10) DEFAULT '644',
    upload_order INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (script_id) REFERENCES scripts(id) ON DELETE CASCADE,
    FOREIGN KEY (resource_id) REFERENCES resource_files(id) ON DELETE CASCADE,
    UNIQUE(script_id, resource_id)
);

CREATE INDEX IF NOT EXISTS idx_script_resources_script_id ON script_resources(script_id);

-- =====================================================
-- 任务管理表
-- =====================================================

-- 任务表
CREATE TABLE IF NOT EXISTS tasks (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    script_id BIGINT NOT NULL,
    script_version VARCHAR(50),
    execution_mode VARCHAR(20) DEFAULT 'immediate',
    parallel_mode VARCHAR(20) DEFAULT 'parallel',
    scheduled_time TIMESTAMP,
    lifecycle_config JSONB,
    param_values JSONB,
    status VARCHAR(20) DEFAULT 'pending',
    deploy_status VARCHAR(20),
    run_status VARCHAR(20),
    cleanup_status VARCHAR(20),
    total_servers INT DEFAULT 0,
    completed_servers INT DEFAULT 0,
    passed_servers INT DEFAULT 0,
    failed_servers INT DEFAULT 0,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tasks_status ON tasks(status);
CREATE INDEX IF NOT EXISTS idx_tasks_script_id ON tasks(script_id);

COMMENT ON TABLE tasks IS '测试任务';

-- 任务服务器关联表
CREATE TABLE IF NOT EXISTS task_servers (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    server_id BIGINT NOT NULL,
    role VARCHAR(50) DEFAULT 'default',
    status VARCHAR(20) DEFAULT 'pending',
    deploy_status VARCHAR(20),
    run_status VARCHAR(20),
    cleanup_status VARCHAR(20),
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    FOREIGN KEY (server_id) REFERENCES servers(id) ON DELETE CASCADE,
    UNIQUE(task_id, server_id, role)
);

CREATE INDEX IF NOT EXISTS idx_task_servers_task_id ON task_servers(task_id);
CREATE INDEX IF NOT EXISTS idx_task_servers_server_id ON task_servers(server_id);

-- 定时任务表
CREATE TABLE IF NOT EXISTS scheduled_tasks (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    script_id BIGINT NOT NULL,
    script_version VARCHAR(50),
    cron_expression VARCHAR(100) NOT NULL,
    server_ids JSONB,
    param_values JSONB,
    lifecycle_config JSONB,
    enabled BOOLEAN DEFAULT TRUE,
    last_run_time TIMESTAMP,
    next_run_time TIMESTAMP,
    last_run_status VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_scheduled_tasks_enabled ON scheduled_tasks(enabled);
CREATE INDEX IF NOT EXISTS idx_scheduled_tasks_next_run ON scheduled_tasks(next_run_time);

-- =====================================================
-- 测试结果表
-- =====================================================

-- 测试结果表
CREATE TABLE IF NOT EXISTS test_results (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    server_id BIGINT NOT NULL,
    task_server_id BIGINT,
    result VARCHAR(20),
    result_reason TEXT,
    overall_score INT,
    raw_output TEXT,
    raw_error TEXT,
    metrics JSONB,
    parsed_data JSONB,
    output_files JSONB,
    exit_code INT,
    duration_ms INT,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    FOREIGN KEY (server_id) REFERENCES servers(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_test_results_task_id ON test_results(task_id);
CREATE INDEX IF NOT EXISTS idx_test_results_server_id ON test_results(server_id);
CREATE INDEX IF NOT EXISTS idx_test_results_result ON test_results(result);

-- =====================================================
-- 解析规则表
-- =====================================================

-- 解析规则表
CREATE TABLE IF NOT EXISTS result_rules (
    id BIGSERIAL PRIMARY KEY,
    script_id BIGINT,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    parser_type VARCHAR(20) NOT NULL,
    builtin_format VARCHAR(50),
    script_source VARCHAR(20),
    script_path VARCHAR(255),
    script_content TEXT,
    script_language VARCHAR(50),
    input_source VARCHAR(20) DEFAULT 'stdout',
    file_pattern VARCHAR(255),
    output_format VARCHAR(50),
    enabled BOOLEAN DEFAULT TRUE,
    priority INT DEFAULT 0,
    rules JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_result_rules_script_id ON result_rules(script_id);
CREATE INDEX IF NOT EXISTS idx_result_rules_enabled ON result_rules(enabled);

COMMENT ON TABLE result_rules IS '结果解析规则';

-- =====================================================
-- 指标定义表
-- =====================================================

-- 指标定义表
CREATE TABLE IF NOT EXISTS metric_definitions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    category VARCHAR(50) NOT NULL,
    unit VARCHAR(50),
    description TEXT,
    baseline_config JSONB,
    compare_mode VARCHAR(20) DEFAULT 'higher_better',
    applicable_categories JSONB,
    data_type VARCHAR(20),
    extract_rule TEXT,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_metric_definitions_category ON metric_definitions(category);

-- =====================================================
-- 报告表
-- =====================================================

-- 报告表
CREATE TABLE IF NOT EXISTS reports (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    task_id BIGINT,
    report_type VARCHAR(50),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    summary JSONB,
    details JSONB,
    status VARCHAR(20) DEFAULT 'generating',
    file_path VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_reports_task_id ON reports(task_id);

-- =====================================================
-- 系统配置表
-- =====================================================

-- 系统配置表
CREATE TABLE IF NOT EXISTS system_config (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- 触发器
-- =====================================================

-- 为需要自动更新的表创建触发器
CREATE OR REPLACE TRIGGER update_servers_updated_at
    BEFORE UPDATE ON servers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE TRIGGER update_scripts_updated_at
    BEFORE UPDATE ON scripts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE TRIGGER update_tasks_updated_at
    BEFORE UPDATE ON tasks
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE TRIGGER update_resource_files_updated_at
    BEFORE UPDATE ON resource_files
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE TRIGGER update_scheduled_tasks_updated_at
    BEFORE UPDATE ON scheduled_tasks
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE TRIGGER update_metric_definitions_updated_at
    BEFORE UPDATE ON metric_definitions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE TRIGGER update_result_rules_updated_at
    BEFORE UPDATE ON result_rules
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =====================================================
-- 初始数据
-- =====================================================

-- 插入默认系统配置
INSERT INTO system_config (config_key, config_value, description) VALUES
    ('max_upload_size', '21474836480', '最大上传文件大小（字节），默认20GB'),
    ('script_timeout', '3600', '脚本执行超时时间（秒），默认1小时'),
    ('parallel_tasks', '10', '最大并行任务数'),
    ('log_retention_days', '30', '日志保留天数')
ON CONFLICT (config_key) DO NOTHING;

-- 插入默认指标定义
INSERT INTO metric_definitions (name, display_name, category, unit, compare_mode) VALUES
    ('throughput', '吞吐量', 'performance', 'ops/s', 'higher_better'),
    ('latency_avg', '平均延迟', 'performance', 'ms', 'lower_better'),
    ('latency_p99', 'P99延迟', 'performance', 'ms', 'lower_better'),
    ('latency_p95', 'P95延迟', 'performance', 'ms', 'lower_better'),
    ('success_rate', '成功率', 'reliability', '%', 'higher_better'),
    ('error_rate', '错误率', 'reliability', '%', 'lower_better'),
    ('cpu_usage', 'CPU使用率', 'resource', '%', 'lower_better'),
    ('memory_usage', '内存使用率', 'resource', '%', 'lower_better'),
    ('disk_io_read', '磁盘读取速率', 'resource', 'MB/s', 'higher_better'),
    ('disk_io_write', '磁盘写入速率', 'resource', 'MB/s', 'higher_better'),
    ('network_in', '网络入流量', 'network', 'MB/s', 'higher_better'),
    ('network_out', '网络出流量', 'network', 'MB/s', 'higher_better')
ON CONFLICT DO NOTHING;

-- =====================================================
-- Flyway 版本记录表（用于版本管理）
-- =====================================================

CREATE TABLE IF NOT EXISTS flyway_schema_history (
    installed_rank INT NOT NULL,
    version VARCHAR(50),
    description VARCHAR(200) NOT NULL,
    type VARCHAR(20) NOT NULL,
    script VARCHAR(1000) NOT NULL,
    checksum INT,
    installed_by VARCHAR(100) NOT NULL,
    installed_on TIMESTAMP NOT NULL DEFAULT now(),
    execution_time INT NOT NULL,
    success BOOLEAN NOT NULL,
    PRIMARY KEY (installed_rank)
);

-- =====================================================
-- 完成
-- =====================================================

-- 授权（根据实际情况修改用户名）
-- GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO postgres;
-- GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO postgres;
