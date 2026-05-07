-- ============================================================
-- 自动化测试管理平台 - SQLite 数据库初始化脚本
-- 版本：v2.0.0
-- 数据库：SQLite 3.38+
-- 更新日期：2026-04-10
-- 说明：从 PostgreSQL 迁移，JSONB 改为 TEXT 存储
-- ============================================================

-- 启用外键约束
PRAGMA foreign_keys = ON;

-- ============================================================
-- 服务器相关表
-- ============================================================

-- 服务器分组表
CREATE TABLE IF NOT EXISTS server_groups (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 服务器表
CREATE TABLE IF NOT EXISTS servers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(100) NOT NULL,
    host VARCHAR(255) NOT NULL,
    port INTEGER DEFAULT 22 NOT NULL,
    username VARCHAR(50) NOT NULL,
    auth_type VARCHAR(20) NOT NULL,
    auth_secret TEXT,
    os_type VARCHAR(50),
    os_version VARCHAR(500),
    cpu_cores INTEGER,
    cpu_model VARCHAR(200),
    cpu_arch VARCHAR(50),
    memory_size VARCHAR(20),
    memory_total_mb INTEGER,
    disk_info TEXT,  -- JSON
    group_id BIGINT REFERENCES server_groups(id) ON DELETE SET NULL,
    tags TEXT DEFAULT '[]',  -- JSON Array
    remark TEXT,
    status VARCHAR(20) DEFAULT 'offline' NOT NULL,
    enabled BOOLEAN DEFAULT 1,  -- 是否启用（禁用后不出现在任务服务器选择列表中）
    last_check_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_servers_name ON servers(name);
CREATE INDEX IF NOT EXISTS idx_servers_host ON servers(host);
CREATE INDEX IF NOT EXISTS idx_servers_status ON servers(status);
CREATE INDEX IF NOT EXISTS idx_servers_group_id ON servers(group_id);

-- ============================================================
-- 脚本相关表
-- ============================================================

-- 测试脚本表
CREATE TABLE IF NOT EXISTS scripts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(100) NOT NULL UNIQUE,
    script_type VARCHAR(20) NOT NULL,
    test_category VARCHAR(50) NOT NULL,
    description TEXT,
    current_version VARCHAR(20) DEFAULT 'v1.0.0' NOT NULL,
    lifecycle_mode VARCHAR(20) DEFAULT 'simple' NOT NULL,
    has_deploy BOOLEAN DEFAULT 0,
    has_cleanup BOOLEAN DEFAULT 0,
    deploy_entry VARCHAR(255),
    cleanup_entry VARCHAR(255),
    entry_file VARCHAR(255),
    file_list TEXT,  -- JSON
    parse_rules TEXT,  -- JSON
    default_timeout INTEGER DEFAULT 3600,
    default_retry INTEGER DEFAULT 0,
    is_builtin BOOLEAN DEFAULT 0,
    status VARCHAR(20) DEFAULT 'enabled' NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_scripts_name ON scripts(name);
CREATE INDEX IF NOT EXISTS idx_scripts_status ON scripts(status);
CREATE INDEX IF NOT EXISTS idx_scripts_test_category ON scripts(test_category);

-- 脚本版本表
CREATE TABLE IF NOT EXISTS script_versions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    script_id BIGINT NOT NULL REFERENCES scripts(id) ON DELETE CASCADE,
    version VARCHAR(20) NOT NULL,
    lifecycle_mode VARCHAR(20) DEFAULT 'simple' NOT NULL,
    has_deploy BOOLEAN DEFAULT 0,
    has_cleanup BOOLEAN DEFAULT 0,
    deploy_entry VARCHAR(255),
    cleanup_entry VARCHAR(255),
    entry_file VARCHAR(255),
    file_list TEXT NOT NULL,  -- JSON
    storage_path VARCHAR(500) NOT NULL,
    total_size BIGINT,
    file_count INTEGER DEFAULT 1,
    checksum VARCHAR(64),
    change_log TEXT,
    content TEXT,
    steps TEXT DEFAULT '{}',  -- JSON (保持顺序)
    parameters TEXT,  -- JSON
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE(script_id, version)
);

CREATE INDEX IF NOT EXISTS idx_script_versions_script_id ON script_versions(script_id);

-- 资源文件表
CREATE TABLE IF NOT EXISTS resource_files (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(255) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    file_type VARCHAR(50),
    category VARCHAR(50),
    checksum VARCHAR(32) NOT NULL UNIQUE,
    description TEXT,
    created_by VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_resource_files_category ON resource_files(category);
CREATE INDEX IF NOT EXISTS idx_resource_files_file_type ON resource_files(file_type);

-- 脚本资源关联表
CREATE TABLE IF NOT EXISTS script_resources (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    script_id BIGINT NOT NULL REFERENCES scripts(id) ON DELETE CASCADE,
    resource_id BIGINT NOT NULL REFERENCES resource_files(id) ON DELETE CASCADE,
    target_path VARCHAR(255) DEFAULT '/tmp' NOT NULL,
    permissions VARCHAR(10) DEFAULT '644',
    upload_order INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(script_id, resource_id)
);

CREATE INDEX IF NOT EXISTS idx_script_resources_script_id ON script_resources(script_id);

-- ============================================================
-- 任务相关表
-- ============================================================

-- 测试任务表
CREATE TABLE IF NOT EXISTS tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    script_id BIGINT NOT NULL REFERENCES scripts(id) ON DELETE CASCADE,
    script_version VARCHAR(20) NOT NULL,
    shared_params TEXT DEFAULT '{}',  -- JSON
    step_params TEXT,  -- JSON
    step_server_mapping TEXT,  -- JSON
    role_execution_strategy TEXT DEFAULT '{}',  -- JSON
    deploy_started_at TIMESTAMP,
    deploy_finished_at TIMESTAMP,
    cleanup_started_at TIMESTAMP,
    cleanup_finished_at TIMESTAMP,
    collect_enabled BOOLEAN DEFAULT 1,
    collect_config TEXT,  -- JSON
    execution_mode VARCHAR(20) NOT NULL,
    scheduled_time TIMESTAMP,
    parallel_mode VARCHAR(20) DEFAULT 'sequential',
    max_parallel INTEGER DEFAULT 1,
    failure_strategy VARCHAR(20) DEFAULT 'continue',
    timeout INTEGER DEFAULT 300000,
    status VARCHAR(50) DEFAULT 'pending' NOT NULL,
    progress INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_tasks_status ON tasks(status);
CREATE INDEX IF NOT EXISTS idx_tasks_script_id ON tasks(script_id);
CREATE INDEX IF NOT EXISTS idx_tasks_created_at ON tasks(created_at);
CREATE INDEX IF NOT EXISTS idx_tasks_status_created ON tasks(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_tasks_scheduled_time ON tasks(scheduled_time);

-- 任务-服务器关联表
CREATE TABLE IF NOT EXISTS task_servers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    server_id BIGINT DEFAULT NULL,
    role VARCHAR(50) DEFAULT 'default',
    role_params TEXT DEFAULT '{}',  -- JSON
    deploy_status VARCHAR(20) DEFAULT 'pending',
    deploy_started_at TIMESTAMP,
    deploy_finished_at TIMESTAMP,
    deploy_exit_code INTEGER,
    deploy_output TEXT,
    deploy_error TEXT,
    run_status VARCHAR(50) DEFAULT 'pending',
    run_started_at TIMESTAMP,
    run_finished_at TIMESTAMP,
    run_exit_code INTEGER,
    run_output TEXT,
    run_error TEXT,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    exit_code INTEGER,
    output TEXT,
    error_message TEXT,
    parsed_result TEXT,  -- JSON
    cleanup_status VARCHAR(20) DEFAULT 'pending',
    cleanup_started_at TIMESTAMP,
    cleanup_finished_at TIMESTAMP,
    cleanup_exit_code INTEGER,
    cleanup_output TEXT,
    cleanup_error TEXT,
    overall_status VARCHAR(20) DEFAULT 'pending',
    current_phase VARCHAR(20),
    current_command TEXT,
    command_started_at TIMESTAMP,
    progress INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE(task_id, server_id, role)
);

CREATE INDEX IF NOT EXISTS idx_task_servers_task_id ON task_servers(task_id);
CREATE INDEX IF NOT EXISTS idx_task_servers_server_id ON task_servers(server_id);
CREATE INDEX IF NOT EXISTS idx_task_servers_overall_status ON task_servers(overall_status);
CREATE INDEX IF NOT EXISTS idx_task_servers_run_status ON task_servers(run_status);

-- 任务步骤执行表
CREATE TABLE IF NOT EXISTS task_steps (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id BIGINT NOT NULL REFERENCES tasks(id),
    server_id BIGINT NOT NULL DEFAULT -1,  -- -1 表示本地执行，无外键约束
    step_name VARCHAR(50) NOT NULL,
    display_name VARCHAR(200),
    script VARCHAR(500),
    command TEXT,
    depends_on TEXT,  -- JSON Array
    params TEXT,  -- JSON
    status VARCHAR(20) DEFAULT 'pending',
    wait_reason VARCHAR(200),
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    exit_code INTEGER,
    output TEXT,
    error_message TEXT,
    result_collector BOOLEAN DEFAULT 0,
    parsed_result TEXT,  -- JSON
    startup_probe TEXT,  -- JSON
    probe_status VARCHAR(20),
    probe_started_at TIMESTAMP,
    probe_finished_at TIMESTAMP,
    output_files TEXT,  -- JSON
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(task_id, server_id, step_name)
);

CREATE INDEX IF NOT EXISTS idx_task_steps_task ON task_steps(task_id);
CREATE INDEX IF NOT EXISTS idx_task_steps_status ON task_steps(status);

-- ============================================================
-- 结果相关表
-- ============================================================

-- 测试结果表
CREATE TABLE IF NOT EXISTS test_results (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id BIGINT,
    task_name VARCHAR(255),
    script_name VARCHAR(255),
    server_id BIGINT,
    server_name VARCHAR(255),
    server_ip VARCHAR(100),
    task_server_id BIGINT,
    result VARCHAR(20) NOT NULL,
    result_reason TEXT,
    overall_score INTEGER,
    metrics TEXT,  -- JSON
    raw_output TEXT,
    raw_error TEXT,
    output_files TEXT,  -- JSON
    exit_code INTEGER,
    duration_ms INTEGER,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    parsed_data TEXT,  -- JSON
    parse_rule_id BIGINT,
    parse_error TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_test_results_task_id ON test_results(task_id);
CREATE INDEX IF NOT EXISTS idx_test_results_server_id ON test_results(server_id);
CREATE INDEX IF NOT EXISTS idx_test_results_result ON test_results(result);
CREATE INDEX IF NOT EXISTS idx_test_results_created_at ON test_results(created_at);

-- 结果判定规则表
CREATE TABLE IF NOT EXISTS result_rules (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    script_id BIGINT REFERENCES scripts(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    parser_type VARCHAR(20),
    builtin_format VARCHAR(20),
    script_source VARCHAR(20),
    script_path VARCHAR(500),
    script_content TEXT,
    script_language VARCHAR(20),
    input_source VARCHAR(20),
    file_pattern VARCHAR(500),
    output_format VARCHAR(20),
    rules TEXT,  -- JSON
    priority INTEGER DEFAULT 0,
    enabled BOOLEAN DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_result_rules_script_id ON result_rules(script_id);
CREATE INDEX IF NOT EXISTS idx_result_rules_enabled ON result_rules(enabled);

-- ============================================================
-- 指标相关表（单表，移除分区）
-- ============================================================

-- 指标定义表
CREATE TABLE IF NOT EXISTS metric_definitions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(200) NOT NULL,
    category VARCHAR(50) NOT NULL,
    unit VARCHAR(50),
    description TEXT,
    data_type VARCHAR(20),
    extract_rule TEXT,
    baseline_config TEXT,  -- JSON
    comparison_mode VARCHAR(20) DEFAULT 'higher_better',
    applicable_categories TEXT,  -- JSON
    enabled BOOLEAN DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_metric_definitions_name ON metric_definitions(name);
CREATE INDEX IF NOT EXISTS idx_metric_definitions_category ON metric_definitions(category);

-- 性能指标表（单表）
CREATE TABLE IF NOT EXISTS metrics (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id BIGINT NOT NULL,
    server_id BIGINT NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    metric_type VARCHAR(50) NOT NULL,
    metric_name VARCHAR(100) NOT NULL,
    value DOUBLE NOT NULL,
    unit VARCHAR(20),
    tags TEXT  -- JSON
);

CREATE INDEX IF NOT EXISTS idx_metrics_task_id ON metrics(task_id);
CREATE INDEX IF NOT EXISTS idx_metrics_server_id ON metrics(server_id);
CREATE INDEX IF NOT EXISTS idx_metrics_timestamp ON metrics(timestamp);
CREATE INDEX IF NOT EXISTS idx_metrics_task_timestamp ON metrics(task_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_metrics_type_name ON metrics(metric_type, metric_name);

-- ============================================================
-- 定时任务表
-- ============================================================

CREATE TABLE IF NOT EXISTS scheduled_tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(100) NOT NULL,
    task_id BIGINT REFERENCES tasks(id),
    cron_expression VARCHAR(100),
    schedule_type VARCHAR(20) DEFAULT 'cron',
    interval_minutes INTEGER,
    next_run_time TIMESTAMP,
    last_run_time TIMESTAMP,
    status VARCHAR(20) DEFAULT 'disabled',
    parameters TEXT,  -- JSON
    run_count INTEGER DEFAULT 0,
    fail_count INTEGER DEFAULT 0,
    remark TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- ============================================================
-- 系统配置表
-- ============================================================

CREATE TABLE IF NOT EXISTS system_config (
    key VARCHAR(100) PRIMARY KEY NOT NULL,
    value TEXT NOT NULL,
    description TEXT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- ============================================================
-- 触发器已移除，使用 MyBatis-Plus 自动填充 updated_at
-- ============================================================

-- ============================================================
-- 任务编排表
-- ============================================================

-- 编排定义表
CREATE TABLE IF NOT EXISTS pipelines (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    max_parallel INTEGER DEFAULT 5,
    enabled BOOLEAN DEFAULT 1,
    created_by VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pipelines_name ON pipelines(name);
CREATE INDEX IF NOT EXISTS idx_pipelines_enabled ON pipelines(enabled);

-- 编排任务配置表
CREATE TABLE IF NOT EXISTS pipeline_tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    pipeline_id BIGINT NOT NULL REFERENCES pipelines(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    script_id BIGINT NOT NULL REFERENCES scripts(id) ON DELETE CASCADE,
    order_num INTEGER DEFAULT 0,
    server_ids TEXT,
    step_server_mapping TEXT,
    step_params TEXT,
    shared_params TEXT,
    timeout BIGINT,
    enabled BOOLEAN DEFAULT 1,
    depends_on TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pipeline_tasks_pipeline_id ON pipeline_tasks(pipeline_id);
CREATE INDEX IF NOT EXISTS idx_pipeline_tasks_order ON pipeline_tasks(pipeline_id, order_num);

-- 编排执行记录表
CREATE TABLE IF NOT EXISTS pipeline_runs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    pipeline_id BIGINT NOT NULL REFERENCES pipelines(id) ON DELETE CASCADE,
    pipeline_name VARCHAR(100),
    status VARCHAR(20) DEFAULT 'pending',
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    triggered_by VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pipeline_runs_pipeline_id ON pipeline_runs(pipeline_id);
CREATE INDEX IF NOT EXISTS idx_pipeline_runs_status ON pipeline_runs(status);
CREATE INDEX IF NOT EXISTS idx_pipeline_runs_created_at ON pipeline_runs(created_at);

-- 编排执行任务关联表
CREATE TABLE IF NOT EXISTS pipeline_run_tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    pipeline_run_id BIGINT NOT NULL REFERENCES pipeline_runs(id) ON DELETE CASCADE,
    task_id BIGINT NOT NULL REFERENCES tasks(id),
    task_name VARCHAR(100),
    status VARCHAR(20) DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pipeline_run_tasks_run_id ON pipeline_run_tasks(pipeline_run_id);
CREATE INDEX IF NOT EXISTS idx_pipeline_run_tasks_task_id ON pipeline_run_tasks(task_id);

-- ============================================================
-- 数据库初始化完成
-- 表数量：22
-- ============================================================
