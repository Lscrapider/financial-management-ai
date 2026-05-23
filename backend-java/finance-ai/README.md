# finance-ai AI 能力模块

本目录是理财分析 AI 项目的 AI Java 能力模块，不独立启动。

`finance-service` 依赖本模块，并由 `finance-service` 的 `FinanceApplication` 扫描这里的 Controller、Service 和 Spring AI 配置。

当前模块包含：

- `/api/ai/chat` Chat 接口
- Spring AI ChatClient 调用逻辑
- 后续 Query Rewrite、Tool Calling 和 Chat 编排逻辑

## 构建

在项目根目录执行：

```bash
mvn -pl backend-java/finance-ai -am package
```
