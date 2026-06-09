# finance-security 安全模块

## 模块定位

`finance-security` 是 Java 后端公共安全模块，不独立启动。它提供 Spring Security 配置、JWT、登录用户、Bearer Token 过滤器、Token 黑名单和刷新会话 Cookie 能力。

## 主要内容

```text
src/main/java/com/scrapider/finance/
├── config/
│   └── SecurityConfig.java
└── security/
    ├── AppUserDetailsService.java
    ├── BearerTokenAuthenticationFilter.java
    ├── JwtUtils.java
    ├── LoginUser.java
    ├── RefreshSessionCookieHandler.java
    ├── RefreshSessionStore.java
    └── TokenStore.java
```

## 依赖关系

```text
finance-security
  -> finance-data
```

`finance-service` 和 `finance-ai` 通过本模块复用登录态对象和 Token 校验能力。
