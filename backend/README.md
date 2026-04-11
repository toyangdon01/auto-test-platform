# 自动化测试管理平台 - 后端

> Spring Boot 3.x 后端项目脚手架

## 技术栈

- **Java**: 17
- **Spring Boot**: 3.2.3
- **MyBatis-Plus**: 3.5.5
- **PostgreSQL**: 13+
- **Redis**: 用于缓存和任务状态管理

## 项目结构

```
backend/
├── pom.xml                          # Maven 依赖配置
├── src/
│   └── main/
│       ├── java/com/autotest/
│       │   ├── AutoTestPlatformApplication.java   # 启动类
│       │   ├── common/              # 通用类
│       │   │   ├── ApiResponse.java # 统一响应
│       │   │   ├── PageRequest.java # 分页请求
│       │   │   └── PageResult.java  # 分页结果
│       │   ├── config/              # 配置类
│       │   │   ├── MybatisPlusConfig.java
│       │   │   ├── AsyncConfig.java
│       │   │   └── StorageConfig.java
│       │   ├── controller/          # 控制器
│       │   │   ├── ServerController.java
│       │   │   ├── ServerGroupController.java
│       │   │   ├── ScriptController.java
│       │   │   └── TaskController.java
│       │   ├── dto/                 # 数据传输对象
│       │   │   ├── request/         # 请求 DTO
│       │   │   └── response/        # 响应 DTO
│       │   ├── entity/              # 实体类
│       │   │   ├── Server.java
│       │   │   ├── ServerGroup.java
│       │   │   ├── Script.java
│       │   │   ├── ScriptVersion.java
│       │   │   ├── Task.java
│       │   │   ├── TaskServer.java
│       │   │   ├── Metric.java
│       │   │   ├── Report.java
│       │   │   ├── TestResult.java
│       │   │   ├── MetricDefinition.java
│       │   │   ├── ResultRule.java
│       │   │   └── SystemConfig.java
│       │   ├── exception/           # 异常处理
│       │   │   ├── BusinessException.java
│       │   │   └── GlobalExceptionHandler.java
│       │   ├── mapper/              # MyBatis Mapper
│       │   ├── service/             # 服务接口
│       │   │   ├── ServerService.java
│       │   │   ├── TaskService.java
│       │   │   └── impl/            # 服务实现
│       │   └── util/                # 工具类
│       └── resources/
│           └── application.yml      # 配置文件
└── README.md
```

## 实体类清单

| 实体 | 表名 | 说明 |
|------|------|------|
| ServerGroup | server_groups | 服务器分组 |
| Server | servers | 服务器 |
| Script | scripts | 脚本 |
| ScriptVersion | script_versions | 脚本版本 |
| Task | tasks | 任务 |
| TaskServer | task_servers | 任务服务器关联 |
| Metric | metrics | 指标数据 |
| Report | reports | 报告 |
| TestResult | test_results | 测试结果 |
| MetricDefinition | metric_definitions | 指标定义 |
| ResultRule | result_rules | 结果判定规则 |
| SystemConfig | system_config | 系统配置 |

## 快速开始

### 1. 环境准备

- JDK 17+
- Maven 3.6+
- PostgreSQL 13+
- Redis 6+

### 2. 创建数据库

```sql
CREATE DATABASE auto_test_platform;
```

执行数据库初始化脚本：`../database/init.sql`

### 3. 修改配置

编辑 `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/auto_test_platform
    username: your_username
    password: your_password
  data:
    redis:
      host: localhost
      port: 6379
```

### 4. 启动项目

```bash
# 编译
mvn clean package -DskipTests

# 运行
java -jar target/auto-test-platform-1.0.0-SNAPSHOT.jar

# 或使用 Maven
mvn spring-boot:run
```

### 5. 访问接口文档

- Swagger UI: http://localhost:8080/api/v1/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/api/v1/docs

## API 接口

### 服务器管理 `/servers`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /servers | 获取服务器列表 |
| GET | /servers/{id} | 获取服务器详情 |
| POST | /servers | 创建服务器 |
| PUT | /servers/{id} | 更新服务器 |
| DELETE | /servers/{id} | 删除服务器 |
| POST | /servers/{id}/test | 测试连接 |
| GET | /servers/{id}/status | 获取状态 |
| POST | /servers/{id}/refresh | 刷新信息 |

### 脚本管理 `/scripts`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /scripts | 获取脚本列表 |
| GET | /scripts/{id} | 获取脚本详情 |
| POST | /scripts | 创建脚本 |
| PUT | /scripts/{id} | 更新脚本 |
| DELETE | /scripts/{id} | 删除脚本 |

### 任务管理 `/tasks`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /tasks | 获取任务列表 |
| GET | /tasks/{id} | 获取任务详情 |
| POST | /tasks | 创建任务 |
| PUT | /tasks/{id} | 更新任务 |
| DELETE | /tasks/{id} | 删除任务 |
| POST | /tasks/{id}/execute | 执行任务 |
| POST | /tasks/{id}/cancel | 取消任务 |
| POST | /tasks/{id}/retry | 重试任务 |
| GET | /tasks/{id}/progress | 获取进度 |
| GET | /tasks/{id}/logs | 获取日志 |

## 开发指南

### 添加新实体

1. 在 `entity/` 目录创建实体类
2. 在 `mapper/` 目录创建 Mapper 接口
3. 在 `service/` 目录创建服务接口和实现
4. 在 `controller/` 目录创建控制器

### 请求/响应 DTO

- 请求 DTO 放在 `dto/request/`
- 响应 DTO 放在 `dto/response/`
- 使用 `@Valid` 进行参数校验

### 异常处理

```java
// 抛出业务异常
throw BusinessException.of("错误信息");

// 自定义错误码
throw BusinessException.of(1001, "自定义错误");
```

### 分页查询

```java
// 使用 PageRequest
PageResult<Entity> result = PageResult.of(mapper.selectPage(request.toPage(), wrapper));
```

## 待实现功能

- [ ] SSH 连接工具类
- [ ] 脚本执行引擎
- [ ] 指标采集服务
- [ ] 报告生成服务
- [ ] 文件上传/下载
- [ ] 定时任务调度
- [ ] WebSocket 实时推送
- [ ] 密码加密存储

## 配置项说明

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| autotest.storage.scripts-path | /data/auto-test/scripts | 脚本存储路径 |
| autotest.storage.reports-path | /data/auto-test/reports | 报告存储路径 |
| autotest.storage.temp-path | /data/auto-test/temp | 临时文件路径 |
| autotest.storage.results-path | /data/auto-test/results | 结果文件路径 |
| autotest.ssh.connect-timeout | 30000 | SSH 连接超时(ms) |
| autotest.task.max-parallel | 10 | 最大并行任务数 |

---

**版本**: 1.0.0  
**创建日期**: 2026-03-09
