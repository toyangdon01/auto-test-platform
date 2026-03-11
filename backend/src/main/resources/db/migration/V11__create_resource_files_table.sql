-- V11: 创建资源文件管理表

-- 资源文件表
CREATE TABLE IF NOT EXISTS resource_files (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL COMMENT '文件名称',
    storage_path VARCHAR(500) NOT NULL COMMENT '平台存储路径',
    file_size BIGINT NOT NULL COMMENT '文件大小（字节）',
    file_type VARCHAR(50) COMMENT '文件类型：binary/rpm/tar/zip/config/other',
    category VARCHAR(50) COMMENT '分类：与脚本分类一致',
    checksum VARCHAR(32) NOT NULL COMMENT 'MD5校验值',
    description TEXT COMMENT '描述',
    created_by VARCHAR(100) COMMENT '上传者',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(checksum) -- MD5 唯一约束，防止重复上传
);

COMMENT ON TABLE resource_files IS '资源文件表';
COMMENT ON COLUMN resource_files.name IS '文件名称';
COMMENT ON COLUMN resource_files.storage_path IS '平台存储路径';
COMMENT ON COLUMN resource_files.file_size IS '文件大小（字节）';
COMMENT ON COLUMN resource_files.file_type IS '文件类型';
COMMENT ON COLUMN resource_files.category IS '分类';
COMMENT ON COLUMN resource_files.checksum IS 'MD5校验值';
COMMENT ON COLUMN resource_files.description IS '描述';

-- 脚本资源关联表
CREATE TABLE IF NOT EXISTS script_resources (
    id BIGSERIAL PRIMARY KEY,
    script_id BIGINT NOT NULL COMMENT '脚本ID',
    resource_id BIGINT NOT NULL COMMENT '资源文件ID',
    target_path VARCHAR(255) NOT NULL DEFAULT '/tmp' COMMENT '目标路径',
    permissions VARCHAR(10) DEFAULT '644' COMMENT '文件权限',
    upload_order INT DEFAULT 0 COMMENT '上传顺序',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (script_id) REFERENCES scripts(id) ON DELETE CASCADE,
    FOREIGN KEY (resource_id) REFERENCES resource_files(id) ON DELETE CASCADE,
    UNIQUE(script_id, resource_id) -- 同一脚本不能重复关联同一资源
);

COMMENT ON TABLE script_resources IS '脚本资源关联表';
COMMENT ON COLUMN script_resources.target_path IS '目标路径（远程服务器上的路径）';
COMMENT ON COLUMN script_resources.permissions IS '文件权限（如 755）';
COMMENT ON COLUMN script_resources.upload_order IS '上传顺序（数字小的先上传）';

-- 索引
CREATE INDEX idx_resource_files_category ON resource_files(category);
CREATE INDEX idx_resource_files_file_type ON resource_files(file_type);
CREATE INDEX idx_script_resources_script_id ON script_resources(script_id);
