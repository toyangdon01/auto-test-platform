-- =====================================================
-- 修复多角色唯一约束
-- 允许同一服务器在同一任务中执行多个角色
-- =====================================================

-- 1. 删除可能存在的旧唯一约束
DO $$
BEGIN
    -- 检查并删除 task_id + server_id 的唯一约束
    IF EXISTS (
        SELECT 1 FROM pg_constraint 
        WHERE conname = 'task_servers_task_id_server_id_key'
    ) THEN
        ALTER TABLE task_servers DROP CONSTRAINT task_servers_task_id_server_id_key;
    END IF;
    
    -- 也检查其他可能的约束名称
    IF EXISTS (
        SELECT 1 FROM pg_constraint 
        WHERE conname = 'uq_task_servers_task_server'
    ) THEN
        ALTER TABLE task_servers DROP CONSTRAINT uq_task_servers_task_server;
    END IF;
END $$;

-- 2. 创建新的复合唯一约束（task_id + server_id + role）
-- 这样同一服务器可以在同一任务中执行多个角色
ALTER TABLE task_servers 
ADD CONSTRAINT uq_task_servers_task_server_role 
UNIQUE (task_id, server_id, role);

-- 注释
COMMENT ON CONSTRAINT uq_task_servers_task_server_role ON task_servers IS 
'同一服务器在同一任务中可以执行多个角色，但每个角色只能执行一次';
