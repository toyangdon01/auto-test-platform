# Android 解除进程限制 - 完整指南

## 🎯 目标
解除 Android 12+ 的"幻影进程杀手"限制（默认 32 个子进程）

---

## 📋 方案对比

| 方案 | 难度 | 需要电脑 | 持久性 |
|------|------|----------|--------|
| Shizuku | ⭐⭐ | ❌ | 重启后失效 |
| 无线 ADB | ⭐⭐⭐ | ❌ | 重启后失效 |
| 有线 ADB | ⭐⭐ | ✅ | 重启后失效 |

---

## 🔧 方案 A：Shizuku（推荐）⭐

### 步骤 1：安装 Shizuku

1. 打开浏览器下载 Shizuku：
   - GitHub: https://github.com/RikkaApps/Shizuku/releases
   - 或 Play 商店搜索 "Shizuku"

2. 安装 Shizuku APK

### 步骤 2：启动 Shizuku

1. 打开 Shizuku App
2. 按指引开启 **开发者选项**：
   ```
   设置 → 关于手机 → 软件信息
   → 连续点击"版本号"7 次
   ```
3. 开启 **USB 调试**：
   ```
   设置 → 开发者选项 → USB 调试 → 开启
   ```
4. 在 Shizuku 中选择 **"通过无线调试启动"**
5. 按提示配对并启动

### 步骤 3：执行解除限制

**方式 1：使用 Shizuku CLI（最简单）**

在 Termux 中执行：
```bash
# 通过 Shizuku 执行 ADB 命令
shizuku device_config put activity_manager max_phantom_processes 2147483647
shizuku device_config set_sync_disabled_for_tests persistent
```

**方式 2：使用 Termux ADB + Shizuku**

```bash
# 设置 ADB 连接到 Shizuku
export ADB_SERVER_SOCKET=shizuku

# 执行命令
adb shell device_config put activity_manager max_phantom_processes 2147483647
adb shell device_config set_sync_disabled_for_tests persistent
```

---

## 🔧 方案 B：无线 ADB（Android 11+）

### 步骤 1：开启无线调试

```
设置 → 开发者选项 → 无线调试 → 开启
```

### 步骤 2：获取配对信息

1. 点击"无线调试"
2. 选择"使用配对码配对设备"
3. 记下 **IP:端口** 和 **配对码**

### 步骤 3：在 Termux 中配对

```bash
# 配对（只需一次）
adb pair 192.168.x.x:端口  配对码

# 连接
adb connect 192.168.x.x:端口

# 执行解除限制
adb shell device_config put activity_manager max_phantom_processes 2147483647
adb shell device_config set_sync_disabled_for_tests persistent
```

---

## 🔧 方案 C：用电脑执行（最稳定）

### 步骤

1. 手机开启 USB 调试
2. 用 USB 连接电脑
3. 在电脑执行：

```bash
# Windows/Mac/Linux
adb shell device_config put activity_manager max_phantom_processes 2147483647
adb shell device_config set_sync_disabled_for_tests persistent
```

---

## ✅ 验证是否生效

```bash
# 检查当前限制
adb shell device_config get activity_manager max_phantom_processes

# 应显示：2147483647
```

---

## ⚠️ 重要提示

1. **重启后失效** - 每次重启手机需要重新执行
2. **三星额外限制** - One UI 可能有额外的进程管理
3. **配合其他优化** - 电池白名单、后台保护等

---

## 🔄 自动化（重启后自动执行）

创建启动脚本 `~/restore-phantom-fix.sh`：

```bash
#!/data/data/com.termux/files/usr/bin/bash
# 重启后运行此脚本恢复设置

# 检查 Shizuku 是否运行
if shizuku --version >/dev/null 2>&1; then
    shizuku device_config put activity_manager max_phantom_processes 2147483647
    echo "✅ 幻影进程限制已解除"
else
    echo "⚠️ Shizuku 未运行，请先启动 Shizuku"
fi
```

---

## 📝 快速检查清单

- [ ] 安装 Shizuku App
- [ ] 开启开发者选项
- [ ] 开启 USB 调试
- [ ] 启动 Shizuku 服务
- [ ] 执行解除限制命令
- [ ] 验证设置生效
- [ ] 创建重启后恢复脚本

---

## 🆘 故障排除

**Q: Shizuku 无法启动**
A: 确保开发者选项和 USB 调试已开启

**Q: ADB 找不到设备**
A: 确保已配对并连接

**Q: 命令执行失败**
A: 检查是否有 ADB 权限

**Q: 重启后失效**
A: 正常现象，需要重新执行或使用自动化脚本
