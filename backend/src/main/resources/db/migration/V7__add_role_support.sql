-- =====================================================
-- 多角色测试支持 - 数据库迁移
-- =====================================================

-- 1. 脚本版本表：添加角色定义字段
ALTER TABLE script_versions ADD COLUMN IF NOT EXISTS roles JSONB DEFAULT '[]';

-- 2. 任务服务器表：添加角色配置字段
ALTER TABLE task_servers ADD COLUMN IF NOT EXISTS role VARCHAR(50) DEFAULT 'default';
ALTER TABLE task_servers ADD COLUMN IF NOT EXISTS role_params JSONB DEFAULT '{}';

-- 3. 任务表：添加角色执行策略字段
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS role_execution_strategy JSONB DEFAULT '{}';

-- 4. 创建默认角色定义索引（方便查询）
CREATE INDEX IF NOT EXISTS idx_script_versions_roles ON script_versions USING gin(roles);
CREATE INDEX IF NOT EXISTS idx_task_servers_role ON task_servers(role);

-- 注释
COMMENT ON COLUMN script_versions.roles IS '角色定义列表，JSON格式';
COMMENT ON COLUMN task_servers.role IS '服务器角色名称';
COMMENT ON COLUMN task_servers.role_params IS '角色特定参数';
COMMENT ON COLUMN tasks.role_execution_strategy IS '角色执行策略配置';
