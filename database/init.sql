-- ============================================================
-- 自动化测试管理平台 - 数据库初始化脚本
-- 版本: v1.0.2
-- 数据库: PostgreSQL 13+
-- 更新日期: 2026-03-09
-- ============================================================

-- 设置客户端编码
SET client_encoding = 'UTF8';

-- ============================================================
-- 1. 服务器分组表
-- ============================================================
DROP TABLE IF EXISTS server_groups CASCADE;

CREATE TABLE server_groups (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE server_groups IS '服务器分组表';
COMMENT ON COLUMN server_groups.id IS '分组ID';
COMMENT ON COLUMN server_groups.name IS '分组名称';
COMMENT ON COLUMN server_groups.description IS '分组描述';
COMMENT ON COLUMN server_groups.created_at IS '创建时间';
COMMENT ON COLUMN server_groups.updated_at IS '更新时间';

CREATE INDEX idx_server_groups_name ON server_groups(name);

-- ============================================================
-- 2. 服务器表
-- ============================================================
DROP TABLE IF EXISTS servers CASCADE;

CREATE TABLE servers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    host VARCHAR(255) NOT NULL,
    port INTEGER NOT NULL DEFAULT 22,
    username VARCHAR(50) NOT NULL,
    auth_type VARCHAR(20) NOT NULL,  -- password/ssh_key
    auth_secret TEXT,                  -- 加密存储
    os_type VARCHAR(50),
    os_version VARCHAR(100),
    cpu_cores INTEGER,
    cpu_model VARCHAR(200),
    memory_size VARCHAR(20),
    memory_total_mb INTEGER,
    disk_info JSONB,
    group_id BIGINT REFERENCES server_groups(id) ON DELETE SET NULL,
    tags JSONB DEFAULT '[]',
    remark TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'offline',  -- online/offline/maintenance
    last_check_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE servers IS '服务器表';
COMMENT ON COLUMN servers.id IS '服务器ID';
COMMENT ON COLUMN servers.name IS '服务器名称';
COMMENT ON COLUMN servers.host IS '主机地址(IP或域名)';
COMMENT ON COLUMN servers.port IS 'SSH端口';
COMMENT ON COLUMN servers.username IS 'SSH用户名';
COMMENT ON COLUMN servers.auth_type IS '认证方式: password/ssh_key';
COMMENT ON COLUMN servers.auth_secret IS '认证凭证(加密存储)';
COMMENT ON COLUMN servers.os_type IS '操作系统类型';
COMMENT ON COLUMN servers.os_version IS '操作系统版本';
COMMENT ON COLUMN servers.cpu_cores IS 'CPU核心数';
COMMENT ON COLUMN servers.cpu_model IS 'CPU型号';
COMMENT ON COLUMN servers.memory_size IS '内存大小(如64GB)';
COMMENT ON COLUMN servers.memory_total_mb IS '内存总量(MB)';
COMMENT ON COLUMN servers.disk_info IS '磁盘信息JSON';
COMMENT ON COLUMN servers.group_id IS '所属分组ID';
COMMENT ON COLUMN servers.tags IS '标签数组';
COMMENT ON COLUMN servers.remark IS '备注';
COMMENT ON COLUMN servers.status IS '状态: online/offline/maintenance';
COMMENT ON COLUMN servers.last_check_at IS '最后检测时间';
COMMENT ON COLUMN servers.created_at IS '创建时间';
COMMENT ON COLUMN servers.updated_at IS '更新时间';

CREATE INDEX idx_servers_name ON servers(name);
CREATE INDEX idx_servers_host ON servers(host);
CREATE INDEX idx_servers_group_id ON servers(group_id);
CREATE INDEX idx_servers_status ON servers(status);

-- ============================================================
-- 3. 脚本表
-- ============================================================
DROP TABLE IF EXISTS scripts CASCADE;

CREATE TABLE scripts (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    script_type VARCHAR(20) NOT NULL,  -- shell/python
    test_category VARCHAR(50) NOT NULL,
    description TEXT,
    current_version VARCHAR(20) NOT NULL DEFAULT 'v1.0.0',
    
    -- 生命周期配置
    lifecycle_mode VARCHAR(20) NOT NULL DEFAULT 'simple',  -- simple/full
    has_deploy BOOLEAN DEFAULT FALSE,
    has_cleanup BOOLEAN DEFAULT FALSE,
    deploy_entry VARCHAR(255),
    cleanup_entry VARCHAR(255),
    
    entry_file VARCHAR(255),            -- 执行入口文件路径
    file_list JSONB,                    -- 文件列表
    parameters JSONB,                   -- 参数定义（支持分组：shared/deploy/run）
    parse_rules JSONB,
    default_timeout INTEGER DEFAULT 3600,
    default_retry INTEGER DEFAULT 0,
    is_builtin BOOLEAN DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'enabled',  -- enabled/disabled
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE scripts IS '测试脚本表';
COMMENT ON COLUMN scripts.id IS '脚本ID';
COMMENT ON COLUMN scripts.name IS '脚本名称(唯一)';
COMMENT ON COLUMN scripts.script_type IS '脚本类型: shell/python';
COMMENT ON COLUMN scripts.test_category IS '测试类型分类';
COMMENT ON COLUMN scripts.description IS '脚本描述';
COMMENT ON COLUMN scripts.current_version IS '当前版本号';
COMMENT ON COLUMN scripts.lifecycle_mode IS '生命周期模式: simple/full';
COMMENT ON COLUMN scripts.has_deploy IS '是否包含部署阶段';
COMMENT ON COLUMN scripts.has_cleanup IS '是否包含卸载阶段';
COMMENT ON COLUMN scripts.deploy_entry IS '部署入口文件路径';
COMMENT ON COLUMN scripts.cleanup_entry IS '卸载入口文件路径';
COMMENT ON COLUMN scripts.entry_file IS '执行入口文件路径';
COMMENT ON COLUMN scripts.file_list IS '文件列表JSON';
COMMENT ON COLUMN scripts.parameters IS '参数定义JSON(分组)';
COMMENT ON COLUMN scripts.parse_rules IS '输出解析规则JSON';
COMMENT ON COLUMN scripts.default_timeout IS '默认超时时间(秒)';
COMMENT ON COLUMN scripts.default_retry IS '默认重试次数';
COMMENT ON COLUMN scripts.is_builtin IS '是否预置脚本';
COMMENT ON COLUMN scripts.status IS '状态: enabled/disabled';
COMMENT ON COLUMN scripts.created_at IS '创建时间';
COMMENT ON COLUMN scripts.updated_at IS '更新时间';

CREATE INDEX idx_scripts_name ON scripts(name);
CREATE INDEX idx_scripts_test_category ON scripts(test_category);
CREATE INDEX idx_scripts_status ON scripts(status);
CREATE INDEX idx_scripts_lifecycle_mode ON scripts(lifecycle_mode);

-- ============================================================
-- 4. 脚本版本表
-- ============================================================
DROP TABLE IF EXISTS script_versions CASCADE;

CREATE TABLE script_versions (
    id BIGSERIAL PRIMARY KEY,
    script_id BIGINT NOT NULL REFERENCES scripts(id) ON DELETE CASCADE,
    version VARCHAR(20) NOT NULL,
    
    -- 生命周期配置
    lifecycle_mode VARCHAR(20) NOT NULL DEFAULT 'simple',
    has_deploy BOOLEAN DEFAULT FALSE,
    has_cleanup BOOLEAN DEFAULT FALSE,
    deploy_entry VARCHAR(255),
    cleanup_entry VARCHAR(255),
    
    entry_file VARCHAR(255) NOT NULL,   -- 执行入口文件路径
    file_list JSONB NOT NULL,           -- 文件列表
    storage_path VARCHAR(500) NOT NULL, -- 存储目录路径
    total_size BIGINT,                  -- 总文件大小(字节)
    file_count INTEGER DEFAULT 1,       -- 文件数量
    checksum VARCHAR(64),               -- 整体校验值(SHA256)
    change_log TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(script_id, version)
);

COMMENT ON TABLE script_versions IS '脚本版本表';
COMMENT ON COLUMN script_versions.id IS '版本记录ID';
COMMENT ON COLUMN script_versions.script_id IS '脚本ID';
COMMENT ON COLUMN script_versions.version IS '版本号';
COMMENT ON COLUMN script_versions.lifecycle_mode IS '生命周期模式';
COMMENT ON COLUMN script_versions.has_deploy IS '是否包含部署阶段';
COMMENT ON COLUMN script_versions.has_cleanup IS '是否包含卸载阶段';
COMMENT ON COLUMN script_versions.deploy_entry IS '部署入口文件';
COMMENT ON COLUMN script_versions.cleanup_entry IS '卸载入口文件';
COMMENT ON COLUMN script_versions.entry_file IS '执行入口文件路径';
COMMENT ON COLUMN script_versions.file_list IS '文件列表JSON';
COMMENT ON COLUMN script_versions.storage_path IS '存储目录路径';
COMMENT ON COLUMN script_versions.total_size IS '总文件大小(字节)';
COMMENT ON COLUMN script_versions.file_count IS '文件数量';
COMMENT ON COLUMN script_versions.checksum IS '整体校验值';
COMMENT ON COLUMN script_versions.change_log IS '变更日志';
COMMENT ON COLUMN script_versions.created_at IS '创建时间';

CREATE INDEX idx_script_versions_script_id ON script_versions(script_id);

-- ============================================================
-- 5. 任务表
-- ============================================================
DROP TABLE IF EXISTS tasks CASCADE;

CREATE TABLE tasks (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    script_id BIGINT NOT NULL REFERENCES scripts(id),
    script_version VARCHAR(20) NOT NULL,
    
    -- 参数配置（分组）
    shared_params JSONB DEFAULT '{}',   -- 共享参数
    deploy_params JSONB DEFAULT '{}',   -- 部署参数
    run_params JSONB NOT NULL DEFAULT '{}',  -- 执行参数
    
    -- 生命周期控制
    skip_deploy BOOLEAN DEFAULT FALSE,
    skip_cleanup BOOLEAN DEFAULT FALSE,
    deploy_timeout INTEGER DEFAULT 600,
    cleanup_timeout INTEGER DEFAULT 300,
    
    -- 生命周期阶段状态
    deploy_status VARCHAR(20),          -- pending/running/completed/failed/skipped
    cleanup_status VARCHAR(20),
    deploy_started_at TIMESTAMP,
    deploy_finished_at TIMESTAMP,
    cleanup_started_at TIMESTAMP,
    cleanup_finished_at TIMESTAMP,
    
    -- 指标采集配置
    collect_enabled BOOLEAN DEFAULT TRUE,
    collect_config JSONB,
    
    -- 执行配置
    execution_mode VARCHAR(20) NOT NULL,  -- immediate/scheduled
    scheduled_time TIMESTAMP,
    parallel_mode VARCHAR(20) DEFAULT 'sequential',  -- sequential/parallel
    max_parallel INTEGER DEFAULT 1,
    failure_strategy VARCHAR(20) DEFAULT 'continue',  -- continue/stop
    status VARCHAR(20) NOT NULL DEFAULT 'pending',  -- pending/deploying/running/cleaning/completed/cancelled/failed
    progress INTEGER DEFAULT 0,  -- 0-100
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE tasks IS '测试任务表';
COMMENT ON COLUMN tasks.id IS '任务ID';
COMMENT ON COLUMN tasks.name IS '任务名称';
COMMENT ON COLUMN tasks.description IS '任务描述';
COMMENT ON COLUMN tasks.script_id IS '脚本ID';
COMMENT ON COLUMN tasks.script_version IS '脚本版本';
COMMENT ON COLUMN tasks.shared_params IS '共享参数JSON';
COMMENT ON COLUMN tasks.deploy_params IS '部署参数JSON';
COMMENT ON COLUMN tasks.run_params IS '执行参数JSON';
COMMENT ON COLUMN tasks.skip_deploy IS '是否跳过部署';
COMMENT ON COLUMN tasks.skip_cleanup IS '是否跳过卸载';
COMMENT ON COLUMN tasks.deploy_timeout IS '部署超时时间(秒)';
COMMENT ON COLUMN tasks.cleanup_timeout IS '卸载超时时间(秒)';
COMMENT ON COLUMN tasks.deploy_status IS '部署阶段状态';
COMMENT ON COLUMN tasks.cleanup_status IS '卸载阶段状态';
COMMENT ON COLUMN tasks.deploy_started_at IS '部署开始时间';
COMMENT ON COLUMN tasks.deploy_finished_at IS '部署结束时间';
COMMENT ON COLUMN tasks.cleanup_started_at IS '卸载开始时间';
COMMENT ON COLUMN tasks.cleanup_finished_at IS '卸载结束时间';
COMMENT ON COLUMN tasks.collect_enabled IS '是否启用指标采集';
COMMENT ON COLUMN tasks.collect_config IS '指标采集配置JSON';
COMMENT ON COLUMN tasks.execution_mode IS '执行方式: immediate/scheduled';
COMMENT ON COLUMN tasks.scheduled_time IS '定时执行时间';
COMMENT ON COLUMN tasks.parallel_mode IS '并行模式: sequential/parallel';
COMMENT ON COLUMN tasks.max_parallel IS '最大并发数';
COMMENT ON COLUMN tasks.failure_strategy IS '失败策略: continue/stop';
COMMENT ON COLUMN tasks.status IS '任务状态';
COMMENT ON COLUMN tasks.progress IS '执行进度(0-100)';
COMMENT ON COLUMN tasks.created_at IS '创建时间';
COMMENT ON COLUMN tasks.started_at IS '开始执行时间';
COMMENT ON COLUMN tasks.finished_at IS '结束时间';
COMMENT ON COLUMN tasks.updated_at IS '更新时间';

CREATE INDEX idx_tasks_status ON tasks(status);
CREATE INDEX idx_tasks_script_id ON tasks(script_id);
CREATE INDEX idx_tasks_created_at ON tasks(created_at);
CREATE INDEX idx_tasks_scheduled_time ON tasks(scheduled_time);
CREATE INDEX idx_tasks_deploy_status ON tasks(deploy_status);
CREATE INDEX idx_tasks_cleanup_status ON tasks(cleanup_status);
CREATE INDEX idx_tasks_status_created ON tasks(status, created_at DESC);

-- ============================================================
-- 6. 任务-服务器关联表
-- ============================================================
DROP TABLE IF EXISTS task_servers CASCADE;

CREATE TABLE task_servers (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    server_id BIGINT NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    
    -- ===== 部署阶段 =====
    deploy_status VARCHAR(20) DEFAULT 'pending',     -- pending/running/completed/failed/skipped
    deploy_started_at TIMESTAMP,
    deploy_finished_at TIMESTAMP,
    deploy_exit_code INTEGER,
    deploy_output TEXT,                              -- 部署日志
    deploy_error TEXT,
    
    -- ===== 执行阶段 =====
    run_status VARCHAR(20) DEFAULT 'pending',        -- pending/running/completed/failed
    started_at TIMESTAMP,                            -- 执行开始时间（兼容旧字段）
    finished_at TIMESTAMP,                           -- 执行结束时间（兼容旧字段）
    exit_code INTEGER,                               -- 执行返回码（兼容旧字段）
    output TEXT,                                     -- 执行日志（兼容旧字段）
    error_message TEXT,
    parsed_result JSONB,                             -- 解析后的结果
    
    -- ===== 卸载阶段 =====
    cleanup_status VARCHAR(20) DEFAULT 'pending',    -- pending/running/completed/failed/skipped
    cleanup_started_at TIMESTAMP,
    cleanup_finished_at TIMESTAMP,
    cleanup_exit_code INTEGER,
    cleanup_output TEXT,                             -- 卸载日志
    cleanup_error TEXT,
    
    -- ===== 综合 =====
    overall_status VARCHAR(20) DEFAULT 'pending',    -- 整体状态
    progress INTEGER DEFAULT 0,                      -- 整体进度 0-100
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(task_id, server_id)
);

COMMENT ON TABLE task_servers IS '任务-服务器关联表';
COMMENT ON COLUMN task_servers.id IS '记录ID';
COMMENT ON COLUMN task_servers.task_id IS '任务ID';
COMMENT ON COLUMN task_servers.server_id IS '服务器ID';
COMMENT ON COLUMN task_servers.deploy_status IS '部署阶段状态';
COMMENT ON COLUMN task_servers.deploy_started_at IS '部署开始时间';
COMMENT ON COLUMN task_servers.deploy_finished_at IS '部署结束时间';
COMMENT ON COLUMN task_servers.deploy_exit_code IS '部署返回码';
COMMENT ON COLUMN task_servers.deploy_output IS '部署日志输出';
COMMENT ON COLUMN task_servers.deploy_error IS '部署错误信息';
COMMENT ON COLUMN task_servers.run_status IS '执行阶段状态';
COMMENT ON COLUMN task_servers.started_at IS '执行开始时间';
COMMENT ON COLUMN task_servers.finished_at IS '执行结束时间';
COMMENT ON COLUMN task_servers.exit_code IS '执行返回码';
COMMENT ON COLUMN task_servers.output IS '执行日志输出';
COMMENT ON COLUMN task_servers.error_message IS '错误信息';
COMMENT ON COLUMN task_servers.parsed_result IS '解析后的结果JSON';
COMMENT ON COLUMN task_servers.cleanup_status IS '卸载阶段状态';
COMMENT ON COLUMN task_servers.cleanup_started_at IS '卸载开始时间';
COMMENT ON COLUMN task_servers.cleanup_finished_at IS '卸载结束时间';
COMMENT ON COLUMN task_servers.cleanup_exit_code IS '卸载返回码';
COMMENT ON COLUMN task_servers.cleanup_output IS '卸载日志输出';
COMMENT ON COLUMN task_servers.cleanup_error IS '卸载错误信息';
COMMENT ON COLUMN task_servers.overall_status IS '整体状态';
COMMENT ON COLUMN task_servers.progress IS '整体进度(0-100)';
COMMENT ON COLUMN task_servers.created_at IS '创建时间';

CREATE INDEX idx_task_servers_task_id ON task_servers(task_id);
CREATE INDEX idx_task_servers_server_id ON task_servers(server_id);
CREATE INDEX idx_task_servers_overall_status ON task_servers(overall_status);
CREATE INDEX idx_task_servers_deploy_status ON task_servers(deploy_status);
CREATE INDEX idx_task_servers_run_status ON task_servers(run_status);
CREATE INDEX idx_task_servers_cleanup_status ON task_servers(cleanup_status);

-- ============================================================
-- 7. 性能指标表（分区表）
-- ============================================================
DROP TABLE IF EXISTS metrics CASCADE;

CREATE TABLE metrics (
    id BIGSERIAL,
    task_id BIGINT NOT NULL,
    server_id BIGINT NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    metric_type VARCHAR(50) NOT NULL,  -- cpu/memory/disk/network/custom
    metric_name VARCHAR(100) NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    unit VARCHAR(20),
    tags JSONB,
    PRIMARY KEY (id, timestamp)
) PARTITION BY RANGE (timestamp);

COMMENT ON TABLE metrics IS '性能指标表(按月分区)';
COMMENT ON COLUMN metrics.id IS '指标ID';
COMMENT ON COLUMN metrics.task_id IS '任务ID';
COMMENT ON COLUMN metrics.server_id IS '服务器ID';
COMMENT ON COLUMN metrics.timestamp IS '采集时间';
COMMENT ON COLUMN metrics.metric_type IS '指标类型';
COMMENT ON COLUMN metrics.metric_name IS '指标名称';
COMMENT ON COLUMN metrics.value IS '指标值';
COMMENT ON COLUMN metrics.unit IS '单位';
COMMENT ON COLUMN metrics.tags IS '标签';

-- 创建分区（示例：2026年3月）
CREATE TABLE metrics_202603 PARTITION OF metrics
    FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');

-- 创建分区（示例：2026年4月）
CREATE TABLE metrics_202604 PARTITION OF metrics
    FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');

-- 分区表索引
CREATE INDEX idx_metrics_task_id ON metrics(task_id);
CREATE INDEX idx_metrics_server_id ON metrics(server_id);
CREATE INDEX idx_metrics_timestamp ON metrics(timestamp);
CREATE INDEX idx_metrics_type_name ON metrics(metric_type, metric_name);
CREATE INDEX idx_metrics_task_timestamp ON metrics(task_id, timestamp DESC);

-- ============================================================
-- 8. 报告表
-- ============================================================
DROP TABLE IF EXISTS reports CASCADE;

CREATE TABLE reports (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    summary TEXT,
    conclusion VARCHAR(50),  -- pass/fail/warning
    report_data JSONB,
    file_path VARCHAR(500),
    file_format VARCHAR(20),  -- pdf/html
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE reports IS '测试报告表';
COMMENT ON COLUMN reports.id IS '报告ID';
COMMENT ON COLUMN reports.task_id IS '任务ID';
COMMENT ON COLUMN reports.title IS '报告标题';
COMMENT ON COLUMN reports.summary IS '报告摘要';
COMMENT ON COLUMN reports.conclusion IS '测试结论';
COMMENT ON COLUMN reports.report_data IS '报告数据JSON';
COMMENT ON COLUMN reports.file_path IS '报告文件路径';
COMMENT ON COLUMN reports.file_format IS '文件格式: pdf/html';
COMMENT ON COLUMN reports.created_at IS '创建时间';

CREATE INDEX idx_reports_task_id ON reports(task_id);
CREATE INDEX idx_reports_created_at ON reports(created_at);
CREATE INDEX idx_reports_conclusion_created ON reports(conclusion, created_at DESC);

-- ============================================================
-- 9. 系统配置表
-- ============================================================
DROP TABLE IF EXISTS system_config CASCADE;

CREATE TABLE system_config (
    key VARCHAR(100) PRIMARY KEY,
    value TEXT NOT NULL,
    description TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE system_config IS '系统配置表';
COMMENT ON COLUMN system_config.key IS '配置键';
COMMENT ON COLUMN system_config.value IS '配置值';
COMMENT ON COLUMN system_config.description IS '配置描述';
COMMENT ON COLUMN system_config.updated_at IS '更新时间';

-- 初始化系统配置
INSERT INTO system_config (key, value, description) VALUES
('script_storage_path', '/data/scripts', '脚本存储路径'),
('report_storage_path', '/data/reports', '报告存储路径'),
('temp_storage_path', '/data/temp', '临时文件存储路径'),
('default_timeout', '3600', '默认超时时间(秒)'),
('default_retry', '0', '默认重试次数'),
('max_concurrent_tasks', '10', '最大并发任务数'),
('metrics_retention_days', '30', '指标数据保留天数'),
('report_retention_days', '90', '报告保留天数'),
('ssh_connect_timeout', '30', 'SSH连接超时(秒)'),
('ssh_execute_timeout', '3600', 'SSH执行超时(秒)'),
('ssh_reconnect_times', '3', 'SSH重连次数');

-- ============================================================
-- 10. 测试结果表
-- ============================================================
DROP TABLE IF EXISTS test_results CASCADE;

CREATE TABLE test_results (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    server_id BIGINT NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    task_server_id BIGINT NOT NULL REFERENCES task_servers(id) ON DELETE CASCADE,
    
    -- 结果判定
    result VARCHAR(20) NOT NULL,           -- pass/fail/warning/error
    result_reason TEXT,                     -- 判定原因
    overall_score INTEGER,                  -- 综合评分 0-100
    
    -- 解析后的标准化指标
    metrics JSONB NOT NULL,                 -- 标准化指标数据
    
    -- 原始数据
    raw_output TEXT,                        -- 原始标准输出
    raw_error TEXT,                         -- 原始错误输出
    output_files JSONB,                     -- 输出文件列表
    
    -- 执行信息
    exit_code INTEGER,                      -- 脚本返回码
    duration_ms INTEGER,                    -- 执行时长(毫秒)
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE test_results IS '测试结果表';
COMMENT ON COLUMN test_results.id IS '结果ID';
COMMENT ON COLUMN test_results.task_id IS '任务ID';
COMMENT ON COLUMN test_results.server_id IS '服务器ID';
COMMENT ON COLUMN test_results.task_server_id IS '任务-服务器关联ID';
COMMENT ON COLUMN test_results.result IS '测试结果: pass/fail/warning/error';
COMMENT ON COLUMN test_results.result_reason IS '判定原因';
COMMENT ON COLUMN test_results.overall_score IS '综合评分(0-100)';
COMMENT ON COLUMN test_results.metrics IS '标准化指标数据JSON';
COMMENT ON COLUMN test_results.raw_output IS '原始标准输出';
COMMENT ON COLUMN test_results.raw_error IS '原始错误输出';
COMMENT ON COLUMN test_results.output_files IS '输出文件列表JSON';
COMMENT ON COLUMN test_results.exit_code IS '脚本返回码';
COMMENT ON COLUMN test_results.duration_ms IS '执行时长(毫秒)';
COMMENT ON COLUMN test_results.started_at IS '开始时间';
COMMENT ON COLUMN test_results.finished_at IS '结束时间';
COMMENT ON COLUMN test_results.created_at IS '创建时间';

CREATE INDEX idx_test_results_task_id ON test_results(task_id);
CREATE INDEX idx_test_results_server_id ON test_results(server_id);
CREATE INDEX idx_test_results_result ON test_results(result);
CREATE INDEX idx_test_results_created_at ON test_results(created_at);

-- ============================================================
-- 11. 指标定义表
-- ============================================================
DROP TABLE IF EXISTS metric_definitions CASCADE;

CREATE TABLE metric_definitions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,      -- 指标名称
    display_name VARCHAR(200) NOT NULL,     -- 显示名称
    category VARCHAR(50) NOT NULL,          -- 分类：performance/reliability/stability
    unit VARCHAR(50),                        -- 单位
    description TEXT,                        -- 描述
    baseline_config JSONB,                   -- 基准线配置
    comparison_mode VARCHAR(20) DEFAULT 'higher_better',  -- higher_better/lower_better
    applicable_categories JSONB,             -- 适用测试类型
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE metric_definitions IS '指标定义表';
COMMENT ON COLUMN metric_definitions.id IS '指标定义ID';
COMMENT ON COLUMN metric_definitions.name IS '指标名称(唯一)';
COMMENT ON COLUMN metric_definitions.display_name IS '显示名称';
COMMENT ON COLUMN metric_definitions.category IS '分类: performance/reliability/stability';
COMMENT ON COLUMN metric_definitions.unit IS '单位';
COMMENT ON COLUMN metric_definitions.description IS '描述';
COMMENT ON COLUMN metric_definitions.baseline_config IS '基准线配置JSON';
COMMENT ON COLUMN metric_definitions.comparison_mode IS '比较模式: higher_better/lower_better';
COMMENT ON COLUMN metric_definitions.applicable_categories IS '适用测试类型JSON';
COMMENT ON COLUMN metric_definitions.created_at IS '创建时间';

CREATE INDEX idx_metric_definitions_name ON metric_definitions(name);
CREATE INDEX idx_metric_definitions_category ON metric_definitions(category);

-- 初始化预置指标定义
INSERT INTO metric_definitions (name, display_name, category, unit, baseline_config, comparison_mode, applicable_categories) VALUES
('iops', 'IOPS', 'performance', 'ops/s', '{"min": 1000, "warning": 10000, "excellent": 50000}', 'higher_better', '["基础性能-磁盘", "应用性能-MySQL", "应用性能-PostgreSQL"]'),
('throughput', '吞吐量', 'performance', 'MB/s', '{"min": 10, "warning": 100, "excellent": 500}', 'higher_better', '["基础性能-磁盘", "基础性能-网络", "应用性能-MySQL"]'),
('latency_avg', '平均延迟', 'performance', 'ms', '{"max": 100, "warning": 10, "excellent": 1}', 'lower_better', '["基础性能-磁盘", "基础性能-网络", "应用性能-MySQL", "应用性能-Redis"]'),
('latency_p99', 'P99延迟', 'performance', 'ms', '{"max": 500, "warning": 50, "excellent": 5}', 'lower_better', '["基础性能-磁盘", "应用性能-MySQL", "应用性能-Redis"]'),
('latency_p95', 'P95延迟', 'performance', 'ms', '{"max": 200, "warning": 20, "excellent": 2}', 'lower_better', '["基础性能-磁盘", "应用性能-MySQL", "应用性能-Redis"]'),
('latency_max', '最大延迟', 'performance', 'ms', '{"max": 1000, "warning": 100, "excellent": 10}', 'lower_better', '["基础性能-磁盘", "应用性能-MySQL"]'),
('error_rate', '错误率', 'reliability', '%', '{"max": 5, "warning": 1, "excellent": 0.1}', 'lower_better', '["应用性能-MySQL", "应用性能-Redis", "应用性能-Nginx"]'),
('success_rate', '成功率', 'reliability', '%', '{"min": 95, "warning": 99, "excellent": 99.9}', 'higher_better', '["应用性能-MySQL", "应用性能-Redis", "应用性能-Nginx"]'),
('qps', 'QPS', 'performance', 'req/s', '{"min": 100, "warning": 1000, "excellent": 10000}', 'higher_better', '["应用性能-Redis", "应用性能-Nginx"]'),
('tps', 'TPS', 'performance', 'txn/s', '{"min": 10, "warning": 100, "excellent": 1000}', 'higher_better', '["应用性能-MySQL", "应用性能-PostgreSQL"]'),
('cpu_usage', 'CPU使用率', 'performance', '%', '{"max": 100, "warning": 80, "excellent": 50}', 'lower_better', '["基础性能-CPU"]'),
('memory_usage', '内存使用率', 'performance', '%', '{"max": 100, "warning": 80, "excellent": 50}', 'lower_better', '["基础性能-内存"]'),
('disk_usage', '磁盘使用率', 'performance', '%', '{"max": 100, "warning": 80, "excellent": 50}', 'lower_better', '["基础性能-磁盘"]'),
('network_bandwidth', '网络带宽', 'performance', 'Mbps', '{"min": 10, "warning": 100, "excellent": 1000}', 'higher_better', '["基础性能-网络"]');

-- ============================================================
-- 12. 结果判定规则表
-- ============================================================
DROP TABLE IF EXISTS result_rules CASCADE;

CREATE TABLE result_rules (
    id BIGSERIAL PRIMARY KEY,
    script_id BIGINT REFERENCES scripts(id) ON DELETE CASCADE,    -- NULL表示全局规则
    name VARCHAR(100) NOT NULL,
    description TEXT,
    rules JSONB NOT NULL,                        -- 判定规则
    priority INTEGER DEFAULT 0,                  -- 优先级
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE result_rules IS '结果判定规则表';
COMMENT ON COLUMN result_rules.id IS '规则ID';
COMMENT ON COLUMN result_rules.script_id IS '脚本ID(NULL为全局规则)';
COMMENT ON COLUMN result_rules.name IS '规则名称';
COMMENT ON COLUMN result_rules.description IS '规则描述';
COMMENT ON COLUMN result_rules.rules IS '判定规则JSON';
COMMENT ON COLUMN result_rules.priority IS '优先级(数值越大优先级越高)';
COMMENT ON COLUMN result_rules.enabled IS '是否启用';
COMMENT ON COLUMN result_rules.created_at IS '创建时间';
COMMENT ON COLUMN result_rules.updated_at IS '更新时间';

CREATE INDEX idx_result_rules_script_id ON result_rules(script_id);
CREATE INDEX idx_result_rules_enabled ON result_rules(enabled);

-- ============================================================
-- 创建更新时间自动更新触发器函数
-- ============================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- 为需要的表创建触发器
DROP TRIGGER IF EXISTS update_server_groups_updated_at ON server_groups;
CREATE TRIGGER update_server_groups_updated_at
    BEFORE UPDATE ON server_groups
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_servers_updated_at ON servers;
CREATE TRIGGER update_servers_updated_at
    BEFORE UPDATE ON servers
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_scripts_updated_at ON scripts;
CREATE TRIGGER update_scripts_updated_at
    BEFORE UPDATE ON scripts
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_tasks_updated_at ON tasks;
CREATE TRIGGER update_tasks_updated_at
    BEFORE UPDATE ON tasks
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_result_rules_updated_at ON result_rules;
CREATE TRIGGER update_result_rules_updated_at
    BEFORE UPDATE ON result_rules
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_system_config_updated_at ON system_config;
CREATE TRIGGER update_system_config_updated_at
    BEFORE UPDATE ON system_config
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
-- 完成提示
-- ============================================================
DO $$
BEGIN
    RAISE NOTICE '========================================';
    RAISE NOTICE '数据库初始化完成!';
    RAISE NOTICE '表数量: 12';
    RAISE NOTICE '预置数据: system_config(11条), metric_definitions(14条)';
    RAISE NOTICE '========================================';
END $$;
