# RSS 抓取入库记录

> 本文档记录 RSS 抓取、解析、去重、入库和查询出口的当前设计。实现细节以源码、迁移脚本和测试为准。

## 定位

RSS 入库是 SignalBrief 的第一段核心链路，目标是把外部 RSS / Atom 源转换成可供后续 AI 摘要、Markdown 简报和邮件推送使用的本地文章数据。

当前已经覆盖：配置源读取、HTTP 抓取、RSS / Atom 解析、文章去重、PostgreSQL 入库、定时触发、批次统计和简报候选查询。

当前暂不覆盖：RSS 源数据库管理、手动触发接口、任务运行表、重试告警、AI 摘要、Markdown 生成和邮件发送。

## 当前模块

- `config/FeedProperties`：绑定 `signal-brief.feeds`，并提供启用源过滤。
- `config/IngestionProperties`：绑定 `signal-brief.ingestion` 的开关和 cron。
- `feed/FeedClient`：定义 feed 获取边界，返回 `InputStream`。
- `feed/HttpFeedClient`：使用 Spring `RestClient` 抓取 feed，设置 `User-Agent: signal-brief`，非 2xx 响应包装为 `FeedFetchException`。
- `feed/RomeFeedParser`：使用 ROME 和 `XmlReader` 解析 RSS / Atom，保留 XML 自身编码识别能力。
- `article/ArticleDeduplicationService`：按 guid、url、contentHash 顺序判断重复。
- `article/ArticleIngestionService`：把 `FetchedArticle` 转为 `NewArticle` 并写库。
- `article/ArticleMapper`：负责文章写入和去重查询。
- `article/ArticleQueryService`：提供后续简报候选文章查询。
- `ingestion/FeedIngestionService`：编排多源入库，单源失败隔离。
- `ingestion/FeedIngestionScheduler`：在配置开启后按 cron 触发入库。

## 配置

默认配置位于 `src/main/resources/application.yaml`：

```yaml
signal-brief:
  ingestion:
    enabled: ${SIGNAL_BRIEF_INGESTION_ENABLED:false}
    cron: "${SIGNAL_BRIEF_INGESTION_CRON:0 0 6 1,16 * *}"
  feeds: []
```

RSS 源通过配置维护，第一版不入库；修改源配置后需要重启应用。每个源包含 `name`、`url`、`category`、`enabled`，其中 `category` 必须来自 `ArticleCategory`。

定时任务默认关闭，避免本地启动或测试时访问外部源。需要开启时设置：

```bash
SIGNAL_BRIEF_INGESTION_ENABLED=true ./mvnw spring-boot:run
```

默认 cron 表示每月 1 日和 16 日 06:00 执行。

## 数据与去重

文章表由 `src/main/resources/db/migration/V1__create_article_table.sql` 创建，核心字段包括来源、分类、标题、文章 URL、guid、发布时间、摘要、内容哈希和创建更新时间。

应用层去重顺序：

1. 有 guid 时，按 `source_name + guid` 判断。
2. 没有 guid 但有 url 时，按 `url` 判断。
3. guid 和 url 都缺失时，按 `content_hash` 判断。

数据库层使用 `ON CONFLICT DO NOTHING` 和唯一索引兜底：`uk_article_source_guid`、`uk_article_url`、`uk_article_content_hash`。

## 查询出口

`ArticleQueryService.findBriefCandidates(startInclusive, endExclusive)` 是后续简报生成的文章入口。查询规则：

- 时间窗口使用半开区间 `[startInclusive, endExclusive)`。
- 有效时间优先使用 `published_at`，缺失时回退 `created_at`。
- 排序按 `category ASC`、有效时间倒序、`id DESC`，保证输出稳定。

## 测试与验证

本地日常验证默认运行基础测试，并关闭 Spring Docker Compose：

```bash
./mvnw -o -Dspring.docker.compose.enabled=false test
```

CI 运行完整验证：

```bash
./mvnw -B verify
```

当前测试覆盖配置绑定、RSS / Atom fixture 解析、HTTP 抓取异常、去重、入库编排、定时开关、查询窗口和 MyBatis 数据库行为。数据库集成测试依赖 CI 中独立 PostgreSQL service。

## 维护约束

- 不要把 feed 内容提前解码成 `String` 再交给 XML 解析器；保持 `InputStream` 入口，让 XML 解析器处理 BOM 和 XML 声明编码。
- HTTP 调用优先使用 Spring `RestClient`；项目已引入 `httpclient5`，底层实现由 Spring Boot 依赖管理和自动配置选择。
- `httpclient5`、Spring 生态依赖不要在 `pom.xml` 重复声明版本，除非 Spring Boot 不管理该依赖。
- 新增文章字段时同步更新 record、Mapper、迁移脚本和测试。
- 调整去重规则时同步考虑应用层判断、数据库唯一索引和历史数据兼容。
- 接入真实源后优先补充超时、重试、限流、失败告警和可观测性。

## 下一步

- 补充真实 RSS 源清单，并区分官方源、一手源和行业源。
- 设计手动触发入口，便于本地调试和运维补偿。
- 对摘要字段做 HTML 清理和长度控制。
- 基于查询出口实现 AI 摘要、Markdown 简报和邮件推送。
