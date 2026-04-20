#!/data/data/com.termux/files/usr/bin/bash
# 通过 Shizuku 解除 Android 进程限制
# 需要先安装并启动 Shizuku App

set -e

echo "=== Android 幻影进程限制修复 ==="
echo ""

# 检查 Shizuku 是否可用
check_shizuku() {
    if command -v shizuku &> /dev/null; then
        return 0
    fi
    
    # 检查是否有 Shizuku 的 socket
    if [ -S "$HOME/.shizuku/shizuku" ]; then
        return 0
    fi
    
    # 尝试通过 ADB 连接到 Shizuku
    if adb shell echo "test" 2>/dev/null | grep -q "test"; then
        return 0
    fi
    
    return 1
}

# 执行修复
fix_phantom_process() {
    echo "执行解除限制命令..."
    
    # 尝试通过 ADB 执行
    if adb shell device_config put activity_manager max_phantom_processes 2147483647 2>/dev/null; then
        echo "✅ 最大子进程数已设置为无限制"
    else
        echo "❌ ADB 命令执行失败"
        return 1
    fi
    
    # 禁用同步
    if adb shell device_config set_sync_disabled_for_tests persistent 2>/dev/null; then
        echo "✅ 已禁用配置同步"
    fi
    
    # 验证
    echo ""
    echo "验证设置..."
    RESULT=$(adb shell device_config get activity_manager max_phantom_processes 2>/dev/null)
    
    if [ "$RESULT" = "2147483647" ]; then
        echo "🎉 修复成功！当前限制：$RESULT"
        echo ""
        echo "⚠️ 注意：重启手机后需要重新执行此脚本"
        return 0
    else
        echo "⚠️ 设置可能未生效，当前值：$RESULT"
        return 1
    fi
}

# 主流程
main() {
    echo "检查环境..."
    
    # 检查 ADB
    if ! command -v adb &> /dev/null; then
        echo "❌ 未安装 ADB 工具"
        echo "请执行：pkg install android-tools"
        exit 1
    fi
    
    echo "✅ ADB 已安装"
    
    # 检查 ADB 是否有权限
    echo "检查 ADB 权限..."
    if ! adb shell echo "test" 2>/dev/null | grep -q "test"; then
        echo ""
        echo "❌ ADB 无权限或未连接"
        echo ""
        echo "请先完成以下步骤之一："
        echo ""
        echo "【方案 A】使用 Shizuku（推荐）"
        echo "  1. 安装 Shizuku App"
        echo "     https://github.com/RikkaApps/Shizuku/releases"
        echo "  2. 开启开发者选项和 USB 调试"
        echo "  3. 在 Shizuku 中启动服务"
        echo "  4. 重新运行此脚本"
        echo ""
        echo "【方案 B】使用无线 ADB（Android 11+）"
        echo "  1. 设置 → 开发者选项 → 无线调试 → 开启"
        echo "  2. 获取配对码和端口"
        echo "  3. 执行：adb pair IP:端口 配对码"
        echo "  4. 执行：adb connect IP:端口"
        echo "  5. 重新运行此脚本"
        echo ""
        echo "【方案 C】用电脑执行"
        echo "  1. 手机开启 USB 调试"
        echo "  2. USB 连接电脑"
        echo "  3. 在电脑执行："
        echo "     adb shell device_config put activity_manager max_phantom_processes 2147483647"
        echo ""
        exit 1
    fi
    
    echo "✅ ADB 权限正常"
    echo ""
    
    # 执行修复
    fix_phantom_process
}

main "$@"
