-- 添加新字段
ALTER TABLE result_rules ADD COLUMN IF NOT EXISTS parser_type VARCHAR(20);
ALTER TABLE result_rules ADD COLUMN IF NOT EXISTS builtin_format VARCHAR(20);
ALTER TABLE result_rules ADD COLUMN IF NOT EXISTS script_source VARCHAR(20);
ALTER TABLE result_rules ADD COLUMN IF NOT EXISTS script_path VARCHAR(500);
ALTER TABLE result_rules ADD COLUMN IF NOT EXISTS script_content TEXT;
ALTER TABLE result_rules ADD COLUMN IF NOT EXISTS script_language VARCHAR(20);
ALTER TABLE result_rules ADD COLUMN IF NOT EXISTS input_source VARCHAR(20);
ALTER TABLE result_rules ADD COLUMN IF NOT EXISTS file_pattern VARCHAR(500);
ALTER TABLE result_rules ADD COLUMN IF NOT EXISTS output_format VARCHAR(20);

-- 修改 rules 字段为可空
ALTER TABLE result_rules ALTER COLUMN rules DROP NOT NULL;

-- 为已有记录设置默认值
UPDATE result_rules SET 
  parser_type = 'builtin',
  builtin_format = 'json',
  input_source = 'stdout',
  output_format = 'json'
WHERE parser_type IS NULL;

-- 添加 test_results 表的新字段
ALTER TABLE test_results ADD COLUMN IF NOT EXISTS parsed_data JSONB;
ALTER TABLE test_results ADD COLUMN IF NOT EXISTS parse_rule_id BIGINT;
ALTER TABLE test_results ADD COLUMN IF NOT EXISTS parse_error TEXT;
