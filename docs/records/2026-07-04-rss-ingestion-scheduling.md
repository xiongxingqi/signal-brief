# RSS 入库任务化与文章查询出口记录

> 本文档由 RSS 入库任务化与文章查询出口的设计实施过程提炼而来，用作项目长期维护记录。具体实现以源码、测试和配置为准。

## 背景

RSS 抓取入库第一版已经具备“读取配置源、抓取 feed、解析 RSS / Atom、去重并写入 `article` 表”的能力，但调用入口仍停留在服务方法层面，后续 AI 简报也缺少稳定的文章查询出口。

本次整理的目标是把 RSS 入库链路推进到可按配置定时触发，并为后续 Markdown 简报、AI 摘要和邮件推送提供文章候选查询能力。

## 范围

本次只覆盖：

- RSS 入库定时触发配置。
- 定时任务调度器。
- 简报候选文章查询出口。
- 对应单元测试、集成测试和文档说明。

本次不覆盖：

- 手动 HTTP 或 CLI 触发入口。
- AI 摘要生成。
- Markdown 简报生成。
- 邮件发送。
- RSS 源入库管理。
- 新增任务运行表或分布式调度能力。

## 关键决策

- 定时任务默认关闭，通过 `signal-brief.ingestion.enabled=true` 开启，避免本地启动、测试或 CI 意外访问外部 RSS 源。
- 默认 cron 为 `0 0 6 1,16 * *`，表示每月 1 日和 16 日 06:00 执行。
- `FeedIngestionScheduler` 只做触发和结果日志，不改变 `FeedIngestionService` 的单源失败隔离语义。
- 文章查询使用独立的 `ArticleQueryMapper` 和 `ArticleQueryService`，不扩展现有写入/去重用的 `ArticleMapper`。
- 简报候选查询使用半开区间 `[startInclusive, endExclusive)`。
- 文章有效时间优先使用 `published_at`，缺失时回退 `created_at`，SQL 口径为 `COALESCE(published_at, created_at)`。
- 查询结果按分类、有效时间倒序、`id` 倒序排列，保证后续生成简报时输出稳定。

## 当前实现

新增配置项：

```yaml
signal-brief:
  ingestion:
    enabled: ${SIGNAL_BRIEF_INGESTION_ENABLED:false}
    cron: "${SIGNAL_BRIEF_INGESTION_CRON:0 0 6 1,16 * *}"
```

主要组件：

- `config/IngestionProperties`：绑定 RSS 入库任务开关和 cron。
- `ingestion/FeedIngestionScheduler`：在启用时按 cron 调用 `FeedIngestionService.ingestEnabledFeeds()`。
- `article/ArticleQueryService`：对外提供 `findBriefCandidates(Instant startInclusive, Instant endExclusive)`。
- `article/ArticleQueryMapper`：使用 MyBatis 查询 `article` 表并映射为 `Article` record。

## 验证

本地基础测试使用：

```bash
./mvnw -o -Dspring.docker.compose.enabled=false test
```

本次新增和覆盖的重点测试：

- `IngestionPropertiesTest`：配置默认值和显式绑定。
- `FeedIngestionSchedulerTest`：默认不注册、开启后注册、直接调用可触发一次入库。
- `ArticleQueryServiceTest`：查询窗口参数校验和 mapper 委托。
- `ArticleQueryMapperIT`：数据库查询窗口、`created_at` 回退和排序行为。

数据库集成测试仍由 CI 的 PostgreSQL service 执行，本地日常开发默认不跑 `verify`。

## 后续事项

- 按需要增加手动触发入口，方便本地或运维侧主动拉取 RSS。
- 接入真实 RSS 源后补充超时、重试、限流和失败告警策略。
- 基于 `ArticleQueryService` 实现 AI 摘要、Markdown 简报和邮件推送。
- 如后续支持多实例部署，再评估分布式锁、任务运行表或 Spring Batch。
