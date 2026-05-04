-- 删除 task_servers 表的 is_local 字段（已用 server_id = -1 替代）
ALTER TABLE task_servers DROP COLUMN IF EXISTS is_local;