#!/bin/bash
# 文件收集测试脚本

echo "=== 开始文件收集测试 ==="
echo "当前时间: $(date)"
echo "主机名: $(hostname)"

# 创建输出目录
OUTPUT_DIR="/tmp/test_output"
mkdir -p $OUTPUT_DIR

# 创建测试文件1: 日志文件
cat > $OUTPUT_DIR/test.log << 'EOF'
[2026-03-16 11:35:00] INFO: 测试开始
[2026-03-16 11:35:01] INFO: 执行步骤1
[2026-03-16 11:35:02] INFO: 执行步骤2
[2026-03-16 11:35:03] INFO: 测试完成
EOF

# 创建测试文件2: JSON结果
cat > $OUTPUT_DIR/result.json << 'EOF'
{
  "status": "pass",
  "score": 95,
  "metrics": {
    "cpu_usage": 45.2,
    "memory_usage": 62.8,
    "disk_usage": 35.5
  },
  "tests": [
    {"name": "test1", "result": "pass"},
    {"name": "test2", "result": "pass"}
  ]
}
EOF

# 创建测试文件3: CSV数据
cat > $OUTPUT_DIR/data.csv << 'EOF'
timestamp,cpu,memory,disk
11:35:00,45.2,62.8,35.5
11:35:01,48.1,63.2,35.5
11:35:02,46.5,62.9,35.6
EOF

echo "已创建输出文件:"
ls -la $OUTPUT_DIR/

echo "=== 测试完成 ==="
exit 0
