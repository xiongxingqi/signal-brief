# RSS 入库任务化与查询出口记录

> 本文档记录 2026-07-04 对 RSS 入库链路的任务化改造。长期总览见 [RSS 抓取入库记录](rss-ingestion.md)。

## 背景

RSS 抓取入库第一版已经能读取配置源、抓取 feed、解析 RSS / Atom、去重并写入 `article` 表，但调用入口仍停留在服务方法层面。后续半月报需要一个稳定的文章查询出口，也需要把入库链路接到可配置调度上。

本次改造把 RSS 入库推进到“可按配置定时执行”，并为 AI 摘要、Markdown 简报和邮件发送预留统一文章入口。

## 本次范围

已完成：

- 新增 RSS 入库任务配置：`signal-brief.ingestion.enabled` 与 `signal-brief.ingestion.cron`。
- 新增定时调度器 `FeedIngestionScheduler`。
- 新增简报候选文章查询出口 `ArticleQueryService`。
- 新增 MyBatis 查询边界 `ArticleQueryMapper`。
- 补充配置、调度、查询和数据库行为测试。

未包含：

- 手动 HTTP / CLI 触发入口。
- RSS 源数据库管理。
- 任务运行表、失败重试、分布式锁或 Spring Batch。
- AI 摘要、Markdown 简报和邮件推送。

## 关键决策

- 定时任务默认关闭，只有 `signal-brief.ingestion.enabled=true` 时才注册 `FeedIngestionScheduler`。
- 默认 cron 为 `0 0 6 1,16 * *`，匹配半月报节奏。
- 调度器只负责触发和记录结果，不改变 `FeedIngestionService` 的单源失败隔离语义。
- 查询出口独立为 `ArticleQueryService` / `ArticleQueryMapper`，避免把读模型混入写入和去重用的 `ArticleMapper`。
- 查询窗口使用 `[startInclusive, endExclusive)`，避免连续批次边界重复。
- 有效时间使用 `COALESCE(published_at, created_at)`，兼容没有发布时间的 feed 条目。
- 查询排序按分类、有效时间倒序、`id` 倒序，保证简报生成顺序稳定。

## 落地文件

- `src/main/resources/application.yaml`：新增 `signal-brief.ingestion` 默认配置。
- `src/main/java/cn/name/celestrong/signalbrief/config/IngestionProperties.java`：绑定入库任务配置。
- `src/main/java/cn/name/celestrong/signalbrief/ingestion/FeedIngestionScheduler.java`：定时触发入库。
- `src/main/java/cn/name/celestrong/signalbrief/article/ArticleQueryService.java`：校验时间窗口并委托查询。
- `src/main/java/cn/name/celestrong/signalbrief/article/ArticleQueryMapper.java`：查询简报候选文章。
- `src/test/java/cn/name/celestrong/signalbrief/config/IngestionPropertiesTest.java`：覆盖默认值和显式绑定。
- `src/test/java/cn/name/celestrong/signalbrief/ingestion/FeedIngestionSchedulerTest.java`：覆盖开关和触发行为。
- `src/test/java/cn/name/celestrong/signalbrief/article/ArticleQueryServiceTest.java`：覆盖参数校验。
- `src/test/java/cn/name/celestrong/signalbrief/article/ArticleQueryMapperIT.java`：覆盖数据库查询窗口、回退时间和排序。

## 当前配置

```yaml
signal-brief:
  ingestion:
    enabled: ${SIGNAL_BRIEF_INGESTION_ENABLED:false}
    cron: "${SIGNAL_BRIEF_INGESTION_CRON:0 0 6 1,16 * *}"
```

开启示例：

```bash
SIGNAL_BRIEF_INGESTION_ENABLED=true ./mvnw spring-boot:run
```

本地测试和 CI 都应显式设置 `SPRING_DOCKER_COMPOSE_ENABLED=false`，避免 Spring Boot 测试进程接管本地 `compose.yaml`。

## 验证方式

本地基础验证：

```bash
./mvnw -o -Dspring.docker.compose.enabled=false test
```

CI 完整验证：

```bash
./mvnw -B verify
```

`verify` 会执行 Failsafe 管理的 `*IT` 集成测试，当前由 GitHub Actions 的 PostgreSQL service 承担数据库环境。

## 后续事项

- 增加手动触发入口，用于本地调试、补偿执行和运维操作。
- 增加任务运行记录，保存开始时间、结束时间、统计结果和失败原因。
- 接入真实 RSS 源后补充超时、重试、失败告警和基础指标。
- 基于 `ArticleQueryService` 实现 AI 摘要、Markdown 简报和邮件推送。
- 如后续多实例部署，再评估分布式锁或更完整的批处理框架。
