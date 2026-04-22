# 环境备忘：Termux/Android 无 Root

## 平台信息
- **系统：** Termux on Android, Linux aarch64
- **用户：** `u0_a280`（应用沙盒，无 root）
- **存储：** /data 分区 111GB，可用 93GB ✅

## API Tokens
- **Gitee Token:** `c84168d093c41320c236c9bc87a94b19`

## 关键限制

| 限制 | 说明 |
|------|------|
| 无 sudo | 无法提权，无法修改系统文件 |
| 无 /tmp | 用 `$TMPDIR` 代替 |
| 无 Playwright | Android 不支持，用 Selenium + Chromium |
| 无 Docker | 容器化工具不可用 |
| 后台不持久 | Android 会杀后台，用 tmux 保持 |

## 可写目录
- `$HOME` - 主目录
- `$TMPDIR` - 临时文件（非 /tmp）
- `/sdcard` - 外部存储（需授权）

## 包管理
```bash
pkg install <package>   # 安装
pkg search <keyword>    # 搜索
pkg update              # 更新
```

## 浏览器方案
OpenClaw 内置 browser 工具不可用（Playwright 限制），替代方案：
```bash
# 启动 Selenium 浏览器服务
python3 ~/scripts/browser_server.py --http 8765 &
```

API: `curl http://127.0.0.1:8765/open?url=...`

## 注意事项
1. 临时文件用 `$TMPDIR`，不用 `/tmp`
2. 长任务用 `tmux` 保持
3. 空间充足（93GB），无需频繁清理
4. 服务可能被杀，需要容错设计

---

## Auto-Test-Platform 项目启动

项目路径：`~/workspace/auto-test-platform`

### 开发模式（前后端分离）

```bash
# 终端 1 - 启动后端
cd ~/workspace/auto-test-platform/backend
mvn spring-boot:run
# 后端地址：http://localhost:8080

# 终端 2 - 启动前端（Termux 专用）
cd ~/workspace/auto-test-platform/frontend
env node node_modules/.bin/vite
# 前端地址：http://localhost:3000
```

> **注意：** Termux 没有 `/usr/bin/env`，导致 `npm run dev` 无法工作。需要用 `env node node_modules/.bin/vite` 绕过 shebang 问题。

### 生产模式（打包后）

```bash
# 打包
./build.sh

# 启动（前后端合并）
java -jar backend/target/auto-test-platform-1.0.0-SNAPSHOT.jar
# 访问地址：http://localhost:8080
```

### 远程仓库
- Gitee: `git@gitee.com:toyangdon1/auto-test-platform.git`

---

## Android 进程限制解除

**问题：** Android 12+ 限制最多 32 个子进程

**解决：** 用 ADB 解除限制
```bash
adb shell device_config put activity_manager max_phantom_processes 2147483647
```

**脚本：** `~/restore-phantom-fix.sh`（重启后恢复）

**注意：** 手机重启后需要重新执行
