#!/bin/bash
# Step 2: Resource Check

echo "========================================"
echo "Step 2: Checking System Resources"
echo "========================================"

echo "Memory Usage:"
free -h | head -2

echo ""
echo "Disk Usage:"
df -h / | tail -1

echo ""
echo "Step 2 Complete!"
