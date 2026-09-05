# 宿主机构建、Docker 运行设计

## 目标

让 Jenkins 在宿主机完成 Java、Python 和前端产物构建；Docker 镜像只接收已构建的产物并运行，避免在镜像构建阶段执行 Maven、pnpm 或模型下载。

## 架构

- Jenkins 继续负责检出代码、注入生产环境文件、校验 Compose 配置、创建共享网络和启动 Compose 服务。
- Java 在宿主机用 Maven 构建 `finance-app`，运行镜像仅复制 `finance-app/target/*.jar`。
- 前端在宿主机用 pnpm 构建 `apps/web-ele/dist`，Nginx 镜像仅复制静态文件。
- Python 在宿主机用 Python 3.12 生成离线 wheel 包和 Hugging Face 模型缓存；两个 Python 运行镜像在无网络模式下安装 wheel 包并复制模型缓存。
- 根目录 `.dockerignore` 只向两个 Python 构建上下文发送 Python 源码、初始化脚本和所需的 CI 产物，避免将宿主机前端依赖目录传入 Docker。

## 约束

- Docker Compose、`database-common-network`、Jenkins Secret File 与现有生产环境变量校验保持不变。
- Jenkins 服务用户必须能在标准 PATH 中访问 `java`、`javac`、`mvn`、`node`、`pnpm`、`python3`、`python3 -m pip`、`python3 -m venv` 和 `docker`。
- 不使用 `/home/lan/.nvm` 作为 Jenkins 工具来源；该目录对 `jenkins` 用户不可遍历。
- Java 项目声明的目标版本为 17；宿主机 JDK 21 可以编译，Java 运行镜像继续使用 JRE 17。
- Python 运行镜像统一使用 Python 3.12，以匹配宿主机构建的 wheel 包。
- 生成的 `.ci-artifacts/`、Java `target/` 和前端 `dist/` 不能进入 Git。
- 前端 Docker 构建上下文必须显式重新包含 `apps/web-ele/dist`，根 Docker 构建上下文必须显式包含 Python wheel 包和模型缓存。

## 验收

1. Jenkins 的宿主机工具预检能在工具缺失或权限不可见时给出明确错误。
2. Java 和前端 Dockerfile 不再包含 Maven、Node 或 pnpm 构建阶段。
3. Python Dockerfile 不再从网络下载依赖或模型；依赖安装仅使用 Jenkins 生成的离线 wheel 包。
4. `docker compose --env-file .env -f docker/docker-compose.yml config --quiet` 可通过，并且各镜像能从对应构建上下文读取产物。
