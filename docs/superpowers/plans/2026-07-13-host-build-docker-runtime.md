# 宿主机构建、Docker 运行 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Java、Python 和 Vue 构建迁移到 Jenkins 宿主机，令 Docker 镜像仅运行已构建产物。

**Architecture:** Jenkins 在工作区生成 Java Jar、前端静态文件、Python wheel 包和模型缓存。Java、Python 与前端 Dockerfile 只复制这些产物；Compose 部署、共享网络和生产环境文件处理维持既有契约。

**Tech Stack:** Jenkins Pipeline、Maven、JDK 21、Python 3.12、pnpm、Docker Compose、Nginx、Spring Boot。

## Global Constraints

- 保留 `database-common-network`、现有 Jenkins Secret File `finance-prod-env-file` 和现有环境变量校验。
- 不新增单元测试；配置变更以工具预检、构建产物检查、Compose 配置校验和镜像构建验证验收。
- 不修改 `database/rabbitmq/definitions.json` 中既有的用户改动。
- Jenkins 必须使用其自身可访问的系统级 Node/pnpm，不能读取 `/home/lan/.nvm`。
- Python wheel 与运行镜像均使用 Python 3.12。

---

### Task 1: Jenkins 宿主机工具和产物预检

**Files:**
- Modify: `Jenkinsfile`

**Interfaces:**
- Consumes: Jenkins `WORKSPACE`、`.env`、系统级构建工具。
- Produces: `.ci-artifacts/python/{database-wheels,ai-wheels,ai-huggingface}` 与 Java/前端标准产物目录。

- [x] **Step 1: 添加构建产物目录变量和工具预检阶段**

在 Pipeline environment 中声明 `HOST_BUILD_ARTIFACT_DIR = '.ci-artifacts'`，并在环境校验后执行：

```sh
for tool in java javac mvn node pnpm python3 docker; do
  command -v "$tool" >/dev/null || exit 1
done
python3 -m pip --version
python3 -m venv --help >/dev/null
docker compose version
```

- [x] **Step 2: 构建 Java、Python 与前端产物**

使用以下实际入口：

```sh
(cd backend-java && mvn -q -pl finance-app -am -DskipTests clean package)
python3 -m venv .ci-artifacts/python/venv
.ci-artifacts/python/venv/bin/pip wheel --wheel-dir .ci-artifacts/python/database-wheels -r database/requirements.txt
.ci-artifacts/python/venv/bin/pip wheel --wheel-dir .ci-artifacts/python/ai-wheels -r ai-python/requirements.txt
(cd frontend-vue && pnpm install --frozen-lockfile && pnpm -F @vben/web-ele build)
```

- [x] **Step 3: 下载 Python 模型到工作区缓存**

从 Jenkins 注入的 `.env` 中只读取 `EMBEDDING_MODEL_NAME` 和 `HF_ENDPOINT`，并在 `HF_HOME=.ci-artifacts/python/ai-huggingface` 下运行 `SentenceTransformer` 下载模型；不打印环境文件内容。

- [x] **Step 4: 验证产物**

运行：

```sh
test -f backend-java/finance-app/target/*.jar
test -f frontend-vue/apps/web-ele/dist/index.html
test -d .ci-artifacts/python/database-wheels
test -d .ci-artifacts/python/ai-wheels
test -d .ci-artifacts/python/ai-huggingface
```

### Task 2: 将 Java 与前端 Dockerfile 改为运行时镜像

**Files:**
- Modify: `backend-java/finance-service/Dockerfile`
- Modify: `frontend-vue/Dockerfile`
- Modify: `frontend-vue/.dockerignore`

**Interfaces:**
- Consumes: `backend-java/finance-app/target/*.jar`、`frontend-vue/apps/web-ele/dist`。
- Produces: 仅复制已构建产物的 Java 运行镜像和 Nginx 前端镜像。

- [x] **Step 1: 删除 Java Maven 构建阶段**

保留 `eclipse-temurin:17-jre`，并改为：

```dockerfile
COPY finance-app/target/*.jar app.jar
```

- [x] **Step 2: 删除前端 Node/pnpm 构建阶段**

保留 `nginx:1.27-alpine` 和 Nginx 模板，直接复制：

```dockerfile
COPY apps/web-ele/dist /usr/share/nginx/html/finance
```

- [x] **Step 3: 让构建上下文包含前端 dist**

将 `frontend-vue/.dockerignore` 中的 `dist` 改为 `/dist`，从而只忽略前端根目录的 dist，不屏蔽 `apps/web-ele/dist`。

### Task 3: 将 Python Dockerfile 改为离线运行时镜像

**Files:**
- Modify: `database/Dockerfile`
- Modify: `ai-python/Dockerfile`

**Interfaces:**
- Consumes: 根构建上下文中的 `.ci-artifacts/python/*`。
- Produces: 不在 Docker 构建阶段联网下载 Python 依赖或模型的运行镜像。

- [x] **Step 1: 对齐 database Python 版本并安装离线 wheel**

使用 `python:3.12-slim`，复制 `.ci-artifacts/python/database-wheels`，并使用：

```dockerfile
RUN pip install --no-cache-dir --no-index --find-links=/wheels -r /app/database/requirements.txt
```

- [x] **Step 2: 使 AI worker 离线安装依赖和模型缓存**

复制 `.ci-artifacts/python/ai-wheels` 与 `.ci-artifacts/python/ai-huggingface`，使用同样的 `--no-index --find-links` 安装命令，并删除原有 `SentenceTransformer` 下载命令。

### Task 4: 忽略 CI 构建产物并调整镜像构建阶段

**Files:**
- Modify: `.gitignore`
- Create: `.dockerignore`
- Modify: `Jenkinsfile`

**Interfaces:**
- Consumes: Docker Compose 服务名 `database-init`、`finance-service`、`finance-python-worker`、`finance-frontend`。
- Produces: 不提交大体积构建产物、无 `--no-cache` 的运行镜像构建。

- [x] **Step 1: 显式忽略根目录 `.ci-artifacts/`**

在 `.gitignore` 中加入：

```gitignore
/.ci-artifacts/
```

- [x] **Step 2: 缩小 Python 镜像的根构建上下文**

创建根目录 `.dockerignore`，默认排除全部文件后仅重新包含 `database/**`、`ai-python/**`、`env_loader.py` 以及 `.ci-artifacts/python/{database-wheels,ai-wheels,ai-huggingface}/**`。这会阻止宿主机构建的 `frontend-vue/node_modules` 和其他无关文件进入两个 Python 镜像上下文。

- [x] **Step 3: 构建运行镜像**

移除仅针对前端的 `--no-cache`，因为前端静态产物变更会使 Docker 的 `COPY` 层失效：

```sh
docker compose --env-file "$ENV_FILE_PATH" -f "$COMPOSE_FILE_PATH" build database-init finance-service finance-python-worker finance-frontend
```

### Task 5: 静态与部署配置验证

**Files:**
- Verify: `Jenkinsfile`
- Verify: `backend-java/finance-service/Dockerfile`
- Verify: `database/Dockerfile`
- Verify: `ai-python/Dockerfile`
- Verify: `frontend-vue/Dockerfile`
- Verify: `docker/docker-compose.yml`

- [x] **Step 1: 检查补丁格式和产物路径引用**

Run: `git diff --check`

Expected: 退出码 0。

- [x] **Step 2: 在具备 Jenkins Secret File 生成的环境文件后校验 Compose**

Run: `docker compose --env-file .env -f docker/docker-compose.yml config --quiet`

Expected: 退出码 0，且不输出环境文件内容。

- [ ] **Step 3: 在 Jenkins 中运行一次流水线**

Expected: 工具预检、宿主机产物构建、离线运行镜像构建、Compose 启动与服务状态阶段依次通过。
