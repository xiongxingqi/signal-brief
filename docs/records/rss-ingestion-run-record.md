# RSS 入库运行记录

> 本文档记录 RSS 入库运行记录和源级明细的当前设计。RSS 抓取、解析和去重总览见 [RSS 抓取入库记录](rss-ingestion.md)，内部查询入口见 [内部手动触发 API 记录](manual-trigger-api.md)。实现细节以源码、迁移脚本和测试为准。

## 定位

RSS 入库运行记录用于把每次手动或定时入库的执行结果落库，方便排查失败源、补偿执行和后续扩展告警、摘要、邮件发送与归档状态追踪。

当前只覆盖 RSS 入库链路，不抽象通用任务系统。告警、指标上报、运行记录清理策略、多实例运行锁和 Web 管理页仍属于后续设计。

## 模块边界

- `FeedIngestionService`：继续负责编排多源抓取、解析、去重和入库，保持单源失败隔离。
- `IngestionRunRecorder`：只负责创建运行记录、写入源级明细、更新最终状态，避免编排层感知表结构。
- `RssIngestionRunMapper`：负责运行记录和源级明细的 MyBatis 持久化查询。
- `RssIngestionRunQueryService`：提供最近运行记录和单次运行详情查询。
- `ManualTriggerController`：提供内部 API 查询入口，不承载运行状态计算逻辑。

## 数据模型

`rss_ingestion_run` 保存批次级汇总：触发方式、运行状态、开始结束时间、耗时、源数量、抓取数量、入库数量、跳过数量和失败源数量。

`rss_ingestion_source_run` 保存源级明细：源名称、源 URL、分类、状态、失败类型、HTTP 状态、尝试次数、最大尝试次数、错误摘要、抓取/入库/跳过统计和执行耗时。

状态枚举保持在 RSS 入库语义内：

- `IngestionTriggerType`：`MANUAL`、`SCHEDULED`。
- `IngestionRunStatus`：`RUNNING`、`SUCCESS`、`PARTIAL_SUCCESS`、`FAILED`。
- `IngestionSourceRunStatus`：`SUCCESS`、`FAILED`。

## 状态规则

批次创建后先写入 `RUNNING`。正常处理完所有源后，根据源级结果计算最终状态：

- 没有失败源：`SUCCESS`。
- 至少一个源失败，但批次完成：`PARTIAL_SUCCESS`。
- 所有源失败，或批次级异常导致无法继续：`FAILED`。

单个源失败不影响后续源继续处理。失败明细优先记录 `FeedFetchException` 中的失败类型、HTTP 状态、尝试次数和最大尝试次数；非 HTTP 抓取异常记录为未预期失败，并保留简短错误摘要。

## API 与查询

`POST /internal/ingestions/rss` 的响应包含 `runId`，便于触发后继续查询详情。

`GET /internal/ingestions/rss/runs` 查询最近运行记录，默认返回 20 条，最大 100 条，排序为 `started_at DESC, id DESC`。

`GET /internal/ingestions/rss/runs/{id}` 查询单次运行和源级明细。记录不存在时返回 `404 Not Found`。

## 可靠性约束

批次级记录创建失败时，应让本次入库失败并暴露异常，避免出现“实际抓取已执行但没有运行记录”的不可追踪状态。批次已经创建后，如果后续发生批次级异常，应尽量把批次更新为 `FAILED`。

错误摘要不保存完整堆栈，避免数据库中保存过长或敏感的内部信息。完整排查仍依赖应用日志。

## 测试与验证

当前测试覆盖：

- `RssIngestionRunMapperIT`：批次写入、源级明细写入、列表排序和详情关联查询。
- `IngestionRunRecorderTest`：状态汇总、耗时计算和错误摘要截断。
- `FeedIngestionServiceTest`：全成功、部分失败、全部失败时的统计和记录行为。
- `FeedIngestionSchedulerTest`：定时入口传入 `SCHEDULED`。
- `ManualTriggerControllerTest`：手动触发返回 `runId`，运行记录列表和详情接口可查询。

本地基础验证：

```bash
./mvnw -Dspring.docker.compose.enabled=false test
```

数据库集成测试继续由 CI 的 `./mvnw -B verify` 执行；本地只在排查迁移、Mapper 或 SQL 行为时运行完整验证。

## 后续方向

- 增加连续失败源告警策略。
- 增加运行记录保留周期和清理策略。
- AI 摘要、邮件发送和归档接入后，再评估是否抽象端到端任务运行记录。
- 多实例部署前评估运行中任务保护、幂等锁或分布式锁。
