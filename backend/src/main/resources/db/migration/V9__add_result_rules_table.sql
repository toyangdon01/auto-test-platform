-- 解析规则表
CREATE TABLE result_rules (
    id BIGSERIAL PRIMARY KEY,
    script_id BIGINT,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    
    -- 解析方式: builtin=内置规则, script=解析脚本
    parser_type VARCHAR(20) NOT NULL,
    
    -- 内置规则格式: key_value, json
    builtin_format VARCHAR(20),
    
    -- 脚本配置
    script_source VARCHAR(20),      -- package, inline
    script_path VARCHAR(500),       -- 脚本包中的路径
    script_content TEXT,            -- 内联脚本内容
    script_language VARCHAR(20),    -- python, shell
    
    -- 输入配置
    input_source VARCHAR(20) NOT NULL,  -- stdout, file
    file_pattern VARCHAR(500),           -- 文件路径正则
    
    -- 输出配置
    output_format VARCHAR(20) NOT NULL DEFAULT 'json',
    
    -- 状态
    enabled BOOLEAN DEFAULT true,
    priority INTEGER DEFAULT 0,
    rules JSONB,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_result_rules_script FOREIGN KEY (script_id) REFERENCES scripts(id) ON DELETE CASCADE
);

COMMENT ON TABLE result_rules IS '测试结果解析规则';
COMMENT ON COLUMN result_rules.parser_type IS '解析方式：builtin=内置规则, script=解析脚本';
COMMENT ON COLUMN result_rules.builtin_format IS '内置格式：key_value=键值对, json=JSON格式';
COMMENT ON COLUMN result_rules.script_source IS '脚本来源：package=从脚本包选择, inline=直接编写';
COMMENT ON COLUMN result_rules.input_source IS '输入来源：stdout=标准输出, file=指定文件';
COMMENT ON COLUMN result_rules.file_pattern IS '文件路径正则表达式';

-- 索引
CREATE INDEX idx_result_rules_script ON result_rules(script_id);

-- 测试结果表扩展
ALTER TABLE test_results ADD COLUMN IF NOT EXISTS parsed_data JSONB;
ALTER TABLE test_results ADD COLUMN IF NOT EXISTS parse_rule_id BIGINT;
ALTER TABLE test_results ADD COLUMN IF NOT EXISTS parse_error TEXT;

COMMENT ON COLUMN test_results.parsed_data IS '解析后的结构化数据';
COMMENT ON COLUMN test_results.parse_rule_id IS '使用的解析规则ID';
COMMENT ON COLUMN test_results.parse_error IS '解析错误信息';

CREATE INDEX IF NOT EXISTS idx_test_results_parse_rule ON test_results(parse_rule_id);
