# AI Chat Agent 架构方案

本文档描述 AI Chat Agent 化后的 Java、Python LangChain、RabbitMQ、HTTP、WebSocket 与内部签名逻辑。

## 目标

将当前 AI Chat 从同步问答升级为可调度工具、查询业务数据、读取记忆和持续向前端推送过程事件的 Agent 流程。

核心目标：

- 前端通过 WebSocket 与 Java 后端保持会话连接。
- Java 负责用户鉴权、WebSocket 推送、Agent Session 管理、数据访问边界和内部接口校验。
- Java 通过 RabbitMQ 触发 Python Agent 流程。
- Python 基于 LangChain 承载 LLM、Planner、Memory、Tool Calling、Answer 等 Agent 执行逻辑。
- Python 需要查询数据库或业务数据时，通过 Java 暴露的内部数据网关 HTTP 接口获取。
- Python 生成过程事件和最终答案后，通过 Java 内部 callback 接口回传。
- Java 再通过 WebSocket 将 Agent 事件推送给前端。
- Java 不直接调用 LLM，不参与 Agent 规划和答案生成。

## 总体链路

```text
前端
  -> WebSocket 发送用户消息到 Java
Java
  -> 校验用户登录态
  -> 创建 Agent Session
  -> 通过 RabbitMQ 发布 agent.run.start 消息
Python Worker
  -> 消费 RabbitMQ 消息
  -> 初始化 Python LangChain Agent
  -> 调用 LLM 做意图理解、工具规划和答案生成
  -> 需要业务数据时调用 Java 内部数据网关
Java
  -> 校验 Agent 请求签名、过期时间、nonce、scope
  -> 查询数据库、InfluxDB、知识库或业务服务
  -> 返回受控数据
Python Worker
  -> 基于 LangChain 工具结果、记忆和原始问题生成过程事件或最终答案
  -> 通过 HTTP callback 回传 Java
Java
  -> 校验 callback 签名
  -> 通过 WebSocket 推送给前端
```

## 模块职责

### 前端

- 与 Java 建立 WebSocket 连接。
- 发送用户问题。
- 接收 Agent 过程事件、工具调用进度、最终答案和错误提示。
- 不接触 `sessionSecret`。

### Java 后端

- 校验前端用户身份。
- 创建和保存 Agent Session。
- 通过 RabbitMQ 触发 Python Agent。
- 暴露内部数据网关接口给 Python 查询业务数据。
- 暴露内部 callback 接口接收 Python 事件。
- 校验 Python HTTP 请求的 HMAC 签名。
- 控制工具白名单、查询 limit、用户权限和数据隔离。
- 通过 WebSocket 向前端推送消息。
- 不直接调用大模型。
- 不执行 Planner、Memory、Tool Calling 和 Answer 逻辑。

### Python Agent

- 消费 RabbitMQ 中的 Agent 启动消息。
- 使用 LangChain 初始化 Agent Runtime。
- 使用 OpenAI 兼容 LLM 客户端调用 DeepSeek 或其他模型。
- 在 Python 侧执行 Agent 流程：
  - Planner：由 LLM 和 LangChain Tool Calling 拆解用户意图，生成工具计划。
  - Memory：读取短期对话上下文、用户偏好和历史记录。
  - Tool Calling：LangChain Tool 根据计划调用 Java 内部数据网关。
  - Answer：LLM 结合工具结果、记忆和原始问题生成答案。
- 所有调用 Java 的 HTTP 请求都必须使用 `sessionSecret` 生成 HMAC 签名。
- 不落库保存 `sessionSecret`，不打印包含 `sessionSecret` 的日志。
- 不把 `sessionSecret` 写入 prompt、memory、tool description 或 LLM 可见上下文。

## Python LangChain Runtime

Python 是 Agent 和 LLM 能力承载层。Java 只通过 RabbitMQ 启动 Agent，不直接调用 LLM。

建议使用 LangChain 的 Tool Calling Agent 作为第一阶段运行时：

```text
RabbitMQ AgentRunHandler
  -> AgentExecutor
      -> ChatOpenAI 或 OpenAI 兼容 Chat Model
      -> Memory
      -> LangChain Tools
          -> MarketQuoteTool
          -> MarketKlineTool
          -> KnowledgeSearchTool
          -> ReportLatestTool
          -> WatchPoolContextTool
      -> AgentCallbackClient
```

LangChain Tool 不直接访问数据库。每个 Tool 只负责把模型生成的结构化参数转成 Java 数据网关请求。

示例：

```text
MarketQuoteTool
  -> JavaDataGatewayClient.call("market.quote", params)

KnowledgeSearchTool
  -> JavaDataGatewayClient.call("knowledge.search", params)
```

第一阶段先使用 LangChain Tool Calling Agent；如果后续需要复杂状态机、循环规划、人工确认或长耗时研究任务，再升级为 LangGraph。

### Tool Calling 执行语义

当前第一版 Agent Executor 是一次性 Tool Calling 流程，不是循环式多工具 Agent。

```text
1. Python 将当前问题、短期记忆和工具定义发送给 LLM
2. LLM 只负责判断是否需要工具，并返回结构化 tool_calls
3. Python 读取 tool_calls，由代码执行对应 LangChain Tool
4. LangChain Tool 通过 Java 内部数据网关查询业务数据
5. Python 将工具结果包装为 ToolMessage，再次发送给 LLM
6. LLM 基于工具结果生成最终答案
7. Python callback Java，Java 通过 WebSocket 推送前端
```

模型不会直接调用 Java 接口，也不会直接访问数据库。Tool Calling 的本质是：

```text
模型提出工具调用请求
Python 执行工具
Java 执行业务数据查询
Python 把结果交回模型
模型生成最终答案
```

这条链路可以理解为两层调用：

```text
第一层：LLM Tool Calling
  - 由模型决定是否调用 market_quote、market_kline、knowledge_search 等工具
  - 用自然语言上下文生成结构化工具参数

第二层：Java Data Gateway Calling
  - 由 Python Tool 使用 HMAC 签名调用 Java 内部数据网关
  - Java 负责 scope、userId、limit、参数和权限校验
  - Java 只暴露 action 白名单，不暴露 SQL，不允许 Python 直接查库
```

第一版对工具结果统一走二次模型生成答案，适合行情分析、买卖风险说明、对比解读等需要综合表达的场景。后续可以增加 `ToolResultPolicy`：

```text
direct_return:
  天气、汇率、单字段状态、最新价格等简单查询，由代码格式化后直接返回。

model_analyze:
  行情分析、K 线、财务指标、知识库召回、投资风险说明等复杂结果，再交给模型生成答案。
```

无论采用哪种结果策略，Java 数据网关仍然是业务数据访问边界，Python 和 LLM 都不直接接触数据库。

## 对话短期记忆与清理

短期记忆以 `conversationId + userId` 为边界，不绑定 WebSocket Session，也不绑定 Agent Session。

```text
conversationId = 对话上下文身份
userId = 对话归属和权限校验
WebSocket Session = 当前传输连接
Agent Session = Python 调 Java 内部接口的临时签名凭证
```

第一版不支持多会话栏，`conversationId` 由 Java 生成和复用，前端不生成也不传入 `conversationId`：

```text
/ws/ai-chat?accessToken=...
```

Java 在 WebSocket 握手阶段完成：

```text
1. 从 accessToken 解析 userId
2. 查询当前 userId 最近一个 active conversation
3. 如果 active conversation 存在，复用它的 conversationId
4. 如果 active conversation 不存在，由 Java 生成 conversationId 并创建记录
5. activeCount + 1
6. cleanupVersion + 1
7. 把 userId、conversationId 写入 WebSocketSession attributes
```

同一个 WebSocket 后续发送多条消息时不再重复增加 `activeCount`。Java 收到用户消息后：

```text
1. 从 WebSocketSession attributes 读取后端绑定的 conversationId
2. 保存 user message 到 ai_chat_message
3. 创建 Agent Session
4. 发 RabbitMQ agent.run.start 给 Python
```

Python 每次 Agent Run 固定通过 Java 内部数据网关读取最近短期记忆：

```text
action = conversation.history
params = {
}
limit = 20
```

Java 使用 Agent Session 中的 `userId + conversationId` 查询 `ai_chat_message`，不允许 Python 自行指定任意用户。Python 将短期记忆、当前问题和工具结果交给 LLM 生成答案。

Java 收到 Python `final_answer` callback 后：

```text
1. 验签
2. 保存 assistant message 到 ai_chat_message
3. 通过 WebSocket 推送给前端
```

WebSocket 关闭后不立即删除短期记忆。Java 使用 `activeCount + cleanupVersion` 防止误删：

```text
WS close:
  activeCount - 1
  if activeCount == 0:
      发送 30 分钟延迟 conversation.cleanup 消息，携带当前 cleanupVersion

用户重新连接:
  Java 按 userId 复用最近 active conversation
  activeCount + 1
  cleanupVersion + 1

cleanup 消费:
  if message.cleanupVersion != conversation.cleanupVersion:
      跳过
  else:
      根据 ai_chat_message 生成 conversation_summary 长期记忆
      保存 ai_user_memory
      删除 ai_chat_message 短期消息
      conversation.status = cleaned
```

第一版 cleanup 摘要使用 Java 确定性文本压缩，保证清理链路不依赖 LLM 可用性。后续接入长期记忆增强时，可以把摘要生成替换为 Python `agent.memory.summarize`：Java cleanup 发布总结任务，Python 读取短期消息生成高质量摘要，Java 保存 `ai_user_memory` 后再删除短期消息。

建议表：

```text
ai_chat_conversation
- id
- user_id
- conversation_id
- title
- status
- cleanup_version
- created_at
- updated_at
- cleaned_at

ai_chat_message
- id
- user_id
- conversation_id
- message_id
- role
- content
- metadata_json
- created_at

ai_user_memory
- id
- user_id
- memory_type
- title
- content
- metadata_json
- source_conversation_id
- confidence
- created_at
- updated_at
- deleted
```

## Agent Session

当前端 WebSocket 消息进入 Java 后，Java 创建一次 Agent Session。

建议字段：

```json
{
  "agentSessionId": "UUID",
  "sessionSecret": "base64url-encoded-random-32-bytes",
  "userId": 10001,
  "conversationId": "conv-xxx",
  "messageId": "msg-xxx",
  "scopes": [
    "market.quote",
    "market.kline",
    "market.intraday",
    "knowledge.search",
    "report.latest",
    "watch_pool.context",
    "conversation.history"
  ],
  "expiresAt": "2026-06-08T10:30:00+08:00"
}
```

`sessionSecret` 生成规则：

```text
SecureRandom 生成 32 字节随机数
  -> Base64URL 编码
  -> sessionSecret
```

Java 侧保存：

```text
agentSessionId -> sessionSecret、userId、conversationId、messageId、scopes、expiresAt、usedNonces
```

保存介质可以先使用本地缓存；如果服务多实例部署，应使用 Redis。

## Java 到 Python：RabbitMQ 启动消息

Java 发布 Agent 启动消息到 RabbitMQ。

示例消息：

```json
{
  "type": "agent.run.start",
  "agentSessionId": "sid-xxx",
  "conversationId": "conv-xxx",
  "messageId": "msg-xxx",
  "userMessage": "帮我看看隆基绿能最近怎么样",
  "sessionSecret": "base64url-secret",
  "scopes": [
    "market.quote",
    "market.kline",
    "knowledge.search",
    "report.latest",
    "conversation.history"
  ],
  "expiresAt": "2026-06-08T10:30:00+08:00",
  "dataGatewayUrl": "http://finance-service:8081/internal/agent/data/query",
  "callbackUrl": "http://finance-service:8081/internal/agent/callback"
}
```

RabbitMQ 要求：

- 仅允许内部网络访问。
- Java 发布端和 Python 消费端使用独立账号。
- 队列设置消息 TTL，避免过期 Agent 消息被延迟消费。
- 配置死信队列，便于排查消费失败。
- 生产日志和消费日志禁止输出完整 `sessionSecret`。
- 条件允许时启用 RabbitMQ TLS。

## Python 到 Java：内部数据网关

Python 需要查询业务数据时，只能调用 Java 暴露的内部数据网关。

接口：

```http
POST /internal/agent/data/query
```

请求头：

```text
X-Agent-Session-Id: sid-xxx
X-Agent-Timestamp: 1718000000000
X-Agent-Nonce: random-nonce
X-Agent-Signature: hmac-signature
```

请求体：

```json
{
  "action": "market.quote",
  "params": {
    "targetType": "stock",
    "targetCode": "601012"
  },
  "limit": 20
}
```

该接口不接受 SQL。所有查询通过 `action + params` 的方式进入白名单分发。

建议支持的 action：

```text
market.quote
market.kline
market.intraday
knowledge.search
report.latest
watch_pool.context
scene_analysis.status
conversation.history
```

Java 数据网关负责：

- 校验请求签名。
- 校验 session 是否存在、是否过期。
- 校验 nonce 是否重复。
- 校验 action 是否在 session scopes 内。
- 校验 params 是否合法。
- 校验 limit 是否超过上限。
- 根据 Java 侧记录的 userId 做用户数据隔离。
- 调用现有 Service、Manage、Mapper 或 InfluxDB 查询服务。

## HMAC 签名逻辑

内部 HTTP 请求使用 HTTPS/TLS 负责传输加密，使用 HMAC-SHA256 负责请求认证和防篡改。

`sessionSecret` 只在 Java 创建 Agent Session 时通过 RabbitMQ 传给 Python。后续 Python 调 Java 时不再传输 `sessionSecret`，只传签名。

### 签名输入

Python 先计算请求体哈希：

```text
bodyHash = SHA256(rawBodyBytes)
```

再拼接标准签名字符串：

```text
canonicalString =
  HTTP_METHOD + "\n" +
  PATH + "\n" +
  TIMESTAMP + "\n" +
  NONCE + "\n" +
  BODY_HASH
```

示例：

```text
POST
/internal/agent/data/query
1718000000000
7f3c0e6a8b
a59f3e...
```

最后计算签名：

```text
signature = Base64URL(HMAC_SHA256(sessionSecret, canonicalString))
```

### Java 校验顺序

Java 收到请求后按顺序校验：

```text
1. X-Agent-Session-Id 是否存在
2. Agent Session 是否存在
3. Agent Session 是否过期
4. timestamp 是否在允许窗口内，建议 60 秒
5. nonce 是否已经使用过
6. 使用 sessionSecret 复算 HMAC-SHA256
7. 使用常量时间比较校验签名
8. action 是否在 scopes 内
9. params 和 limit 是否合法
10. userId 是否只能访问自己的数据
```

任一校验失败，直接返回 401 或 403，不执行查询。

### 防重放

即使攻击者截获了一次完整合法请求，也不能长期重放。

防重放依赖：

- `timestamp`：限制请求时间窗口。
- `nonce`：每个请求唯一，同一个 Agent Session 内用过即作废。

Java 应保存已使用的 nonce，保存时间不短于请求时间窗口。

## Python 到 Java：Agent Callback

Python 生成过程事件或最终答案后，通过 Java callback 接口回传。

接口：

```http
POST /internal/agent/callback
```

该接口使用与数据网关相同的 HMAC 签名规则。

过程事件示例：

```json
{
  "agentSessionId": "sid-xxx",
  "conversationId": "conv-xxx",
  "messageId": "msg-xxx",
  "eventType": "tool_result",
  "payload": {
    "tool": "market.quote",
    "summary": "已查询股票最新行情"
  }
}
```

最终答案示例：

```json
{
  "agentSessionId": "sid-xxx",
  "conversationId": "conv-xxx",
  "messageId": "msg-xxx",
  "eventType": "final_answer",
  "payload": {
    "answer": "基于当前行情和知识库资料，当前只能作为观察，不构成确定性买卖建议。",
    "citations": [],
    "agentSteps": [
      {
        "tool": "market.quote",
        "status": "success"
      }
    ]
  }
}
```

Java 收到 callback 后：

```text
1. 校验 HMAC 签名
2. 校验 Agent Session
3. 校验 conversationId 和 messageId 是否匹配
4. 持久化必要的对话事件
5. 通过 WebSocket 推送给前端
```

## 响应数据格式

数据网关建议统一返回：

```json
{
  "action": "market.quote",
  "success": true,
  "data": [],
  "metadata": {
    "source": "stock_quote_snapshot",
    "queriedAt": "2026-06-08T10:30:00+08:00"
  },
  "error": null
}
```

失败返回：

```json
{
  "action": "market.quote",
  "success": false,
  "data": [],
  "metadata": {},
  "error": {
    "code": "ACTION_FORBIDDEN",
    "message": "当前 Agent Session 不允许调用 market.quote"
  }
}
```

## 安全边界

必须遵守：

- `sessionSecret` 不暴露给前端。
- Python 不传 SQL 给 Java。
- Java 不信任 Python 传来的 userId、权限和 scope。
- Java 只信任自己保存的 Agent Session。
- 所有 action 必须走白名单。
- 所有查询必须限制 limit。
- 所有用户相关数据必须按 Java 侧 userId 过滤。
- `sessionSecret` 不落库、不进日志。
- Agent Session 短期有效，建议 10 到 30 分钟。
- 过期、重复 nonce、签名错误、scope 不匹配时立即拒绝。

## 第一阶段落地建议

第一阶段建议只实现最小闭环：

```text
WebSocket 用户提问
  -> Java 创建 Agent Session
  -> Java 发布 RabbitMQ agent.run.start
  -> Python 消费并调用一次 market.quote 或 knowledge.search
  -> Python callback Java
  -> Java WebSocket 推送最终答案
```

第一阶段 Java 侧新增：

```text
AgentSessionService
AgentMessagePublisher
AgentDataGatewayController
AgentCallbackController
AgentDataGatewayService
AgentSignatureService
```

第一阶段 Python 侧新增：

```text
AgentRunHandler
AgentExecutor
AgentMemory
JavaDataGatewayClient
AgentCallbackClient
AgentSignatureSigner
tools/MarketQuoteTool
tools/KnowledgeSearchTool
prompts/AgentSystemPrompt
```

第一阶段先使用：

```text
market.quote
knowledge.search
```

等签名、回调、WebSocket 推送链路稳定后，再扩展 K 线、分时、报告、观察池和深度研究任务。
