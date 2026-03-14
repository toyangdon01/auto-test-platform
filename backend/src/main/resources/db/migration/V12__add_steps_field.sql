-- =====================================================
-- 执行步骤配置 - 数据库迁移
-- =====================================================

-- 添加执行步骤字段
ALTER TABLE script_versions ADD COLUMN IF NOT EXISTS steps JSONB DEFAULT '{}';

-- 添加索引
CREATE INDEX IF NOT EXISTS idx_script_versions_steps ON script_versions USING gin(steps);

-- 注释
COMMENT ON COLUMN script_versions.steps IS '执行步骤配置，JSON格式';
