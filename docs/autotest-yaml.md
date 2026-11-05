# autotest.yaml 配置文件功能

## 功能概述

autotest.yaml 是自动化测试平台的脚本配置文件，用于定义脚本的基本信息、参数配置和步骤配置。

**核心特性：**
- 📄 脚本包上传时自动解析配置
- 🔄 配置变更时自动同步到 YAML 文件
- 📦 支持版本控制和配置迁移
- 🔧 与现有数据库结构 100% 兼容

---

## YAML 文件格式

### 完整示例

```yaml
# autotest.yaml - 自动化测试脚本配置文件

# ==================== 基本信息 ====================
name: fio-disk-test
description: FIO 磁盘性能测试脚本

type: shell                    # shell | python
category: disk                 # cpu | memory | disk | network | database | ...
timeout: 1800                  # 默认超时时间（秒）

# ==================== 参数配置 ====================
parameters:
  - name: RUNTIME
    type: string
    default: "60"
    description: 测试时长（秒）
    
  - name: SIZE
    type: string
    default: "5G"
    description: 测试文件大小
    
  - name: IODEPTH
    type: string
    default: "32"
    description: IO 深度
    
  - name: FILENAME
    type: string
    default: "/dev/vdb"
    description: 测试设备或文件路径

# ==================== 资源配置（引用已存在的资源文件 ID） ====================
resources:
  - resourceId: 100
    targetPath: /etc/fio.conf
    permissions: "644"
    order: 1
    
  - resourceId: 101
    targetPath: /opt/fio-patterns.dat
    permissions: "644"
    order: 2

# ==================== 步骤配置 ====================
steps:
  # 步骤 1：准备环境
  prepare:
    displayName: 准备环境
    script: scripts/prepare.sh
    dependsOn: []
    params:
      - name: CHECK_DISK
        defaultValue: "true"
        description: 检查磁盘是否存在
    resultParser: false
    resultCollector: false
    startupProbe: null
    fileCollectEnabled: false
    fileCollects: []
    resources: []
    
  # 步骤 2：执行 FIO 测试
  run_test:
    displayName: 执行 FIO 测试
    script: main.sh
    dependsOn:
      - prepare
    params:
      - name: TEST_MODE
        defaultValue: "randrw"
        description: 测试模式（read/write/randrw）
    resultParser: true
    resultCollector: true
    startupProbe: null
    parseRule:
      parserType: builtin
      builtinFormat: json
      inputSource: stdout
      filePattern: ""
      scriptSource: inline
      scriptContent: ""
      scriptLanguage: python
    fileCollectEnabled: true
    fileCollects:
      - name: FIO 结果 JSON
        path: /tmp/fio_results.json
        type: file
        required: true
        
      - name: FIO 日志目录
        path: /tmp/fio_logs
        type: directory
        required: false
        
      - name: 错误日志
        path: /tmp/fio_error.log
        type: pattern
        required: false
    resources:
      - resourceId: 102
        targetPath: /tmp/custom_pattern.dat
        permissions: "644"
        order: 1


### 字段说明

#### 基本信息

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | ✅ | 脚本名称（唯一） |
| `description` | string | ❌ | 脚本描述 |
| `type` | string | ❌ | 脚本类型：`shell` / `powershell`。除 `powershell` 外的值（含 `python`、留空）都按 shell 处理，这个字段只在本地执行时决定用哪个执行器 |
| `category` | string | ❌ | 测试分类 |
| `timeout` | integer | ❌ | 默认超时时间（秒） |

> 在界面上传脚本包时，如果改了脚本名称，平台只会就地重写 `autotest.yaml` 的 `name` 一行，
> `timeout`、注释和其余字段保持包里的原样（保存/更新脚本才会按数据库整份反写）。
>
> 包里声明的 `timeout` 会在创建时写进 `scripts.default_timeout`（单位**秒**，任务级超时是毫秒），
> 脚本编辑页的「默认超时（秒）」能看到并改它；留空就按包里的声明。
>
> **步骤里没有超时字段**，别往步骤上配 `timeout`，那个键不认识、会被丢弃。执行时的取值顺序是
> 任务的 `timeout`（毫秒）→ `scripts.default_timeout`（秒，就是这里的 `timeout`）→ 配置的兜底值
> （`ShellExecutor.resolveTimeout`）。要让某个包跑得更久，改顶层 `timeout` 或建任务时给超时。

#### 参数配置

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | ✅ | 参数名称（环境变量名） |
| `type` | string | ❌ | 参数类型：string / number |
| `default` | any | ❌ | 默认值 |
| `description` | string | ❌ | 参数描述 |
| `displayName` | string | ❌ | 界面展示名，任务创建页按 `displayName` → `name` 取标签 |
| `required` | boolean | ❌ | 必填项。提交任务时没给出取值就被拒绝 |
| `options` | array | ❌ | 可选值白名单，见下 |

```yaml
parameters:
  - name: MODE
    type: string
    displayName: 运行模式
    description: netperf 的运行方向
    required: true
    options:
      - { label: TCP 批量发送, value: bulksend }
      - { label: TCP 批量接收, value: bulkrecv }
      - udp                # 裸字符串等价于 { label: udp, value: udp }
```

`options` 支持 `{label, value}` 对象与裸字符串两种写法，落库统一为 `[{label, value}]`，
与前端 `ParamDefinition.options` 形状一致；提交任务时按 `value` 校验（区分大小写，前后空白会被忽略）。

校验发生在 `TaskServiceImpl.checkParams`：REST `POST/PUT /tasks`、定时与编排入口、
以及 MCP 的 `run_test` 都走同一条路径（`run_test` 会先返回结构化的 `PARAM_REQUIRED`
/ `PARAM_NOT_IN_OPTIONS`，不会创建出必然失败的任务）。共享参数和步骤参数都受校验，步骤维度
的问题项多带一个 `step` 字段。只对显式声明了 `required: true` 或 `options` 的参数生效，
历史脚本包行为不变。

注意 `default` 的当前语义：它只用于界面展示与任务编辑页预填，**执行时不会注入环境变量**
（`TaskExecutionService` 只读任务提交的 `sharedParams`）。所以 `required: true` 的参数即使写了
`default`，也必须由提交方显式传值；智能体走 MCP 时不会替你填默认值。

注意：覆盖导入（`import_package(strategy=OVERWRITE)` 与 UI 的 ZIP 覆盖）会用 yaml 的声明
**整体替换** `script_versions.parameters`，在界面上手填的参数定义不会被保留。

#### 步骤配置

`steps` 字段支持两种格式：**对象格式**（推荐）和 **数组格式**（兼容）。

#### 格式一：对象格式（推荐）

```yaml
steps:
  prepare:
    displayName: 准备环境
    script: scripts/prepare.sh
    dependsOn: []
    
  run_test:
    displayName: 执行测试
    script: main.sh
    dependsOn:
      - prepare
```

#### 格式二：数组格式（兼容）

如果用户习惯数组格式，也支持自动转换：

```yaml
steps:
  - name: prepare
    displayName: 准备环境
    script: scripts/prepare.sh
    dependsOn: []
    
  - name: run_test
    displayName: 执行测试
    script: main.sh
    dependsOn:
      - prepare
```

> 💡 **说明**：
> - 对象格式是推荐的标准格式，步骤名称作为 key，更清晰
> - 数组格式会被自动转换为对象格式
> - 如果数组格式的步骤没有 `name` 字段，会自动生成 `step_1`、`step_2` 等名称
> - 两种格式在保存时都会转换为对象格式

#### 步骤字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | string | 步骤名称（数组格式必填，对象格式作为 key） |
| `displayName` | string | 步骤显示名称 |
| `script` | string | 要执行的脚本文件路径 |
| `dependsOn` | string[] | 依赖的步骤列表 |
| `params` | array | 步骤参数定义 |
| `resultParser` | boolean | 是否启用结果解析 |
| `resultCollector` | boolean | 是否启用结果收集 |
| `startupProbe` | object | 启动探测配置 |
| `parseRule` | object | 解析规则配置 |
| `fileCollectEnabled` | boolean | 是否启用文件收集 |
| `fileCollects` | array | 文件收集配置 |
| `resources` | array | 步骤专属资源 |

#### 步骤参数 (steps.<步骤名>.params)

步骤参数的定义形状与共享参数一致，支持 `name` / `type` / `displayName` / `description`
/ `required` / `options`，默认值写 `defaultValue` 或 `default` 都可以（同义，`defaultValue`
是历史写法，两者同时存在时以 `defaultValue` 为准）。

```yaml
steps:
  step_1:
    displayName: 打流
    script: main.sh
    params:
      - name: LEVEL
        type: string
        displayName: 级别
        required: true
        defaultValue: fast          # 会注入环境变量
        options:
          - { label: 快速, value: fast }
          - { label: 慢速, value: slow }
      - name: TAG
        type: string
        displayName: 标记
        required: true              # 没有默认值，提交任务时必须给出（共享参数里给也算）
```

与共享参数的两处差别，是既有行为，不是笔误：

- **步骤声明的默认值会注入环境变量**，共享参数声明的 `default` 不会；
- 生效优先级是 用户按步骤填写的取值 > 步骤声明的默认值 > 共享参数取值。
  所以步骤里声明了某个名字的同名默认值时，任务提交方在 `sharedParams` 里传的值会被盖掉。

校验规则：`steps.<步骤名>.params` 里 `required: true` 且按上面的优先级仍取不到值 → `PARAM_REQUIRED`；
提交的取值不在该步骤的 `options` 里 → `PARAM_NOT_IN_OPTIONS`（问题项带 `step`）。
用户按步骤提交的、步骤本身没声明但共享契约声明了的参数，也按共享契约校验，避免绕过。
`_BACKGROUND` 是界面开关，不参与校验，也不会被导出成环境变量。

#### 文件收集配置 (fileCollects)

```yaml
fileCollectEnabled: true
fileCollects:
  - name: FIO 结果 JSON
    path: /tmp/fio_results.json
    type: file           # file | directory | pattern
    required: true
    
  - name: FIO 日志目录
    path: /tmp/fio_logs
    type: directory
    required: false
    
  - name: 错误日志
    path: /tmp/fio_error.log
    type: pattern        # 支持通配符
    required: false
```

**字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | string | 收集文件的名称（用于展示） |
| `path` | string | 文件路径或模式 |
| `type` | string | 类型：`file`（单文件）\|`directory`（目录）\|`pattern`（通配符） |
| `required` | boolean | 是否必需，失败是否影响任务 |

**收集时机**：
- 步骤执行完成后自动收集
- 收集的文件可以在任务详情页查看和下载

#### 资源配置 (resources)

**脚本级资源配置**：

```yaml
# 在脚本根级别
resources:
  - resourceId: 100
    targetPath: /etc/config.conf
    permissions: "644"
    order: 1
```

**步骤级资源配置**：

```yaml
steps:
  run_test:
    displayName: 执行测试
    script: main.sh
    resources:
      - resourceId: 102
        targetPath: /tmp/custom_pattern.dat
        permissions: "644"
        order: 1
```

**字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `resourceId` | integer | 平台资源文件 ID（可选，与 resourceMd5 二选一） |
| `resourceMd5` | string | 资源文件 MD5 值（可选，与 resourceId 二选一，**推荐用于跨项目共享**） |
| `targetPath` | string | 目标路径（默认：/tmp） |
| `permissions` | string | 文件权限（默认：644） |
| `order` | integer | 上传顺序（默认：0） |

**跨项目共享说明**：

- **`resourceId`**：适用于同一项目内的资源引用
- **`resourceMd5`**：推荐使用，通过 MD5 值匹配资源文件，支持跨项目共享脚本

**示例**：
```yaml
resources:
  # 方式一：使用 resourceId（适用于同一项目）
  - resourceId: 100
    targetPath: /etc/config.conf
    permissions: "644"
    
  # 方式二：使用 resourceMd5（推荐，支持跨项目）
  - resourceMd5: e142c2058313b4646c36fa9bb1b38493
    targetPath: /opt/package.tar.gz
    permissions: "755"
    
  # 方式三：同时提供（导入时优先使用 resourceMd5 匹配）
  - resourceId: 101
    resourceMd5: abc123def456
    targetPath: /tmp/data.dat
    permissions: "644"
```

**导入逻辑**：
1. 导入脚本时，优先使用 `resourceMd5` 查找匹配的资源文件
2. 如果 MD5 匹配失败，再尝试使用 `resourceId`
3. 如果都失败，记录警告并跳过该资源配置

**工作流程**：
1. 上传脚本包时，平台解析 `resources` 配置
2. 验证 `resourceId` 对应的资源文件是否存在
3. 创建脚本与资源的关联关系
4. 任务执行时，资源文件自动上传到服务器的指定路径

#### 启动探测 (startupProbe)

```yaml
startupProbe:
  type: tcp                  # tcp | http | command
  port: 22                   # 端口号（tcp/http）
  url: /health               # URL 路径（http）
  command: "pgrep nginx"     # 自定义命令（command）
  timeoutSeconds: 60         # 超时时间
```

#### 解析规则 (parseRule)

```yaml
parseRule:
  parserType: builtin        # builtin | script
  builtinFormat: json        # json | key-value
  inputSource: stdout        # stdout | file
  filePattern: ""            # 文件路径模式
  scriptSource: inline       # inline | file
  scriptContent: ""          # 内联脚本内容
  scriptLanguage: python     # 脚本语言
```

#### 输出配置（已废弃）

> ⚠️ **注意**：`outputConfig` 字段**已废弃**，不再使用。
>
> **替代方案**：使用步骤级的 `fileCollectEnabled` 和 `fileCollects` 配置。

**废弃原因**：
- 功能已迁移到步骤级别，更灵活
- 支持每个步骤独立配置文件收集
- 更好的控制和错误处理

**迁移示例**：

```yaml
# ❌ 旧方式（已废弃）
outputConfig:
  collectEnabled: true
  collectRules:
    - pattern: "/tmp/result_*.json"
      description: "结果文件"

# ✅ 新方式（推荐）
steps:
  run_test:
    displayName: 执行测试
    script: main.sh
    fileCollectEnabled: true
    fileCollects:
      - name: 结果文件
        path: /tmp/result.json
        type: file
        required: true
      - name: 所有结果
        path: /tmp/result_*.json
        type: pattern
        required: false
```

---

## 校验：坏包在导入时就报，而不是跑到一半才炸

解析 `autotest.yaml` 会产出两档问题项。字段契约的唯一真源是
`backend/src/main/java/com/autotest/config/PackageSchema.java`，校验器和 MCP 的
`describe_package_spec` 工具都读它 —— **加字段只改这一处**，否则文档和校验会立刻漂移。

| 档位 | 含义 | 处置 |
|------|------|------|
| `validationErrors` | 会改变执行结果的形状问题：未知键被静默丢弃、`script` 指不到包内文件、`dependsOn` 指向不存在的步骤或成环、脚本文件是 CRLF…… | MCP 的 `create_script` / `import_package` **默认拒绝**；界面与 REST 上传仍然只回 `warnings`，不挡 |
| `warnings` | 风格建议（没写 `name`、没写 `type`） | 一律放行，随响应返回 |

分档之前只有 warning：坏包照样导入成功，问题到执行阶段才以 exit 127、"任务一直等着不执行"
这类形式暴露，排查成本全落在跑任务的人身上。

### 校验是逐键对着原始 YAML 树做的

解析过程按键手工读取，写错的键在 `ScriptConfig` 里已经不存在了，光看模型看不见错。
所以 `ScriptConfigService.collectSchemaIssues` 拿 `JsonNode` 原始树 + 包目录里的真实文件清单一起比。
每条问题项是 `{code, path, message, hint}`：`path` 指到具体位置（如 `steps.step_1.script`），
`hint` 列出该层的合法键，拼错的键还会给候选（前缀命中或编辑距离 ≤ 2，
例如 `paramaters` → "你是不是想写 'parameters'"）。错误码清单以
`PackageSchema.validationCodes()` 为唯一真源，逐条触发条件看那里。

`script` 的判定：先当包内相对路径查（支持 `steps/step1.sh` 这种子目录）；
既不是包内文件、也不像一段命令（含空格/制表符，或既无 `.` 又无 `/`）时会被当成 shell 命令执行，
拼错后缀就是 exit 127，所以现在直接拒。

### 智能体侧：describe_package_spec

MCP 服务端只开 tools 能力（`resource:false / prompt:false`），写包的智能体拿不到本文件。
因此规范本身做成了一个只读工具，返回内容与 `PackageSchema` 同源：

| 参数 `section` | 返回 |
|----------------|------|
| `schema` | `topLevelKeys` / `parameterKeys` / `stepKeys` / `resourceKeys` / `tagKeys` / `optionKeys`，每个键带用途说明；外加三张嵌套表 `probeKeys` / `parseRuleKeys` / `fileCollectKeys`（这三层**不参与校验**，表里就是执行层实际读取的全部键名） |
| `rules` | 硬规则清单：超时单位与回落顺序、共享参数 `default` 不注入、步骤参数 `defaultValue` 注入并覆盖同名共享参数、`options` 严格匹配、LF 换行、覆盖导入整体替换参数定义、**一个脚本只允许一个步骤开结果收集**、**probe/parseRule/fileCollects 只在远程主机执行**、probe 缺 port 静默通过、解析失败不改步骤状态 |
| `example` | `exampleYaml`：一份能通过校验的包声明（配套的 `main.sh` 由提交方一起带上） |
| `mistakes` | 常见错法 → 症状 → 改法 |
| `codes` | 全部错误码及各自触发条件 |
| `all`（缺省） | 以上全部，外加 `strictValidation`、`howToSubmit`、`howToVerify`、`timeoutUnits`（包秒/任务毫秒/编排秒三处口径）、`relatedSpecs`（指向 `describe_pipeline_spec`） |

### 三个「嵌套结构」键，以及它们只在远程主机生效

`startupProbe`、`parseRule`、`fileCollects` 内层是自由 Map，**平台一个键都不校验**，写错的后果是不生效而不是报错：

| 键 | 执行层实际读的东西 |
|----|--------------------|
| `startupProbe` | `type`（缺省 `tcp`，或 `http`，其它值走命令模式）、`port`、`url`、`command`、`timeoutSeconds`（默认 60）。探测固定在目标机的 `127.0.0.1` 上，没有 `host` 可改。`type=tcp` 却不给 `port`、命令模式不给 `command` → **按通过处理**，既不失败也不探测 |
| `parseRule` | `parserType`（`builtin` 或走脚本）、`builtinFormat`（**只认 `key_value` 与 `json`**）、`inputSource`（`stdout`/`file`）、`filePattern`、`scriptSource`（`inline`/`package`）、`scriptContent`、`scriptLanguage`。解析失败不改步骤状态：步骤仍是 success，原因只进 `test_results.result_reason` 与步骤日志的 `[WARN]` 行 |
| `fileCollects` | 每项 `name` / `path` / `type`（`file`/`directory`/`pattern`），需要 `fileCollectEnabled: true` 才跑；产物落 `task_steps.output_files`，在 `get_task_result` 的 `artifacts[]` 可见 |

三件都只在 **SSH 远程步骤**执行：`serverIds:[-1]`（平台本机）走的是另一条分支，步骤退出码 0 就直接落库，
不探测、不解析、不收文件，而且没有任何提示（`TaskExecutionService.executeStepOnServer` 开头即转入本地执行）。

还有一条跨步骤规则：**一个脚本只允许一个步骤开结果收集**（`resultParser` 或 `resultCollector` 为 true）。
界面是单选所以产不出违规形状，但手写 YAML 可以；后果是每台服务器插入多行 `test_results`，
而这张表没有步骤归属列，事后分不清哪行来自哪步 —— 严格校验按 `RESULT_COLLECTOR_MULTIPLE` 直接拒。
解析产物同时写 `test_results.parsed_data` 与 `task_steps.parsed_result`：前者属于结果行（MCP 用
`get_test_results` 读，含 `metrics`/`overallScore`/`artifacts`），后者是 `get_task_result` 里那一步的 `parsed_result`。
`key_value` 格式**一行只取一组键值**，值取到行尾（`mode=fast rounds=1` 会得到 `mode: "fast rounds=1"`），
要多个指标就一行一个，指标值一律按字符串返回。

不合格时返回的是结构化报错，不是泛泛的"解析失败"：

```json
{
  "ok": false,
  "reason_code": "PACKAGE_INVALID",
  "error": "autotest.yaml 有 5 处问题，未导入；按 errors 逐条改完再试，确认都不影响你要做的事可以传 strict=false",
  "errors": [
    { "code": "STEP_SCRIPT_NOT_FOUND", "path": "steps.step_1.script",
      "error": "包里没有 main.sch，这个值又不像命令，执行时会被当成 shell 命令跑（exit 127）",
      "hint": "包内文件：main.sh, lib/utils.sh" }
  ],
  "next": "describe_package_spec"
}
```

确认过这些问题不影响本次提交（例如历史包里残留的未知键），可以传 `strict=false` 放行；
问题不会静默吞掉，会原样写进返回的 `notes`。`import_package(strategy=SKIP)` 且脚本名已存在时
这次本来什么都不写，所以那条路径仍按原样返回 `SCRIPT_EXISTS`，不拿校验挡路。

> **界面路径没变**：`ScriptUploadResult` 只带 `warnings` 字段，
> 所以 `validationErrors` 目前在前端不可见。别把"MCP 会拦"当成"界面也会拦"。

---

## 使用方式

### 1. 创建新脚本

**方式一：上传包含 autotest.yaml 的脚本包**

```
脚本包结构：
├── autotest.yaml     # 配置文件
├── main.sh           # 主脚本
├── lib/              # 依赖文件
│   └── utils.sh
└── README.md
```

平台会自动解析 autotest.yaml 并应用配置。

**方式二：上传脚本包后手动配置**

1. 上传脚本包（不含 autotest.yaml）
2. 在前端界面配置参数和步骤
3. 保存后自动生成 autotest.yaml

### 2. 更新脚本配置

在前端修改脚本配置后，autotest.yaml 会自动同步更新。

### 3. 导出脚本包

下载脚本包时，包含最新的 autotest.yaml 文件，可用于：
- 版本控制
- 脚本迁移
- 配置备份

---

## 实现细节

### 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                      ScriptController                        │
│  - createScript() 上传时解析 YAML                            │
│  - updateScript() 更新时同步 YAML                            │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    ScriptConfigService                       │
│  - parseConfig() 解析 YAML 文件                              │
│  - saveConfig() 保存 YAML 文件                               │
│  - applyConfigToScript() 应用配置到实体                       │
│  - syncConfig() 同步更新 YAML                                │
│  - generateConfigFromDatabase() 从数据库生成配置              │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                      ScriptConfig                            │
│  - name, description, type, category, timeout               │
│  - parameters: List<ParameterConfig>                        │
│  - steps: Map<String, Object>                               │
│  - outputConfig: Map<String, Object>                        │
│  - resources: List<ResourceConfig>                          │
└─────────────────────────────────────────────────────────────┘
```

### 数据库映射

| YAML 字段 | 数据库字段 | 表 |
|-----------|-----------|-----|
| name | name | scripts |
| description | description | scripts |
| type | script_type | scripts |
| category | test_category | scripts |
| timeout | default_timeout | scripts |
| parameters | parameters | script_versions |
| steps | steps | script_versions |
| outputConfig | output_config | script_versions |

### 核心代码位置

| 文件 | 说明 |
|------|------|
| `backend/src/main/java/com/autotest/config/ScriptConfig.java` | 配置实体类 |
| `backend/src/main/java/com/autotest/service/ScriptConfigService.java` | 配置服务类 |
| `backend/src/main/java/com/autotest/controller/ScriptController.java` | 控制器集成 |

---

## API 接口

### 创建脚本（支持 autotest.yaml）

```http
POST /api/v1/scripts
Content-Type: application/json

{
  "name": "sol-network-config",
  "tempFilePath": "/tmp/upload/xxx",
  "fileList": [...]
}
```

如果 tempFilePath 目录包含 autotest.yaml，系统会自动解析并应用配置。

### 更新脚本（自动同步 YAML）

```http
PUT /api/v1/scripts/{id}
Content-Type: application/json

{
  "description": "更新后的描述",
  "parameters": [...],
  "steps": {...}
}
```

更新后 autotest.yaml 会自动同步。

---

## 测试方法

### 1. 准备测试脚本包

创建目录结构：
```
test-script/
├── autotest.yaml
└── main.sh
```

autotest.yaml 内容：
```yaml
name: test-script
description: 测试脚本
type: shell
category: cpu
timeout: 300

parameters:
  - name: TEST_PARAM
    type: string
    default: "hello"
    description: 测试参数

steps:
  step_1:
    displayName: 执行测试
    script: main.sh
    dependsOn: []
```

main.sh 内容：
```bash
#!/bin/bash
echo "TEST_PARAM = $TEST_PARAM"
echo "Hello from test script!"
```

### 2. 打包并上传

```bash
cd test-script
zip -r ../test-script.zip .
```

通过前端上传 test-script.zip。

### 3. 验证

1. 检查脚本名称、描述是否正确
2. 检查参数配置是否应用
3. 检查步骤配置是否正确
4. 检查脚本目录下是否有 autotest.yaml

---

## 常见问题

### Q: YAML 文件名必须是 autotest.yaml 吗？
A: 是的，固定为 `autotest.yaml`。

### Q: 如果不提供 autotest.yaml 会怎样？
A: 系统会使用默认配置，并在保存后自动生成 autotest.yaml。

### Q: 修改配置后 YAML 文件会自动更新吗？
A: 是的，通过 API 更新脚本配置后会自动同步到 autotest.yaml。

### Q: YAML 支持中文吗？
A: 支持，UTF-8 编码。

### Q: 参数类型支持哪些？
A: 目前支持 `string` 。

---

## 版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.0.0 | 2026-04-08 | 初始实现 |

---

## 相关文档

- [脚本管理 API](./api-scripts.md)
- [步骤配置说明](./steps-configuration.md)
- [参数配置说明](./parameters-configuration.md)
