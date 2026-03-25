#!/bin/bash
# Step 1: System Info Collection

echo "========================================"
echo "Step 1: Collecting System Information"
echo "========================================"

echo "Hostname: $(hostname)"
echo "Date: $(date)"
echo "Uptime: $(uptime)"

# 创建输出文件
mkdir -p /tmp/test_output

# 创建日志文件
cat > /tmp/test_output/step1.log << 'EOF'
[INFO] Step 1 started
[INFO] Collecting system information
[INFO] Step 1 completed
EOF

# 创建 JSON 结果
cat > /tmp/test_output/step1_result.json << 'EOF'
{
  "step": 1,
  "status": "success",
  "data": {
    "hostname": "test-server",
    "collected_at": "2026-03-16"
  }
}
EOF

echo ""
echo "Step 1 Complete! Output files created in /tmp/test_output/"
