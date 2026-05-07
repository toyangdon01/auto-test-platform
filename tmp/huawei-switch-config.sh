#!/bin/bash
# Huawei Switch Port Configuration Script
# 将 25GE 1/0/31 到 25GE 1/0/36 端口配置为 10GE

# 参数检查
if [ -z "$SWITCH_IP" ] || [ -z "$SWITCH_USER" ] || [ -z "$SWITCH_PASS" ]; then
    echo "ERROR: Missing required parameters"
    exit 1
fi

SWITCH_IP="${SWITCH_IP}"
SWITCH_USER="${SWITCH_USER}"
SWITCH_PASS="${SWITCH_PASS}"
PORT_RANGE="${PORT_RANGE:-25ge 1/0/31 to 25ge 1/0/36}"
SPEED_MODE="${SPEED_MODE:-10GE}"

echo "========================================="
echo "Huawei Switch Port Configuration"
echo "========================================="
echo "Switch IP: $SWITCH_IP"
echo "Port Range: $PORT_RANGE"
echo "Speed Mode: $SPEED_MODE"
echo "========================================="

# 使用 expect 登录交换机并执行配置
expect <<EOF
set timeout 30

spawn ssh -o StrictHostKeyChecking=no ${SWITCH_USER}@${SWITCH_IP}

expect {
    "password:" {
        send "${SWITCH_PASS}\r"
    }
    timeout {
        puts "ERROR: SSH connection timeout"
        exit 1
    }
    eof {
        puts "ERROR: SSH connection failed"
        exit 1
    }
}

expect {
    ">" {
        send "sys\r"
    }
    "assword:" {
        puts "ERROR: Password failed"
        exit 1
    }
    timeout {
        puts "ERROR: Login timeout"
        exit 1
    }
}

expect "\[.*\]"
send "interface range $PORT_RANGE\r"

expect "\[.*-"
send "speed 10000\r"

expect "\[.*-"
send "description 10GE_Port\r"

expect "\[.*-"
send "undo shutdown\r"

expect "\[.*-"
send "quit\r"

expect "\[.*\]
send "quit\r"

expect ">"
send "quit\r"

expect eof
EOF

EXIT_CODE=$?

if [ $EXIT_CODE -eq 0 ]; then
    echo "========================================="
    echo "SUCCESS: Ports configured as 10GE"
    echo "========================================="
else
    echo "ERROR: Configuration failed with exit code $EXIT_CODE"
fi

exit $EXIT_CODE