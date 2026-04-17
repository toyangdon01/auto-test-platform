# Pipeline YAML 配置示例
# 用于通过 YAML 文件创建任务编排

# ==================== 基本信息 ====================
name: 性能测试流水线示例
description: 这是一个示例流水线，展示 YAML 配置格式
maxParallel: 3                    # 最大并行数，默认 5

# ==================== 服务器定义（可选） ====================
# - 定义新服务器，平台不存在时自动创建
# - 已存在（按 name 匹配）则更新
servers:
  - name: test-server-1
    host: 192.168.1.100
    port: 22                      # 默认 22
    username: root
    authType: password            # password | ssh_key，默认 password
    authSecret: your-password     # 密码或私钥内容
    tags: [test, disk]            # 可选，标签列表
    remark: 测试服务器             # 可选，备注

  - name: test-server-2
    host: 192.168.1.101
    username: root
    # authType 省略，默认 password
    authSecret: your-password

  - name: test-server-3
    host: 192.168.1.102
    username: root
    authType: ssh_key
    authSecret: |
      -----BEGIN RSA PRIVATE KEY-----
      MIIEpAIBAAKCAQ...
      -----END RSA PRIVATE KEY-----

# ==================== 任务列表 ====================
tasks:
  - name: 磁盘性能测试
    script: fio-disk-test         # 脚本名称，必须存在于平台中
    
    # 服务器分配（名称或 ID）
    serverIds: [test-server-1, test-server-2]
    
    dependsOn: []                 # 依赖任务名称列表
    
    timeout: 1800                 # 超时时间（秒）
    
    # 步骤服务器映射（可选）
    stepServerMapping:
      prepare: [test-server-1]
      run_test: [test-server-1, test-server-2]
    
    # 共享参数（可选）
    sharedParams:
      RUNTIME: 60
      SIZE: 5G
    
    # 步骤参数（可选）
    stepParams:
      run_test:
        TEST_MODE: randrw

  - name: 网络性能测试
    script: iperf3-network-test
    dependsOn: [磁盘性能测试]      # 依赖上一个任务
    timeout: 600
    serverIds: [test-server-2]
    sharedParams:
      DURATION: 30

  - name: 结果汇总
    script: result-summary
    dependsOn: [磁盘性能测试, 网络性能测试]
    timeout: 300
    serverIds: [test-server-1]
