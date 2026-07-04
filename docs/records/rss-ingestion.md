# RSS 抓取入库备忘

> 本文档由 RSS 抓取入库的设计与实施计划提炼而来，用作项目长期维护备忘。具体实现以源码、测试和迁移脚本为准。

## 目标

RSS MVP 的目标是跑通资讯采集第一段链路：读取启用的 RSS / Atom 源，抓取 feed 内容，解析文章条目，按稳定规则去重，并把新文章写入 PostgreSQL。

当前范围只覆盖“抓取、解析、去重、入库、统计、定时触发和文章查询出口”。AI 摘要、邮件发送、Web UI、RSS 源数据库管理暂不在本阶段内。

## 当前实现快照

- RSS 源通过 `signal-brief.feeds` 配置维护，第一版不入库，修改后需要重启应用。
- `HttpFeedClient` 使用 Spring `RestClient` 抓取内容，底层 HTTP 实现通过引入 Apache HttpClient 5 依赖交给 Spring 选择。
- `FeedClient.fetch` 对外仍返回 `InputStream`，避免上层感知 HTTP 客户端细节。
- `RomeFeedParser` 使用 ROME 解析 RSS / Atom，并输出统一的 `FetchedArticle`。
- `ArticleDeduplicationService` 负责按 guid、url、contentHash 去重。
- `ArticleMapper` 使用 MyBatis 写入 `article` 表，数据库层通过唯一索引兜底去重。
- `FeedIngestionService` 编排所有启用源，返回来源数、抓取数、插入数、跳过数、失败源数。
- `FeedIngestionScheduler` 在 `signal-brief.ingestion.enabled=true` 时按 cron 触发入库，默认关闭。
- `ArticleQueryService` 提供简报候选文章查询，按 `published_at` 优先、缺失时回退 `created_at`。

## 核心流程

1. `FeedIngestionService.ingestEnabledFeeds()` 从 `FeedProperties.enabledFeeds()` 获取启用源。
2. 每个源调用 `FeedClient.fetch(source)` 获取 feed 输入流。
3. `FeedParser.parse(source, inputStream)` 把 RSS / Atom 条目转换为 `FetchedArticle`。
4. `ArticleIngestionService.ingest(article)` 判断是否重复，未重复则构造 `NewArticle`。
5. `ArticleMapper.insertIfAbsent(newArticle)` 写入数据库，冲突时跳过。
6. 单个源失败不会中断整个批次，失败会计入 `failedSourceCount`。
7. 开启定时任务后，`FeedIngestionScheduler` 按配置 cron 触发同一条入库链路。

## 模块职责

- `config/FeedProperties`：绑定 `signal-brief.feeds`，并过滤启用源。
- `article/ArticleCategory`：固定文章分类，目前包括 `JAVA`、`FRAMEWORK`、`INDUSTRY`、`CAREER`、`AI`。
- `feed/FeedClient`：定义 feed 获取边界。
- `feed/HttpFeedClient`：HTTP 获取实现，非 2xx 响应包装为 `FeedFetchException`。
- `feed/FeedParser`：定义解析边界。
- `feed/RomeFeedParser`：解析 RSS / Atom，处理标题、链接、guid、发布时间和摘要。
- `article/ArticleDeduplicationService`：确定重复判断顺序并生成 SHA-256 内容哈希。
- `article/ArticleMapper`：MyBatis 持久化边界。
- `article/ArticleQueryService`：按时间窗口查询后续简报候选文章。
- `ingestion/FeedIngestionService`：批量抓取入库编排。
- `ingestion/FeedIngestionScheduler`：可配置定时触发 RSS 入库。

## 配置约定

RSS 源配置项为 `signal-brief.feeds`，每个源包含 `name`、`url`、`category`、`enabled`。默认配置为空列表，生产或本地调试时按需要添加源。

RSS 入库任务配置项为 `signal-brief.ingestion`，包含 `enabled` 和 `cron`。默认 `enabled=false`，避免本地启动或测试时自动访问外部源；默认 cron 为 `0 0 6 1,16 * *`。

配置示例见 `README.md` 的“RSS 源配置”小节。分类值必须来自 `ArticleCategory`，源名称、URL、分类不能为空。

## 数据表与去重

文章表由 `src/main/resources/db/migration/V1__create_article_table.sql` 创建，核心字段包括来源名称、来源 URL、分类、标题、文章 URL、guid、发布时间、摘要、内容哈希和创建更新时间。

去重顺序：

1. 有 guid 时，按 `source_name + guid` 判断。
2. 没有 guid 但有 url 时，按 url 判断。
3. guid 和 url 都不可用时，按 contentHash 判断。

数据库兜底约束：

- `uk_article_source_guid`：同一来源下 guid 唯一。
- `uk_article_url`：非空 url 唯一。
- `uk_article_content_hash`：内容哈希唯一。
- `idx_article_published_at`：按发布时间查询预留索引。

## 错误处理

- HTTP 非 2xx 响应会抛出 `FeedFetchException`，异常信息包含 HTTP 状态码和源名称。
- RSS / Atom 解析失败会抛出 `FeedParseException`，异常信息包含源名称。
- 单源抓取、解析或入库失败会被 `FeedIngestionService` 捕获并记录 warning，不影响后续源。
- 当前没有重试、熔断、限流和失败告警；后续接入真实源后再按需要补充。

## 测试与验证

本地默认只跑基础测试：

- `./mvnw -o -Dspring.docker.compose.enabled=false test`

CI 负责运行包含数据库集成测试的完整验证。集成测试依赖独立 PostgreSQL service，并关闭 Spring Docker Compose 自动接管。

当前重点测试：

- `FeedPropertiesTest`：源配置过滤和校验。
- `RomeFeedParserTest`：RSS / Atom fixture 解析。
- `HttpFeedClientTest`：RestClient 抓取和非 2xx 错误处理。
- `ArticleDeduplicationServiceTest`：去重 key 和哈希行为。
- `ArticleIngestionServiceTest`：重复跳过和新文章入库。
- `ArticleQueryServiceTest`：简报候选文章查询参数校验。
- `FeedIngestionServiceTest`：批量编排统计和单源失败隔离。
- `FeedIngestionSchedulerTest`：定时任务开关和触发行为。
- `ArticleMapperIT`：数据库表、索引和 MyBatis 写入行为。
- `ArticleQueryMapperIT`：文章查询时间窗口、回退时间和排序行为。

## 维护注意

- 不要把 RSS 内容提前解码为 `String` 再交给 XML 解析器；保留 `InputStream` 有利于 XML 解析器处理 BOM 和 XML encoding 声明。
- `httpclient5` 版本由 Spring Boot 依赖管理，不在 `pom.xml` 中显式写版本。
- 新增字段必须同时更新 `NewArticle`、`Article`、`ArticleMapper`、Flyway 迁移和测试。
- 简报候选文章查询使用半开区间 `[startInclusive, endExclusive)`，时间口径为 `COALESCE(published_at, created_at)`。
- 调整去重规则时，需要同步考虑应用层判断和数据库唯一索引。
- 引入真实外部 RSS 源时，应优先补充超时、重试、User-Agent、日志和可观测性策略。

## 后续事项

- 补充真实 RSS 源清单，并区分官方源、一手源和行业源。
- 后续可按需要增加手动触发入口。
- 增加抓取超时、重试和失败告警策略。
- 对摘要字段做 HTML 清理和长度控制。
- 基于 `ArticleQueryService` 接入 AI 摘要、Markdown 简报和邮件推送。
