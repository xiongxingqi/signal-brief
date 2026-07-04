# RSS 入库任务化与文章查询出口设计

## Summary

把现有 RSS 入库能力从“只能服务方法调用”推进到“可配置定时执行”，并补充后续 AI 简报需要的文章查询出口。此次不做手动 HTTP 或 CLI 触发，不接 AI，不发邮件，不新增数据库表。

采用方案：定时采集加查询出口。采集任务默认关闭，避免本地启动、测试或 CI 意外访问外部 RSS；简报候选文章时间过滤使用 `published_at` 优先，缺失时回退 `created_at`。

## Key Changes

新增采集配置 `signal-brief.ingestion`：

- `enabled`：默认 `false`，环境变量 `SIGNAL_BRIEF_INGESTION_ENABLED`。
- `cron`：默认 `"0 0 6 1,16 * *"`，即每月 1 日和 16 日 06:00，环境变量 `SIGNAL_BRIEF_INGESTION_CRON`。
- 同步更新 `.env.example`、`README.md` 和 `docs/rss-ingestion.md`。

新增 `FeedIngestionScheduler`：

- 使用 `@Scheduled(cron = "${signal-brief.ingestion.cron}")` 调用 `FeedIngestionService.ingestEnabledFeeds()`。
- 使用 `@ConditionalOnProperty(prefix = "signal-brief.ingestion", name = "enabled", havingValue = "true")`，默认不注册任务 bean。
- 不在调度层吞掉业务结果；异常仍记录日志，单源失败继续由现有 `FeedIngestionService` 负责。

新增文章查询出口：

- 新增 `ArticleQueryService.findBriefCandidates(Instant startInclusive, Instant endExclusive)`。
- 新增独立 MyBatis 查询 mapper，避免污染现有 `ArticleMapper` 写入和去重接口，也避免改动已有 fake mapper 测试。
- 查询条件使用半开区间 `[startInclusive, endExclusive)`。
- SQL 时间口径：`COALESCE(published_at, created_at)`。
- 排序：按分类、有效时间倒序、`id` 倒序，保证输出稳定。
- 查询列使用 camelCase alias 映射到 `Article` record，不依赖全局 MyBatis 下划线映射配置。

## Test Plan

- 本地基础测试仍以 `./mvnw -o -Dspring.docker.compose.enabled=false test` 为默认验证。
- 新增配置测试：默认 `enabled=false`，默认 cron 为 `"0 0 6 1,16 * *"`，显式配置能正确绑定。
- 新增调度测试：`enabled=false` 时 Spring context 不注册 `FeedIngestionScheduler`；`enabled=true` 时注册 scheduler；直接调用调度方法会触发一次 `FeedIngestionService.ingestEnabledFeeds()`。
- 新增文章查询测试：`startInclusive`、`endExclusive` 不能为空，`startInclusive` 必须早于 `endExclusive`。
- 新增集成测试覆盖 `published_at` 命中、`published_at IS NULL` 时回退 `created_at`、窗口外文章不返回、排序稳定。
- 不新增真实外部 RSS 网络测试；真实抓取仍由现有 `HttpFeedClientTest` 使用 mock server 覆盖。

## Assumptions

- MVP 当前是单实例运行，不引入分布式锁、Spring Batch 或任务运行表。
- 本次不把 RSS 源入库，继续沿用 `signal-brief.feeds` 配置。
- 不在 `application-dev.yaml` 默认启用真实 RSS 源，避免本地启动自动访问外网。
- CI 仍负责数据库集成测试，本地默认只跑基础测试。
