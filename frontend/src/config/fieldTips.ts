/**
 * 字段说明提示配置
 * 
 * 统一管理各页面字段的 tip 提示信息
 */

// 脚本相关字段
export const SCRIPT_FIELD_TIPS = {
  name: '脚本的唯一标识名称，用于在任务创建时选择脚本。建议使用英文和数字，如 mysql_test',
  version: '脚本的版本号，格式为 v1.0.0。每次修改脚本应更新版本号',
  description: '脚本的功能描述，帮助其他用户了解脚本用途',
  entryFunction: '脚本的主入口函数名称。执行时会调用此函数开始测试',
  cleanupFunction: '清理函数名称，在测试完成后调用。用于清理临时文件、恢复环境等',
  timeout: '脚本执行的超时时间（秒）。超时后任务会被强制终止',
  skipDeploy: '跳过部署阶段，直接执行测试。适用于已部署好的环境',
  skipCleanup: '跳过清理阶段，保留测试环境。适用于需要后续手动检查的场景',
  files: '上传脚本文件及其依赖。支持 .sh、.py 等格式，会保持目录结构',
}

// 任务相关字段
export const TASK_FIELD_TIPS = {
  name: '任务的名称，用于标识和区分不同的测试任务',
  executionMode: '立即执行：创建后立即开始\n定时执行：在指定时间自动开始',
  scheduledTime: '定时执行模式的执行时间。到达时间后任务会自动开始',
  parallelMode: '顺序执行：按顺序逐个执行\n并行执行：同时执行多个服务器',
  maxParallel: '并行执行时的最大并发数。建议不超过服务器总数的 50%',
  failureStrategy: '继续执行：某个服务器失败后继续执行其他服务器\n停止执行：有服务器失败时立即停止所有执行',
  deployTimeout: '部署阶段的超时时间（秒）。包括文件上传、解压、配置等',
  runTimeout: '测试执行阶段的超时时间（秒）',
  cleanupTimeout: '清理阶段的超时时间（秒）',
  sharedParams: '所有服务器共享的参数。在脚本中通过环境变量或参数传入',
  roleParams: '特定角色的专属参数。不同角色可以有不同的参数配置',
  collectEnabled: '是否采集性能指标。开启后会在测试过程中采集 CPU、内存等数据',
}

// 服务器相关字段
export const SERVER_FIELD_TIPS = {
  name: '服务器的显示名称，便于识别和管理',
  host: '服务器的 IP 地址或主机名',
  port: 'SSH 连接端口，默认为 22',
  username: 'SSH 登录用户名',
  authType: '密码认证：使用用户名密码登录\n密钥认证：使用 SSH 私钥登录',
  authSecret: '认证凭据：密码认证时填写密码，密钥认证时填写私钥内容',
  deployDir: '脚本部署的目标目录。脚本文件会上传到此目录',
  tags: '服务器的标签，用于分组筛选',
}

// 角色相关字段
export const ROLE_FIELD_TIPS = {
  name: '角色的唯一标识，如 server、client。用于脚本内部判断当前角色',
  displayName: '角色的显示名称，便于理解。如"服务端"、"客户端"',
  entryFunction: '该角色的入口函数。不同角色可以有不同的入口',
  cleanupFunction: '该角色的清理函数。用于角色特定的清理工作',
  dependsOn: '依赖的其他角色。被依赖的角色会先执行',
  startupProbe: '启动探测配置。用于检测角色是否启动成功',
  resultCollector: '是否由该角色收集测试结果。通常由服务端角色收集',
}

// 参数相关字段
export const PARAM_FIELD_TIPS = {
  name: '参数名称，脚本中通过此名称引用参数值',
  displayName: '参数的显示名称，在界面上展示给用户',
  type: '参数类型：文本、数字、布尔值、选择框等',
  defaultValue: '参数的默认值。用户不填写时使用此值',
  required: '是否必填。必填参数在创建任务时必须提供值',
  description: '参数说明，帮助用户理解参数用途',
}
