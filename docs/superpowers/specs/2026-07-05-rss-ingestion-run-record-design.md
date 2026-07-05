# RSS 入库运行记录与源级明细设计

## 背景

SignalBrief 已具备 RSS / Atom 抓取、解析、去重入库、定时触发、手动触发、HTTP 抓取重试和失败分类。当前 `FeedIngestionResult` 只返回批次汇总，源级失败原因主要依赖日志。随着真实 RSS 源接入，后续需要可查询的运行记录，用于排查失败源、补偿执行和支撑后续 AI 摘要、邮件发送、归档状态追踪。

## 目标

- 只覆盖 RSS 入库运行记录，不提前抽象通用任务系统。
- 持久化每次 RSS 入库批次的触发方式、状态、时间、耗时和统计。
- 持久化每个 RSS 源的执行结果、失败类型、HTTP 状态、尝试次数和错误摘要。
- 通过内部 API 查询最近运行记录和单次运行详情。
- 保持现有单源失败隔离语义：一个源失败不影响后续源。

## 非目标

- 不做通用 `task_run` 抽象。
- 不做告警、指标上报、Web 管理页或运行记录清理策略。
- 不改变 RSS 源仍由配置维护的现状。
- 不实现多实例锁、运行中任务保护或并发任务调度控制。
- 不记录完整异常堆栈，避免数据库里保存过长或敏感的内部信息。

## 方案

采用方案 A：在现有 `FeedIngestionService` 编排中记录运行状态，并抽出 `IngestionRunRecorder` 隔离持久化细节。

调用链：

```text
FeedIngestionScheduler / ManualTriggerController
-> FeedIngestionService.ingestEnabledFeeds(triggerType)
-> IngestionRunRecorder.startRun(...)
-> 逐个源抓取、解析、入库
-> IngestionRunRecorder.recordSourceSuccess(...) / recordSourceFailure(...)
-> IngestionRunRecorder.finishRun(...)
```

`FeedIngestionService` 继续负责 RSS 入库编排，不直接调用 Mapper。`IngestionRunRecorder` 负责创建批次、写入源级明细、计算并更新最终批次状态。

## 领域模型

新增枚举：

- `IngestionTriggerType`：`MANUAL`、`SCHEDULED`。
- `IngestionRunStatus`：`RUNNING`、`SUCCESS`、`PARTIAL_SUCCESS`、`FAILED`。
- `IngestionSourceRunStatus`：`SUCCESS`、`FAILED`。

`FeedIngestionResult` 增加 `runId` 字段。手动触发接口返回该字段，便于触发后查询详情。

## 数据库

新增 Flyway 迁移，创建两张表。

`rss_ingestion_run` 保存批次汇总：

- `id`
- `trigger_type`
- `status`
- `started_at`
- `finished_at`
- `duration_millis`
- `source_count`
- `fetched_count`
- `inserted_count`
- `skipped_count`
- `failed_source_count`
- `created_at`
- `updated_at`

`rss_ingestion_source_run` 保存源级明细：

- `id`
- `run_id`
- `source_name`
- `source_url`
- `category`
- `status`
- `failure_type`
- `http_status`
- `attempt_count`
- `max_attempts`
- `error_message`
- `fetched_count`
- `inserted_count`
- `skipped_count`
- `started_at`
- `finished_at`
- `duration_millis`
- `created_at`

`rss_ingestion_source_run.run_id` 外键指向 `rss_ingestion_run.id`。查询最近运行记录按 `started_at DESC, id DESC` 排序。错误摘要建议限制长度，避免异常信息无限增长。

## 状态规则

批次创建后为 `RUNNING`。

完成时按源级结果计算最终状态：

- 没有失败源且批次正常结束：`SUCCESS`。
- 至少一个源失败，但批次正常处理完所有源：`PARTIAL_SUCCESS`。
- 所有源失败，或批次级异常导致无法继续：`FAILED`。

单个源执行成功时记录抓取数、入库数和跳过数。单个源失败时记录失败类型、HTTP 状态、尝试次数、错误摘要，统计值记为 0。

## 内部 API

在现有 internal API 下新增查询入口：

```text
GET /internal/ingestions/rss/runs
GET /internal/ingestions/rss/runs/{id}
```

列表接口返回最近运行记录，支持 `limit` 参数，默认 20，最大 100。详情接口返回批次信息和源级明细。

`POST /internal/ingestions/rss` 继续触发一次 RSS 入库，但响应增加 `runId`。

OpenAPI 仍只暴露在 internal 分组下，受 `SIGNAL_BRIEF_OPENAPI_ENABLED` 控制。

## 错误处理

源级失败继续由 `FeedIngestionService` 捕获并隔离。`FeedFetchException` 中已有的 `failureType`、`httpStatus`、`attemptCount` 和 `maxAttempts` 写入源级记录。非 HTTP 抓取异常统一记录为未预期失败，错误摘要保留简短中文或异常消息。

如果批次级记录创建失败，应让本次入库失败并暴露异常，避免出现“实际抓取执行了但没有运行记录”的不可追踪状态。如果批次已创建但后续发生批次级异常，应尽量把批次更新为 `FAILED`。

## 测试策略

- Mapper IT：覆盖批次写入、源级明细写入、列表排序、详情关联查询。
- `IngestionRunRecorderTest`：覆盖状态汇总、耗时计算和错误摘要截断。
- `FeedIngestionServiceTest`：覆盖全成功、部分失败、全部失败时的统计和记录行为。
- `FeedIngestionSchedulerTest`：验证调度入口传入 `SCHEDULED`。
- `ManualTriggerControllerTest`：验证手动触发返回 `runId`，运行记录列表和详情接口可查询。
- `OpenApiConfigurationTest`：必要时确认新增 internal 接口仍在 internal 分组中。

本地基础验证：

```bash
./mvnw -Dspring.docker.compose.enabled=false test
```

数据库集成测试继续由 CI 的 `./mvnw -B verify` 执行。本地只在排查迁移、Mapper 或 SQL 行为时运行完整 verify。

## 文档同步

实现后同步更新：

- `README.md`：补充运行记录查询接口说明。
- `docs/records/rss-ingestion.md`：补充运行记录已落地的长期设计。
- `docs/records/rss-source-reliability.md`：把“任务运行记录”从后续方向移到当前能力或关联记录。
- `docs/personal-tech-newsletter-system.md`：更新当前阶段和后续路线。

## 后续方向

- 增加连续失败源告警策略。
- 增加运行记录清理策略或保留周期。
- AI 摘要、邮件发送和归档接入后，再评估是否抽象通用任务运行记录。
- 多实例部署前评估运行中任务保护、幂等锁或分布式锁。
