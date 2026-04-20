# Pipeline YAML 配置示例
# 用于通过 YAML 文件创建任务编排

# ==================== 基本信息 ====================
name: 示例流水线                  # 必填：编排名称
description: 这是一个示例流水线    # 可选：描述
maxParallel: 3                    # 可选：最大并行数，默认 5

# ==================== 服务器定义（可选） ====================
# 如果平台中已存在同名服务器，会自动更新；不存在则创建
# 也可以不定义此部分，直接在 stepServerMapping 中使用已存在的服务器名称
servers:
  - name: test-server-1           # 必填：服务器名称
    host: 192.168.1.100           # 必填：服务器地址
    port: 22                      # 可选：SSH 端口，默认 22
    username: root                 # 必填：用户名
    authType: password            # 可选：认证类型，password 或 ssh_key，默认 password
    authSecret: your-password     # 必填：密码或 SSH 私钥内容
    tags: [test]                  # 可选：标签列表
    remark: 测试服务器             # 可选：备注

  - name: test-server-2
    host: 192.168.1.101
    username: root
    authSecret: your-password

# ==================== 任务列表 ====================
tasks:
  # ---------------- 任务 1 ----------------
  - name: 环境准备                # 必填：任务名称
    script: env-check             # 必填：脚本名称（必须存在于平台中）
    timeout: 300                  # 可选：超时时间（秒）
    stepServerMapping:            # 必填：步骤服务器映射
      check: [test-server-1]      # 步骤名: [服务器列表]

  # ---------------- 任务 2 ----------------
  - name: 执行测试
    script: performance-test
    dependsOn: [环境准备]         # 可选：依赖任务列表（当前任务在依赖任务完成后执行）
    timeout: 1800
    stepServerMapping:
      prepare: [test-server-1]
      run_test: [test-server-2]
      cleanup: [test-server-1]
    sharedParams:                 # 可选：共享参数（所有步骤共用）
      RUNTIME: 60
      SIZE: 5G
    stepParams:                   # 可选：步骤参数（仅特定步骤使用）
      prepare:
        CLEANUP_OLD: true
      run_test:                   # 步骤名作为 key
        TEST_MODE: randrw         # 该步骤的参数
        BLOCK_SIZE: 4k
      cleanup:
        KEEP_LOG: true

  # ---------------- 任务 3 ----------------
  - name: 结果收集
    script: result-summary
    dependsOn: [执行测试]
    timeout: 600
    stepServerMapping:
      collect: [test-server-1]
    stepParams:                   # 可选：不同步骤使用不同参数
      collect:
        OUTPUT_FORMAT: json
        INCLUDE_META: true

# ==================== 字段说明 ====================
#
# stepServerMapping 格式说明：
#   key   - 步骤名称（必须与脚本中定义的步骤名称一致）
#   value - 服务器列表
#     导入时：支持服务器名称或 ID
#     导出时：输出服务器名称（更易读）
#
# 示例：
#   stepServerMapping:
#     step_1: [server-1]           # 单台服务器
#     step_2: [server-1, server-2] # 多台服务器并行执行
#
# dependsOn 说明：
#   - 指定当前任务依赖的其他任务名称
#   - 所有依赖任务完成后才会执行当前任务
#   - 多个依赖任务会并行执行
#
# sharedParams vs stepParams：
#   - sharedParams: 所有步骤共享的参数
#   - stepParams: 特定步骤的参数（会覆盖 sharedParams 中的同名参数）
