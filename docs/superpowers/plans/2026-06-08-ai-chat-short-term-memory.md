# AI Chat Short-Term Memory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 AI Chat 增加基于 `userId + conversationId` 的短期记忆、WebSocket 关闭后的延迟清理、短期消息摘要入长期记忆，以及 Python 固定召回短期记忆。

**Architecture:** Java 负责 conversation/message/memory 持久化、WebSocket 绑定、cleanupVersion 防误删和内部数据网关；Python 负责在 Agent Run 开始时读取 `conversation.history` 并放入 LLM 上下文。WebSocket 关闭后只在 activeCount 降为 0 时发送 30 分钟延迟 cleanup，消费时版本一致才先保存 `conversation_summary` 长期记忆，再删除短期消息。

**Tech Stack:** Spring Boot、MyBatis Plus、PostgreSQL、RabbitMQ、WebSocket、Python、LangChain。

---

### Task 1: 数据模型与迁移

**Files:**
- Create: `database/migrations/05_init_ai_chat_agent.sql`
- Create: `backend-java/finance-data/src/main/java/com/scrapider/finance/domain/po/AiChatConversationPO.java`
- Create: `backend-java/finance-data/src/main/java/com/scrapider/finance/domain/po/AiChatMessagePO.java`
- Create: `backend-java/finance-data/src/main/java/com/scrapider/finance/domain/po/AiUserMemoryPO.java`
- Create: `backend-java/finance-data/src/main/java/com/scrapider/finance/mapper/AiChatConversationMapper.java`
- Create: `backend-java/finance-data/src/main/java/com/scrapider/finance/mapper/AiChatMessageMapper.java`
- Create: `backend-java/finance-data/src/main/java/com/scrapider/finance/mapper/AiUserMemoryMapper.java`
- Create: `backend-java/finance-data/src/main/java/com/scrapider/finance/manage/AiChatConversationManage.java`
- Create: `backend-java/finance-data/src/main/java/com/scrapider/finance/manage/AiChatMessageManage.java`
- Create: `backend-java/finance-data/src/main/java/com/scrapider/finance/manage/AiUserMemoryManage.java`

- [ ] 创建 `ai_chat_conversation`、`ai_chat_message` 和 `ai_user_memory` 表。
- [ ] 增加 MyBatis Plus PO、Mapper、Manage。
- [ ] `cleanup_version` 默认 0，每次 WebSocket 绑定递增。

### Task 2: Java conversation 服务

**Files:**
- Create: `backend-java/finance-service/src/main/java/com/scrapider/finance/service/AiChatConversationService.java`
- Create: `backend-java/finance-service/src/main/java/com/scrapider/finance/service/impl/AiChatConversationServiceImpl.java`
- Create: `backend-java/finance-service/src/main/java/com/scrapider/finance/domain/dto/AiChatConversationBindingDTO.java`

- [ ] 握手绑定 conversation，校验 `userId + conversationId`。
- [ ] 内存维护 `activeCount`。
- [ ] WebSocket 关闭时 activeCount 为 0 才发送 cleanup 消息。
- [ ] 保存 user/assistant 消息。
- [ ] 查询最近 N 条短期记忆。

### Task 3: RabbitMQ cleanup

**Files:**
- Modify: `backend-java/finance-service/src/main/java/com/scrapider/finance/config/AgentRabbitConfig.java`
- Modify: `backend-java/finance-service/src/main/java/com/scrapider/finance/service/AgentMessagePublisher.java`
- Modify: `backend-java/finance-service/src/main/java/com/scrapider/finance/service/impl/RabbitAgentMessagePublisherImpl.java`
- Create: `backend-java/finance-service/src/main/java/com/scrapider/finance/domain/dto/ConversationCleanupMessageDTO.java`
- Create: `backend-java/finance-service/src/main/java/com/scrapider/finance/messaging/ConversationCleanupListener.java`

- [ ] 配置 cleanup 队列，使用 TTL + DLX 实现 30 分钟延迟。
- [ ] WS close 发布 cleanup 消息，携带 `cleanupVersion`。
- [ ] cleanup 消费时版本一致才保存 `ai_user_memory` 摘要、删除 `ai_chat_message` 并更新 conversation 状态。

### Task 4: WebSocket 接入短期记忆

**Files:**
- Modify: `backend-java/finance-service/src/main/java/com/scrapider/finance/websocket/AiChatWebSocketHandshakeInterceptor.java`
- Modify: `backend-java/finance-service/src/main/java/com/scrapider/finance/websocket/AiChatWebSocketHandler.java`
- Modify: `backend-java/finance-service/src/main/java/com/scrapider/finance/websocket/AiChatWebSocketSessionRegistry.java`
- Modify: `frontend-vue/apps/web-ele/src/api/ai-chat/index.ts`

- [ ] 前端建立 WS 时带 `conversationId`。
- [ ] Java 握手解析并绑定 conversation。
- [ ] 收消息时保存 user message。
- [ ] callback final_answer 时保存 assistant message。

### Task 5: Python 召回短期记忆

**Files:**
- Modify: `backend-java/finance-service/src/main/java/com/scrapider/finance/service/impl/AgentDataGatewayServiceImpl.java`
- Modify: `ai-python/app/agent/services/agent_executor.py`
- Create: `ai-python/app/agent/tools/conversation_history_tool.py`

- [ ] Java 数据网关支持 `conversation.history`。
- [ ] Python Agent Run 开始时固定读取最近 10 条消息。
- [ ] LLM system/context 中包含短期记忆。

### Task 6: 验证

**Commands:**
- `python -m compileall app`
- `mvn -pl backend-java/finance-service -am -DskipTests compile`
- `pnpm -F @vben/web-ele run typecheck`
- `git diff --check`

- [ ] Python 编译通过。
- [ ] Java 编译通过。
- [ ] 前端类型检查通过。
- [ ] 无 diff 空白错误。
