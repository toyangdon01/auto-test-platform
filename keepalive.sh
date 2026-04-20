#!/data/data/com.termux/files/usr/bin/bash
# Termux 保活脚本 - 防止被系统杀死
# 用法：./keepalive.sh start | stop | status

SERVICE_NAME="autotest-platform"
LOCK_FILE="$HOME/.keepalive.lock"
LOG_FILE="$HOME/.keepalive.log"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" >> "$LOG_FILE"
}

start() {
    if [ -f "$LOCK_FILE" ]; then
        echo "保活服务已在运行 (PID: $(cat $LOCK_FILE))"
        return 1
    fi
    
    # 获取唤醒锁
    termux-wake-lock 2>/dev/null || true
    
    # 后台监控循环
    (
        log "=== 保活服务启动 ==="
        while true; do
            # 检查 tmux 会话
            if ! tmux has-session -t autotest-backend 2>/dev/null; then
                log "警告：autotest-backend 会话不存在，尝试重启..."
                cd "$HOME/.openclaw/workspace/auto-test-platform/backend"
                export JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk
                nohup mvn spring-boot:run > backend.log 2>&1 &
                sleep 5
            fi
            
            if ! tmux has-session -t autotest-frontend 2>/dev/null; then
                log "警告：autotest-frontend 会话不存在"
            fi
            
            # 每 60 秒检查一次
            sleep 60
        done
    ) &
    
    echo $! > "$LOCK_FILE"
    log "保活服务已启动 (PID: $(cat $LOCK_FILE))"
    echo "✅ 保活服务已启动"
}

stop() {
    if [ -f "$LOCK_FILE" ]; then
        kill $(cat "$LOCK_FILE") 2>/dev/null
        rm -f "$LOCK_FILE"
        termux-wake-unlock 2>/dev/null || true
        log "=== 保活服务已停止 ==="
        echo "✅ 保活服务已停止"
    else
        echo "保活服务未运行"
    fi
}

status() {
    if [ -f "$LOCK_FILE" ] && kill -0 $(cat "$LOCK_FILE") 2>/dev/null; then
        echo "✅ 保活服务运行中 (PID: $(cat $LOCK_FILE))"
        echo "唤醒锁状态：$(termux-wake-lock 2>&1 | grep -o 'active\|inactive' || echo '未知')"
    else
        echo "❌ 保活服务未运行"
        rm -f "$LOCK_FILE"
    fi
    
    echo ""
    echo "=== tmux 会话 ==="
    tmux list-sessions 2>/dev/null || echo "无活跃会话"
    
    echo ""
    echo "=== 最近日志 ==="
    tail -5 "$LOG_FILE" 2>/dev/null || echo "无日志"
}

case "$1" in
    start) start ;;
    stop) stop ;;
    status|*) status ;;
esac
