# finance-app 启动模块

## 模块定位

`finance-app` 是 Java 后端唯一启动模块，只负责装配 `finance-data`、`finance-security`、`finance-service` 和 `finance-ai` 并启动 Spring Boot 应用，不放业务逻辑。

## 依赖关系

```text
finance-app
  -> finance-data
  -> finance-security
  -> finance-service
  -> finance-ai
```

`FinanceApplication` 位于 `com.scrapider.finance` 根包，启动后会扫描同根包下所有模块的 Bean。

## 启动

在项目根目录执行：

```bash
mvn -pl backend-java/finance-app -am spring-boot:run
```

在 `backend-java` 目录执行：

```bash
mvn -pl finance-app -am spring-boot:run
```

## 构建

```bash
mvn -pl backend-java/finance-app -am package
```
