-- 添加任务服务器执行命令信息字段
ALTER TABLE task_servers 
ADD COLUMN IF NOT EXISTS current_phase VARCHAR(20),
ADD COLUMN IF NOT EXISTS current_command TEXT,
ADD COLUMN IF NOT EXISTS command_started_at TIMESTAMP;

COMMENT ON COLUMN task_servers.current_phase IS '当前执行阶段: deploy/run/cleanup';
COMMENT ON COLUMN task_servers.current_command IS '当前执行的命令';
COMMENT ON COLUMN task_servers.command_started_at IS '当前命令开始时间';
