# Java 后端模块

本目录是理财分析 AI 项目的 Java 后端聚合目录，所有 Java 服务模块都放在这里。

## 模块

- `finance-service`：主业务服务，负责登录、行情、数据库访问、任务调度、业务 API 和应用启动。
- `finance-ai`：AI 能力模块，被 `finance-service` 依赖，负责 Spring AI、百炼模型调用、Query Rewrite 和 Chat 编排。

## 构建

在项目根目录执行：

```bash
mvn -pl backend-java -am package
```

也可以只构建单个模块：

```bash
mvn -pl backend-java/finance-service -am package
mvn -pl backend-java/finance-ai -am package
```
