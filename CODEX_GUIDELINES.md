# Codex Coding Guidelines

本项目中，Codex 在写代码、改代码、重构或审查代码前，必须先向用户说明本次实现逻辑。

## Before Coding

每次动手前先说明：

1. 要解决的问题或目标。
2. 准备采用的实现方式。
3. 预计会修改或新增的文件。
4. 准备用什么方式验证结果。

如果需求不清楚，先问清楚再写代码。

## While Coding

- 优先遵循项目现有目录结构和编码风格。
- 每次只做和当前需求直接相关的最小改动。
- 不做无关重构，不改无关文件。
- 写代码逻辑时必须使用代码相关 skill；当前默认使用 `karpathy-guidelines`。
- Spring Boot 理财/金融后端项目必须遵守 `com.scrapider.finance` 根包下的分层规范。

## After Coding

完成后说明：

1. 实际修改了什么。
2. 验证命令或验证结果。
3. 如果没有验证成功，要说明原因。

## Spring Boot Backend Structure

本项目 Java 后端是 Spring Boot 理财/金融后端项目，根包名为 `com.scrapider.finance`。生成、修改或重构后端代码时，必须按照现有项目结构放置代码。

### Package Responsibilities

- `api`：放外部接口调用类，例如股票行情、基金数据、市场数据等第三方 API 调用逻辑。
- `config`：放 Spring 配置类，例如 `RestTemplate`、MyBatis Plus、Redis、Swagger、线程池等配置。
- `controller`：放接口入口类，只负责接收请求、校验参数、调用 `service`、返回结果。不要在 `controller` 中写复杂业务逻辑，也不要直接操作数据库。
- `domain`：放项目中的业务数据对象。
- `domain.constant`：放业务常量，例如固定状态值、缓存 key 前缀、默认配置等。
- `domain.dto`：放数据传输对象，主要用于内部层之间传递数据，或映射第三方接口返回结果。
- `domain.enums`：放业务枚举，例如状态、类型、风险等级、市场类型等。
- `domain.param`：放请求参数对象，用于接收前端传入的查询、新增、修改等参数。
- `domain.po`：放数据库持久化对象，通常和数据库表一一对应，供 `mapper` 使用。
- `domain.vo`：放返回给前端的展示对象，不建议直接把 PO 返回给前端。
- `manage`：放 MyBatis Plus 的管理类，一般实现 `ServiceImpl`。该层中不存在复杂业务逻辑，只用于让 MP 查询数据库更方便、更清晰。
- `mapper`：放 MyBatis / MyBatis Plus 的数据库访问接口。
- `service.impl`：放 `service` 的具体实现类，主要业务逻辑写在这里。
- `task`：放定时任务或后台任务，例如定时拉取行情数据、同步数据等。
- `FinanceApplication`：Spring Boot 项目启动类。

### Layering Rules

标准调用链路：

```text
Controller -> Service -> Manage / API / Mapper -> Domain
```

新增功能时必须遵守：

1. `Controller` 只做接口入口，不写复杂业务。
2. `Service` 负责业务逻辑。
3. `Manage` 只负责最小数据库操作封装，优先基于 MyBatis Plus 完成基础查询、保存、批量保存、更新等动作，不放业务编排、参数归一化和 VO 转换。
4. `Mapper` 只负责数据库访问；只有 MyBatis Plus 函数式查询无法表达或明显不适合时，才在 `Mapper` 中手写 SQL。
5. `API` 只负责第三方接口调用。
6. 请求入参使用 `Param`。
7. 返回前端使用 `VO`。
8. 数据库对象使用 `PO`。
9. 不要直接把 `PO` 返回给前端。
10. 不要在 `Controller` 中直接调用 `Mapper`，`Controller` 只能调用 `Service` 作为传递。
11. 数据库查询、插入、修改和删除操作优先通过 `manage` 层基于 MyBatis Plus 完成；业务逻辑必须放在 `service` / `service.impl`，不要下沉到 `manage`。
12. Java 类中调用本类方法时必须显式使用 `this`。
13. Java 代码中优先使用 lambda 风格编写集合处理、回调和函数式接口逻辑。
14. `JsonNode`、第三方响应、DTO 或中间数据构建具体 `PO` 时，构建逻辑优先放到对应实体类的静态方法中，例如 `StockQuoteSnapshotPO.fromApiResponse(...)`，不要散落在 `task`、`service` 或 `manage` 中。
15. 常见判空、字符串处理、集合判断、数字转换、日期转换等通用操作优先使用 Hutool 工具包，例如 `StrUtil`、`CollUtil`、`NumberUtil`、`DateUtil`；不要重复手写通用工具逻辑，除非 Hutool 不适合当前场景。
16. 未使用到的代码要及时删除，包括未使用的类、方法、字段、局部变量、import、配置项和依赖；不要为“以后可能用到”保留死代码。
17. 分页查询接口使用 `pageSize` 和 `pageNum`，查询类接口使用 GET，提交类接口使用 POST。
18. `Controller` 中不要编写局部 `@ExceptionHandler`。异常必须由模块级或全局 `@RestControllerAdvice` 统一处理，并且异常处理器必须使用日志打印异常对象，保留完整堆栈，避免吞掉真实报错原因。
