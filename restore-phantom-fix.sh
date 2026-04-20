#!/data/data/com.termux/files/usr/bin/bash
# 重启后恢复 Android 进程限制解除
# 用法：~/restore-phantom-fix.sh

set -e

echo "=== 恢复 Android 幻影进程限制解除 ==="
echo ""

# ADB 无线调试地址（需要更新）
ADB_HOST="192.168.1.4"
ADB_PORT=""

# 获取当前无线调试端口
get_port() {
    echo "请在手机上查看当前无线调试端口："
    echo "设置 → 开发者选项 → 无线调试"
    echo ""
    read -p "输入端口号：" port
    ADB_PORT=$port
}

# 连接并执行
restore() {
    if [ -z "$ADB_PORT" ]; then
        get_port
    fi
    
    echo "连接到 $ADB_HOST:$ADB_PORT ..."
    
    if adb connect $ADB_HOST:$ADB_PORT 2>/dev/null | grep -q "connected"; then
        echo "✅ 连接成功"
        
        echo "执行解除限制命令..."
        adb shell device_config put activity_manager max_phantom_processes 2147483647
        adb shell device_config set_sync_disabled_for_tests persistent
        
        # 验证
        RESULT=$(adb shell device_config get activity_manager max_phantom_processes)
        if [ "$RESULT" = "2147483647" ]; then
            echo ""
            echo "🎉 修复成功！当前限制：$RESULT"
        else
            echo "⚠️ 设置可能未生效：$RESULT"
        fi
        
        # 断开连接
        adb disconnect $ADB_HOST:$ADB_PORT >/dev/null
    else
        echo "❌ 连接失败"
        echo ""
        echo "请确保："
        echo "1. 无线调试已开启"
        echo "2. 端口号正确"
        echo "3. 手机和 Termux 在同一网络"
    fi
}

# 检查 ADB
if ! command -v adb &> /dev/null; then
    echo "❌ ADB 未安装"
    echo "请执行：pkg install android-tools"
    exit 1
fi

restore
