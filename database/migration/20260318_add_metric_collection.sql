-- 指标采集功能数据库迁移脚本
-- 执行日期：2026-03-18
-- 说明：添加指标采集相关字段

-- ==================== Task 表 ====================
-- 注意：如果字段已存在则跳过

-- 添加指标采集启用字段
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'tasks' AND column_name = 'collect_enabled') THEN
        ALTER TABLE tasks ADD COLUMN collect_enabled BOOLEAN DEFAULT TRUE;
    END IF;
END $$;

-- 添加指标采集配置字段
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'tasks' AND column_name = 'collect_config') THEN
        ALTER TABLE tasks ADD COLUMN collect_config JSONB;
    END IF;
END $$;


-- ==================== TaskServer 表 ====================
-- 添加分阶段状态字段

-- 部署阶段字段
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'task_servers' AND column_name = 'deploy_status') THEN
        ALTER TABLE task_servers ADD COLUMN deploy_status VARCHAR(20) DEFAULT 'pending';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'task_servers' AND column_name = 'deploy_started_at') THEN
        ALTER TABLE task_servers ADD COLUMN deploy_started_at TIMESTAMP;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'task_servers' AND column_name = 'deploy_finished_at') THEN
        ALTER TABLE task_servers ADD COLUMN deploy_finished_at TIMESTAMP;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'task_servers' AND column_name = 'deploy_exit_code') THEN
        ALTER TABLE task_servers ADD COLUMN deploy_exit_code INTEGER;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'task_servers' AND column_name = 'deploy_output') THEN
        ALTER TABLE task_servers ADD COLUMN deploy_output TEXT;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'task_servers' AND column_name = 'deploy_error') THEN
        ALTER TABLE task_servers ADD COLUMN deploy_error TEXT;
    END IF;
END $$;


-- 执行阶段字段（部分字段可能已存在，保留兼容性）
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'task_servers' AND column_name = 'run_status') THEN
        ALTER TABLE task_servers ADD COLUMN run_status VARCHAR(20) DEFAULT 'pending';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'task_servers' AND column_name = 'run_started_at') THEN
        ALTER TABLE task_servers ADD COLUMN run_started_at TIMESTAMP;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'task_servers' AND column_name = 'run_finished_at') THEN
        ALTER TABLE task_servers ADD COLUMN run_finished_at TIMESTAMP;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'task_servers' AND column_name = 'run_exit_code') THEN
        ALTER TABLE task_servers ADD COLUMN run_exit_code INTEGER;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'task_servers' AND column_name = 'run_output') THEN
        ALTER TABLE task_servers ADD COLUMN run_output TEXT;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'task_servers' AND column_name = 'run_error') THEN
        ALTER TABLE task_servers ADD COLUMN run_error TEXT;
    END IF;
END $$;

-- 解析结果字段
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'task_servers' AND column_name = 'parsed_result') THEN
        ALTER TABLE task_servers ADD COLUMN parsed_result JSONB;
    END IF;
END $$;


-- 卸载阶段字段
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'task_servers' AND column_name = 'cleanup_status') THEN
        ALTER TABLE task_servers ADD COLUMN cleanup_status VARCHAR(20) DEFAULT 'pending';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'task_servers' AND column_name = 'cleanup_started_at') THEN
        ALTER TABLE task_servers ADD COLUMN cleanup_started_at TIMESTAMP;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'task_servers' AND column_name = 'cleanup_finished_at') THEN
        ALTER TABLE task_servers ADD COLUMN cleanup_finished_at TIMESTAMP;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'task_servers' AND column_name = 'cleanup_exit_code') THEN
        ALTER TABLE task_servers ADD COLUMN cleanup_exit_code INTEGER;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'task_servers' AND column_name = 'cleanup_output') THEN
        ALTER TABLE task_servers ADD COLUMN cleanup_output TEXT;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'task_servers' AND column_name = 'cleanup_error') THEN
        ALTER TABLE task_servers ADD COLUMN cleanup_error TEXT;
    END IF;
END $$;


-- ==================== 索引创建 ====================
-- 为查询优化添加索引

CREATE INDEX IF NOT EXISTS idx_task_servers_deploy_status ON task_servers(deploy_status);
CREATE INDEX IF NOT EXISTS idx_task_servers_run_status ON task_servers(run_status);
CREATE INDEX IF NOT EXISTS idx_task_servers_cleanup_status ON task_servers(cleanup_status);
CREATE INDEX IF NOT EXISTS idx_task_servers_overall_status ON task_servers(overall_status);
CREATE INDEX IF NOT EXISTS idx_tasks_collect_enabled ON tasks(collect_enabled);


-- ==================== 数据初始化 ====================
-- 更新现有数据（如果有）

-- 设置现有任务的 collect_enabled 为 TRUE（默认启用）
UPDATE tasks SET collect_enabled = TRUE WHERE collect_enabled IS NULL;

-- 设置现有 task_servers 的执行状态（兼容旧数据）
UPDATE task_servers 
SET run_status = overall_status,
    run_started_at = started_at,
    run_finished_at = finished_at,
    run_exit_code = exit_code,
    run_output = output
WHERE run_status IS NULL AND overall_status IS NOT NULL;


-- ==================== 完成提示 ====================
SELECT '数据库迁移完成！' AS status;
