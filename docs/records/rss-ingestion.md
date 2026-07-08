# RSS 抓取入库记录

> 本文档记录 RSS 抓取、解析、去重、入库和查询出口的当前设计。内容提取细节见 [RSS / Atom 内容提取增强记录](rss-atom-content-extraction.md)，源清单与 HTTP 抓取可靠性细节见 [RSS 源清单与抓取可靠性记录](rss-source-reliability.md)，运行记录细节见 [RSS 入库运行记录](rss-ingestion-run-record.md)。实现细节以源码、迁移脚本和测试为准。

## 定位

RSS 入库是 SignalBrief 的第一段核心链路，目标是把外部 RSS / Atom 源转换成可供后续 AI 摘要、Markdown 简报和邮件推送使用的本地文章数据。

当前已经覆盖：真实配置源读取、HTTP 抓取超时、有限重试、失败分类、RSS / Atom 解析、短摘要与正文片段提取、HTML 清洗、文章去重、PostgreSQL 入库、定时触发、手动触发、批次统计、RSS 入库运行与源级明细记录、简报候选查询。

当前暂不覆盖：RSS 源数据库管理、失败告警、AI 摘要和邮件发送。

## 当前模块

- `config/FeedProperties`：绑定 `signal-brief.feeds`，并提供启用源过滤。
- `config/FeedHttpProperties`：绑定 feed 抓取客户端的 User-Agent、超时和重试配置。
- `config/FeedHttpConfiguration`：装配 feed 专用 `RestClient`，底层使用 Apache HttpClient 5。
- `config/IngestionProperties`：绑定 `signal-brief.ingestion` 的开关和 cron。
- `content/HtmlContentCleaner`：通用 HTML 片段清洗组件，将 feed 或后续网页正文中的 HTML 转为纯文本。
- `feed/FeedClient`：定义 feed 获取边界，返回 `InputStream`。
- `feed/HttpFeedClient`：使用 feed 专用 Spring `RestClient` 抓取 feed，非 2xx 响应和客户端 I/O 异常包装为 `FeedFetchException`。
- `feed/FeedEntryContentExtractor`：从 ROME `SyndEntry` 中选择短摘要和正文候选字段，支持 RSS `description`、Atom `summary` / `content` 和 RSS `content:encoded`。
- `feed/RomeFeedParser`：使用 ROME 和 `XmlReader` 解析 RSS / Atom，保留 XML 自身编码识别能力。
- `article/ArticleDeduplicationService`：按 guid、url、contentHash 顺序判断重复。
- `article/ArticleIngestionService`：把 `FetchedArticle` 转为 `NewArticle` 并写库。
- `article/ArticleMapper`：负责文章写入、重复文章空字段补齐和去重查询。
- `article/ArticleQueryService`：提供后续简报候选文章查询。
- `ingestion/FeedIngestionService`：编排多源入库，单源失败隔离。
- `ingestion/IngestionRunRecorder`：持久化 RSS 入库运行记录和源级执行明细。
- `ingestion/RssIngestionRunQueryService`：查询最近运行记录和单次运行明细。
- `ingestion/FeedIngestionScheduler`：在配置开启后按 cron 触发入库。
- `internal/ManualTriggerController`：在内部 API 开启后，提供 RSS 入库手动触发和运行记录查询入口。
- `internal/OpenApiConfiguration`：在 OpenAPI 开启后提供 internal 分组和接口元信息。

## 配置

默认配置位于 `src/main/resources/application.yaml`：

```yaml
signal-brief:
  ingestion:
    enabled: ${SIGNAL_BRIEF_INGESTION_ENABLED:false}
    cron: "${SIGNAL_BRIEF_INGESTION_CRON:0 0 6 1,16 * *}"
  feed-http:
    user-agent: ${SIGNAL_BRIEF_FEED_HTTP_USER_AGENT:signal-brief/0.0.1}
    connect-timeout: ${SIGNAL_BRIEF_FEED_HTTP_CONNECT_TIMEOUT:3s}
    read-timeout: ${SIGNAL_BRIEF_FEED_HTTP_READ_TIMEOUT:10s}
    retry:
      max-attempts: ${SIGNAL_BRIEF_FEED_HTTP_RETRY_MAX_ATTEMPTS:2}
      backoff: ${SIGNAL_BRIEF_FEED_HTTP_RETRY_BACKOFF:1s}
  feeds:
    - name: Spring Blog
      url: https://spring.io/blog.atom
      category: FRAMEWORK
      enabled: true
    - name: Inside Java
      url: https://inside.java/feed.xml
      category: JAVA
      enabled: true
    - name: Kubernetes Blog
      url: https://kubernetes.io/feed.xml
      category: FRAMEWORK
      enabled: true
    - name: OpenAI News
      url: https://openai.com/news/rss.xml
      category: AI
      enabled: true
```

RSS 源本身通过配置维护，第一版不做源表管理；修改源配置后需要重启应用。每个源包含 `name`、`url`、`category`、`enabled`，其中 `category` 必须来自 `ArticleCategory`。

`signal-brief.feed-http` 控制外部 feed 抓取客户端，包含 `user-agent`、`connect-timeout`、`read-timeout` 和 `retry`。这些配置只作用于 `feedRestClient`，不影响内部 API 或后续 AI Provider HTTP 客户端。

HTTP 抓取失败按类型记录：HTTP 状态失败、客户端 I/O 失败和未预期失败。仅网络异常、超时、`429` 和 `5xx` 参与有限重试；其他 `4xx`、解析失败和入库失败不在 HTTP 层重试。

定时任务默认关闭，避免本地启动或测试时访问外部源。需要开启时设置：

```bash
SPRING_PROFILES_ACTIVE=dev SIGNAL_BRIEF_INGESTION_ENABLED=true ./mvnw spring-boot:run
```

默认 cron 表示每月 1 日和 16 日 06:00 执行。

手动触发入口位于 `POST /internal/ingestions/rss`，由 `signal-brief.internal-api.enabled` 控制是否注册。响应沿用 `FeedIngestionResult`，并包含本次运行的 `runId`。该入口不受 `signal-brief.ingestion.enabled` 影响，后者只控制定时任务是否注册。

运行记录查询入口同样由内部 API 开关控制：

- `GET /internal/ingestions/rss/runs`：按开始时间倒序查询最近的 RSS 入库运行记录。
- `GET /internal/ingestions/rss/runs/{id}`：查询单次 RSS 入库运行及各源执行明细。

OpenAPI 文档由 `SPRINGDOC_API_DOCS_ENABLED` 和 `SPRINGDOC_SWAGGER_UI_ENABLED` 控制，默认关闭；本地开启后可访问 `/internal/api-docs/internal` 和 `/internal/swagger-ui.html`。当前文档通过 internal 分组匹配 `/internal/**`，后续对外 API 应新增 public 分组，不要使用全局扫描配置互相影响。

## 数据与去重

文章表由 `src/main/resources/db/migration/V1__create_article_table.sql` 创建，核心字段包括来源、分类、标题、文章 URL、guid、发布时间、摘要、正文片段、内容哈希和创建更新时间。

文章表新增 `content_text`，用于保存 RSS / Atom 正文片段清洗后的纯文本。`summary` 继续表示短摘要，`content_text` 表示正文上下文；旧数据允许为空。

RSS 入库运行记录由 `rss_ingestion_run` 和 `rss_ingestion_source_run` 保存。运行级记录包含触发方式、状态、开始结束时间、耗时、源数量和抓取/入库/跳过/失败统计；源级记录包含源名称、URL、分类、状态、抓取/入库/跳过统计和失败诊断字段。源级失败明细包含失败类型、HTTP 状态、尝试次数、最大尝试次数和错误摘要（`error_message`）。

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

当前测试覆盖配置绑定、RSS / Atom fixture 解析、HTML 清洗、正文片段提取、HTTP 抓取异常、去重、入库编排、运行记录持久化与查询、定时开关、手动触发接口、查询窗口和 MyBatis 数据库行为。数据库集成测试依赖 CI 中独立 PostgreSQL service。

## 维护约束

- 不要把 feed 内容提前解码成 `String` 再交给 XML 解析器；保持 `InputStream` 入口，让 XML 解析器处理 BOM 和 XML 声明编码。
- HTTP 调用优先使用 Spring `RestClient`；feed 抓取使用独立 `feedRestClient`，底层为 Apache HttpClient 5。
- `httpclient5`、Spring 生态依赖不要在 `pom.xml` 重复声明版本，除非 Spring Boot 不管理该依赖。
- 新增文章字段时同步更新 record、Mapper、迁移脚本和测试。
- 调整去重规则时同步考虑应用层判断、数据库唯一索引和历史数据兼容。
- 重复文章重新抓取时，只补齐为空的 `summary` / `content_text`，不要覆盖已有非空内容。
- `content_text` 不参与现有 `content_hash`，避免改变历史去重口径。
- 新增真实源时必须优先使用官方或一手 RSS / Atom 地址，并通过官方页面或实际 HTTP 响应校验。

## 下一步

- 增加连续失败告警和运行记录保留清理策略。
- 将 `content_text` 纳入后续 AI 摘要输入增强，并保留摘要展示的稳定降级路径。
