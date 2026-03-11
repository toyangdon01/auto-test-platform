-- V10: 添加解析结果字段
ALTER TABLE test_results ADD COLUMN IF NOT EXISTS parsed_data JSONB;

COMMENT ON COLUMN test_results.parsed_data IS '解析后的结构化数据';
