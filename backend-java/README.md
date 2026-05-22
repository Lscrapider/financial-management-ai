# Java 后端服务

## 服务定位

本目录是理财分析 AI 项目的 Java 后端服务，基于 Spring Boot、Maven、MyBatis-Plus 和 PostgreSQL 构建。

Java 服务主要负责业务接口、数据编排、定时任务、数据库访问和对 Python AI 服务的调用。

## 基础信息

- 包名：`com.scrapider.finance`
- 构建工具：Maven
- Web 框架：Spring Boot
- 分层方式：MVC 三层结构
- ORM 工具：MyBatis-Plus
- 定时任务：Spring Scheduling
- 代码简化：Lombok
- 数据库：PostgreSQL

## 运行前提

本服务基于 Spring Boot 3.x，要求使用 JDK 17 或更高版本。

如果在本地 IDE 中启动，请确认项目 SDK、Maven Runner 使用的 JDK 都是 17+。如果使用 Docker 启动，请先确认 Docker Desktop 或 Docker daemon 已启动。

## 启动方式

### 本地启动

```bash
mvn spring-boot:run
```

当前项目没有提交 Maven Wrapper，因此本地启动需要先安装 Maven。

### Docker 启动

在项目根目录执行：

```bash
docker compose -f docker/docker-compose.yml up --build backend-java
```

默认会读取以下数据库环境变量：

- `POSTGRES_URL`
- `POSTGRES_USERNAME`
- `POSTGRES_PASSWORD`

如果没有设置，Docker Compose 会使用连接宿主机 PostgreSQL 的默认值。

## 目录结构

```text
backend-java/
├── pom.xml
├── Dockerfile
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/scrapider/finance/
    │   │       ├── FinanceApplication.java    # Spring Boot 启动类
    │   │       ├── controller/                # 控制层
    │   │       ├── service/                   # 服务层接口
    │   │       │   └── impl/                  # 服务层实现
    │   │       ├── manage/                    # MyBatis-Plus 管理封装
    │   │       ├── mapper/                    # MyBatis-Plus Mapper
    │   │       ├── domain/                    # 领域对象
    │   │       │   ├── po/                    # 持久化对象
    │   │       │   ├── dto/                   # 数据传输对象
    │   │       │   ├── vo/                    # 视图返回对象
    │   │       │   ├── param/                 # 请求参数对象
    │   │       │   ├── enums/                 # 枚举
    │   │       │   └── constant/              # 常量
    │   │       ├── config/                    # Spring 配置
    │   │       └── task/                      # 定时任务
    │   └── resources/
    │       ├── application.yml                # 应用配置
    │       └── mapper/                        # Mapper XML
    └── test/
        └── java/com/scrapider/finance/        # 测试代码
```
