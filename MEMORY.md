# MEMORY.md - 长期记忆

## 身份
- **我**: 小十，AI 助手
- **你**: 老大

## 重要工作成果

### 浏览器自动化 ✅
- Playwright 不支持 Android → OpenClaw 内置 browser 工具不可用
- Selenium + Chromium 可正常工作
- 已创建 `~/scripts/browser_server.py` 作为替代方案
- 下次如果需要浏览器操作，启动这个服务即可用

### 存储情况
- / 根分区：仅 309MB（紧张）
- /data 分区：93GB 可用 ✅ ← Termux 数据在这里
- Termux 的数据目录 `/data/data/com.termux` 属于 /data 分区

### 自动化测试平台
- 源码仓库：https://gitee.com/toyangdon1/auto-test-platform
- 已克隆并熟悉代码和文档
- 创建了项目熟悉总结文档

---

*最后更新: 2026-04-26*