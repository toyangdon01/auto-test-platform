# Termux 手机操作指南

## 📱 前提条件

**安装 Termux:API App**（必须）
```
下载：https://github.com/termux/termux-api/releases
或 F-Droid / Play 商店搜索 "Termux:API"
```

**已安装命令：** `pkg install termux-api` ✅

---

## 🔋 系统信息

### 电池状态
```bash
termux-battery-status
# 输出：电量百分比、充电状态、健康状态等
```

### 设备信息
```bash
# 设备基本信息
getprop | grep -E "ro.product|ro.build"

# 屏幕信息
wm size
wm density

# 内存信息
free -h

# 存储信息
df -h
```

### 网络信息
```bash
# IP 地址
ip addr show

# WiFi 信息
termux-wifi-connectioninfo

# 网络诊断
ping google.com
traceroute example.com
```

### 传感器数据
```bash
termux-sensor -s all      # 所有传感器
termux-sensor -s light    # 光线传感器
termux-sensor -s proximity # 距离传感器
```

---

## 📞 通讯功能

### 短信
```bash
# 发送短信
termux-sms-send -n 10086 "Hello"

# 查看短信列表
termux-sms-list

# 监控新短信
termux-sms-list -d
```

### 电话
```bash
# 拨打电话
termux-telephony call 10086

# 查看信号强度
termux-telephony signalstrength

# 查看设备 IMEI
termux-telephony deviceinfo
```

### 联系人
```bash
# 获取联系人列表
termux-contact-list

# 查找联系人
termux-contact-list | grep "张三"
```

### 通话记录
```bash
termux-call-log
```

---

## 📋 剪贴板

```bash
# 复制文本到剪贴板
termux-clipboard-set "Hello World"

# 从剪贴板读取
termux-clipboard-get

# 监控剪贴板变化
termux-clipboard-get -d
```

---

## 🔔 通知

```bash
# 发送通知
termux-notification --title "标题" --content "内容"

# 发送通知（带按钮）
termux-notification \
  --title "提醒" \
  --content "记得喝水！" \
  --button1 "知道了" \
  --button2 "稍后"

# 移除通知
termux-notification-remove <id>
```

---

## 📸 媒体功能

### 摄像头
```bash
# 拍照
termux-camera-photo -c 0 output.jpg
# -c 0 是后置摄像头，-c 1 是前置

# 录像（需要额外配置）
termux-camera-record output.mp4
```

### 媒体扫描
```bash
# 让系统识别新文件
termux-media-scan /sdcard/Download
```

### 下载管理
```bash
# 下载文件
termux-download /sdcard/file.zip
```

---

## 📍 位置服务

```bash
# 获取当前位置
termux-location
# 输出：经纬度、海拔、精度等
```

---

## 📅 日历

```bash
# 获取日历事件
termux-calendar-event -s "2026-04-13" -e "2026-04-20"
```

---

## 🔊 音频控制

```bash
# 获取音量
termux-volume get

# 设置音量（0-15）
termux-volume set media 10

# 震动
termux-vibrate -d 1000  # 震动 1 秒
```

---

## 📁 文件管理

### 访问外部存储
```bash
# 授权访问（首次运行）
termux-setup-storage

# 访问目录
ls ~/storage/shared/      # 内部存储
ls ~/storage/downloads/   # 下载目录
ls ~/storage/dcim/        # 照片目录
```

### 文件操作
```bash
# 复制文件到内部存储
cp file.txt ~/storage/shared/Download/

# 从内部存储读取
cat ~/storage/shared/Documents/notes.txt
```

---

## 🌐 网络服务

### SSH 服务器
```bash
pkg install openssh
sshd  # 启动 SSH 服务
# 默认端口 8022
```

### Web 服务器
```bash
# Python 快速服务器
python3 -m http.server 8080

# 或安装 nginx
pkg install nginx
```

### 网络工具
```bash
pkg install nmap netcat curl wget

# 端口扫描
nmap -p 1-1000 192.168.1.1

# 监听端口
nc -l 1234
```

---

## ⚙️ 自动化任务

### 定时任务
```bash
pkg install cronie

# 编辑 crontab
crontab -e

# 示例：每小时检查服务
0 * * * * ~/check-service.sh
```

### 后台监控
```bash
# 监控电池，低电量时通知
while true; do
  battery=$(termux-battery-status | jq '.percentage')
  if [ $battery -lt 20 ]; then
    termux-notification --title "低电量" --content "电量：$battery%"
  fi
  sleep 300
done
```

---

## 🛠️ 实用脚本示例

### 1. 电池监控脚本
```bash
#!/data/data/com.termux/files/usr/bin/bash
# ~/battery-monitor.sh

battery=$(termux-battery-status 2>/dev/null | grep -o '"percentage":[0-9]*' | cut -d: -f2)
charging=$(termux-battery-status 2>/dev/null | grep -o '"status":"[A-Z]*"' | cut -d'"' -f4)

echo "电量：$battery%"
echo "状态：$charging"

if [ "$battery" -lt 20 ] && [ "$charging" != "CHARGING" ]; then
    termux-notification --title "⚠️ 低电量" --content "剩余电量：$battery%"
fi
```

### 2. 自动备份脚本
```bash
#!/data/data/com.termux/files/usr/bin/bash
# ~/auto-backup.sh

BACKUP_DIR=~/storage/shared/Backup
mkdir -p $BACKUP_DIR

# 备份重要文件
tar -czf $BACKUP_DIR/workspace-$(date +%Y%m%d).tar.gz \
    ~/.openclaw/workspace/

echo "备份完成：$BACKUP_DIR"
termux-notification --title "✅ 备份完成" --content "$(date)"
```

### 3. 网络诊断脚本
```bash
#!/data/data/com.termux/files/usr/bin/bash
# ~/network-check.sh

echo "=== 网络诊断 ==="
echo ""
echo "📶 WiFi 信息:"
termux-wifi-connectioninfo 2>/dev/null || echo "无法获取"

echo ""
echo "🌐 外网连通性:"
ping -c 2 8.8.8.8 | tail -2

echo ""
echo "🔗 端口测试:"
curl -s -o /dev/null -w "Google: %{http_code}\n" https://google.com
```

---

## ⚠️ 权限说明

| 功能 | 需要权限 |
|------|----------|
| 短信 | 短信权限 |
| 电话 | 电话权限 |
| 联系人 | 联系人权限 |
| 位置 | 位置权限 |
| 摄像头 | 相机权限 |
| 存储 | 存储权限 |
| 通知 | 通知权限 |

**授予权限：**
```
设置 → 应用程序 → Termux → 权限 → 开启对应权限
```

---

## 🎯 推荐用途

### 日常实用
1. **电池监控** - 低电量提醒
2. **自动备份** - 定时备份重要文件
3. **通知提醒** - 待办事项提醒
4. **剪贴板同步** - 跨设备文本同步

### 开发相关
1. **SSH 服务器** - 远程访问手机
2. **Web 服务** - 本地测试网站
3. **网络工具** - 网络诊断
4. **自动化脚本** - 定时任务

### 系统管理
1. **文件管理** - 批量处理文件
2. **日志收集** - 系统日志分析
3. **性能监控** - CPU/内存监控

---

## 📚 相关资源

- Termux 官网：https://termux.dev
- Termux Wiki：https://wiki.termux.com
- Termux API：https://github.com/termux/termux-api
- 社区：https://reddit.com/r/termux

---

**提示：** 使用这些功能前，确保已安装 Termux:API App 并授予相应权限！
