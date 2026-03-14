#!/bin/bash

# 简单测试脚本
echo "========================================"
echo "开始执行简单测试"
echo "========================================"

# 获取系统信息
echo "系统信息："
uname -a
echo ""

echo "CPU 信息："
cat /proc/cpuinfo | grep "model name" | head -1
echo ""

echo "内存信息："
free -h
echo ""

echo "磁盘信息："
df -h /
echo ""

# 简单的性能测试
echo "========================================"
echo "执行简单性能测试"
echo "========================================"

# CPU 测试
echo "CPU 压测 (5秒)..."
timeout 5s dd if=/dev/zero of=/dev/null bs=1M count=10000 2>&1 | tail -1
echo ""

# 内存测试
echo "内存测试..."
free -h | grep Mem
echo ""

echo "========================================"
echo "测试完成！"
echo "========================================"

exit 0
