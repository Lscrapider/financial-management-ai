# 场景分析当前上下文交接

本文档用于下一轮上下文继续开发。项目文档使用中文；继续修改代码前先阅读 `docs/CODEX_GUIDELINES.md`。

## 当前目标

实现“标的当前场景标签 -> 知识库 RAG 召回 -> 报告生成”的后端链路。

当前已经完成到：

```text
Java 提交报告任务
  -> Java 查询标的所需行情/估值/财务/分红/行业等数据
  -> Java 发新的 scene-analysis RMQ 消息
  -> Python 消费消息并计算 currentScenes
  -> Python 回调 Java
  -> Java 保存 currentScenesPayload
  -> Java 进入第 6.2 节 chunk 分配计算
```

## 关键设计结论

1. Python 不直接查数据库，不直接调用第三方接口处理标的数据。
2. Java 负责所有数据查询、入库、任务状态和后续 RAG 编排。
3. Python 只负责 currentScenes 的基础变量和 7 大类场景标签计算。
4. `currentScenesPayload` 是第六节检索召回输入，应存入 `scene_analysis_task.current_scenes_payload`。
5. `currentScenesPayload` 报文结构必须对齐 `docs/REPORT_PIPELINE.md` 第 6.1 节：

```json
{
  "target": {
    "type": "STOCK",
    "code": "002958",
    "name": "青农商行"
  },
  "reportType": "quick_analysis",
  "totalChunks": 10,
  "currentScenes": {}
}
```

6. `currentScenesPayload` 在 Java callback 入参中不能用 `JsonNode` 接收，必须用强类型对象。
7. 数据库 jsonb 存储时才允许通过 `ObjectMapper.valueToTree(...)` 转成 `JsonNode`。
8. 第六节 RAG 召回放 Java 侧实现，因为知识库、metadata、pgvector 都在数据库中。

## 已完成代码

### Java 后端

已新增/修改：

- `SceneAnalysisSubmitParam`
  - 新增 `totalChunks`
  - Java submit 校验 `totalChunks > 0`
- `SceneAnalysisMessageDTO`
  - 新增 `totalChunks`
  - Java 发 RMQ 时透传给 Python
- `SceneAnalysisCallbackParam`
  - 精简为只接收 `currentScenesPayload`
  - `currentScenesPayload` 已改为强类型对象
- 新增 currentScenes 强类型 Param：
  - `SceneAnalysisCurrentScenesPayloadParam`
  - `SceneAnalysisCurrentScenesTargetParam`
  - `SceneAnalysisCurrentScenesParam`
  - `SceneAnalysisSceneModuleParam`
- 新增状态枚举：
  - `SceneAnalysisTaskStatusEnum`
  - 状态包括：
    - `pending`
    - `processing_current_scenes`
    - `current_scenes_ready`
    - `retrieving_knowledge`
    - `generating_report`
    - `success`
    - `failed`
- `SceneAnalysisTaskManage`
  - 新增 `markCurrentScenesReady(...)`
  - 状态写入改用枚举
- `SceneAnalysisTaskMapper`
  - 新增 `markCurrentScenesReady(...)`
- 新增 6.2 分配服务：
  - `SceneReportPipelineService`
  - `SceneReportPipelineServiceImpl`
  - `SceneChunkAllocationDTO`

### Python

已新增/修改：

- currentScenes 7 大类处理器已经完成：
  - `asset`
  - `price`
  - `volume`
  - `trend`
  - `valuation`
  - `sentiment`
  - `risk_strategy`
- `currentScenesPayload` 输出结构已改为：
  - `target`
  - `reportType`
  - `totalChunks`
  - `currentScenes`
- Python callback 现在只传：

```json
{
  "currentScenesPayload": {}
}
```

- `currentScenesPayload` 不再包含：
  - `taskNo`
  - `baseMetrics`

## currentScenes 细节

`currentScenes` 每个模块结构：

```json
{
  "score": 0.82,
  "level": "high",
  "direction": "positive",
  "tags": {
    "volume_expand": 0.9
  },
  "evidence": [
    "当前成交量相对 60 日稳健中位水平明显放大，volume_expand 标签触发"
  ]
}
```

约束：

- `evidence` 是字符串数组，不是对象数组。
- 不在 evidence 中输出 `metrics`、`reason` 等嵌套字段。
- evidence 文案要客观、细粒度、便于 RAG 召回和 LLM 分析。
- 组合标签只描述本次实际触发的子信号。

## 已完成文档对齐

已对齐：

- `docs/标的标签计算规则2.md`
- `docs/REPORT_PIPELINE.md`

主要对齐内容：

- `evidence` 为 `string[]`
- currentScenes 示例包含 `score/level/direction/tags/evidence`
- 第六节输入包含 `target/reportType/totalChunks/currentScenes`

## 6.2 节当前实现

Java 侧 `SceneReportPipelineServiceImpl` 已实现第 6.2 节 chunk 分配。

输入：

```java
SceneAnalysisCurrentScenesPayloadParam currentScenesPayload
```

输出：

```java
List<SceneChunkAllocationDTO>
```

每个元素：

- `category`：场景大类，如 `volume`
- `chunkCount`：该类应召回的 chunk 数
- `score`：原始模块分数，来自 `currentScenes[category].score`
- `effectiveScore`：乘以 `reportType` 权重后的分数

当前配置：

- `alpha = 6.0`
- `categoryScoreThreshold = 0.35`
- `minPerActiveCategory = 1`
- `maxPerCategory = 4`
- 不给 `asset` 分配 chunk

当前 6.2 结果只是日志输出，还没有入库，也没有进入 6.3。

## 下一步建议

下一步从 `docs/REPORT_PIPELINE.md` 第 6.3 节开始做。

推荐顺序：

1. 定义 6.3 检索任务 DTO
   - `category`
   - `chunkCount`
   - `currentTags`
   - `queryText`
2. 在 `SceneReportPipelineServiceImpl` 中把 6.2 的 `SceneChunkAllocationDTO` 转成检索任务。
3. 实现 6.4 标签过滤：
   - 读取 `knowledge_vector.metadata -> scenes`
   - 按 category 取 chunkTags
   - 计算 Jaccard similarity
   - 过滤 `jaccard_score >= jaccardThreshold`
4. 实现 6.5 queryText 生成。
5. 实现 6.6 pgvector 查询：
   - Java 调 embedding 接口拿 queryEmbedding
   - Mapper SQL 使用 pgvector 算 semantic score
6. 实现 6.7 final score：
   - `semanticScore`
   - `tagMatchScore`
   - `crossSceneScore`
   - `finalScore`
7. 实现 6.8 每类 TopN。
8. 实现 6.9 `knowledgeContext`。
9. 后续再进入报告 LLM 生成和 `reportPayload/reportText` 存库。

## 注意事项

- `SceneAnalysisTaskPO.currentScenesPayload` 数据库字段仍然是 `JsonNode`，这是持久化边界，可以保留。
- Controller/Param 不能用 `JsonNode` 或 `Map` 接收业务入参。
- Java 后续做 pgvector，不要让 Python 查询知识库。
- 如果前端测试 submit 接口，需要新增：

```json
"totalChunks": 10
```

- 旧的 RMQ 测试报文没有 `totalChunks`，需要从 Java 接口重新生成。

## 最近验证过的命令

```bash
python3 -m compileall ai-python/app/scene_analysis ai-python/test/test_scenes_lable.py
python3 ai-python/test/test_scenes_lable.py
mvn -pl finance-service -am test
```

以上命令在最近一次修改后通过。
