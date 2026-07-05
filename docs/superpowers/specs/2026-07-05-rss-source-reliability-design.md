# RSS 源清单与抓取可靠性设计

## 背景

SignalBrief 已完成 RSS / Atom 抓取、解析、去重入库、定时触发、手动触发和 Markdown 简报草稿生成。当前 `signal-brief.feeds` 仍为空，`HttpFeedClient` 只有固定 `User-Agent`，没有显式超时、重试或失败分类。下一步应先让系统具备可维护的真实源清单和基础抓取可靠性，再进入 AI 摘要、邮件发送和归档。

## 目标

- 补充第一批官方或一手 RSS / Atom 源，优先服务 Java 后端技术半月报。
- 保持 RSS 源仍由配置维护，暂不引入源管理表或后台。
- 为 feed HTTP 抓取补充可配置的 `User-Agent`、连接超时、读取超时和有限重试。
- 明确哪些失败可以重试，哪些失败只记录并交给单源失败隔离处理。
- 增强日志和异常上下文，便于定位具体源、HTTP 状态和尝试次数。
- 保持测试不访问真实外部源。

## 非目标

- 不增加任务运行记录表。
- 不做失败告警对接，例如邮件、Webhook 或监控平台。
- 不做复杂限流、代理池、HTML 抓取或全文爬虫。
- 不引入 Web 管理界面。

## 源清单策略

源清单继续放在 `signal-brief.feeds`。默认调度仍由 `signal-brief.ingestion.enabled=false` 保护，避免应用启动后自动访问外部站点。源本身可以启用，便于手动触发和生产运行共享同一份基础清单。

第一批源只选择官方页面明确存在 RSS 入口或已验证 Atom/RSS 地址的来源。候选方向：

- Spring Blog：Spring 官方博客页面提供 RSS feeds，分类为 `FRAMEWORK`。
- Inside Java：Oracle Java team 新闻源，分类为 `JAVA`。
- Kubernetes Blog：官方博客页脚提供 RSS Feed，分类为 `FRAMEWORK`。
- OpenAI News：官方 News 页面页脚提供 RSS 入口，分类为 `AI`。

实施时必须从官方页面或实际 HTTP 响应校验 URL，再写入配置和文档；不要使用第三方聚合站推测出的 URL。

## HTTP 可靠性

新增 `signal-brief.feed-http` 配置：

```yaml
signal-brief:
  feed-http:
    user-agent: ${SIGNAL_BRIEF_FEED_HTTP_USER_AGENT:signal-brief/0.0.1}
    connect-timeout: ${SIGNAL_BRIEF_FEED_HTTP_CONNECT_TIMEOUT:3s}
    read-timeout: ${SIGNAL_BRIEF_FEED_HTTP_READ_TIMEOUT:10s}
    retry:
      max-attempts: ${SIGNAL_BRIEF_FEED_HTTP_RETRY_MAX_ATTEMPTS:2}
      backoff: ${SIGNAL_BRIEF_FEED_HTTP_RETRY_BACKOFF:1s}
```

`HttpFeedClient` 继续使用 Spring `RestClient`，底层保持 Apache HttpClient 5。超时配置只作用于 feed 抓取客户端，不影响后续内部 API 或其他 HTTP 调用。

重试规则保持保守：

- 网络连接异常、连接超时、读取超时、`429`、`5xx` 可以重试。
- `4xx` 中除 `429` 外不重试。
- RSS / Atom 解析失败、数据校验失败和数据库入库失败不在 HTTP 层重试。
- `max-attempts` 表示总尝试次数，默认 `2`，避免低频半月报任务被单个源长时间拖住。

## 错误处理与日志

保留 `FeedIngestionService` 的单源失败隔离语义：一个源失败不影响其他源。`FeedFetchException` 增加更明确的失败上下文，至少能区分 HTTP 状态失败和客户端 I/O 失败。日志记录源名称、URL、失败类型、HTTP 状态、当前尝试次数和最大尝试次数。

本阶段 `FeedIngestionResult` 可以保持兼容，只累计失败源数量。任务运行记录、失败明细持久化和告警在下一阶段单独设计。

## 测试策略

- `FeedHttpPropertiesTest` 覆盖默认值、环境变量绑定和非法参数。
- `HttpFeedClientTest` 使用 `MockRestServiceServer` 覆盖成功请求、`User-Agent`、非 2xx、可重试状态、不可重试状态和重试次数。
- `FeedIngestionServiceTest` 保持 fake 测试，验证单源失败隔离和统计汇总不被破坏。
- RSS 解析继续使用 `src/test/resources/fixtures/rss`，不访问真实外部 RSS 源。
- 本地基础验证使用 `./mvnw -Dspring.docker.compose.enabled=false test`；真实数据库集成继续交给 CI 的 `verify`。

## 文档与配置同步

新增环境变量必须同步 `.env.example`、README 和 `docs/records/rss-ingestion.md`。README 说明默认不会自动抓取外部源，只有开启定时任务或内部手动触发接口后才会访问真实 RSS。record 文档只记录长期决策和维护约束，不保存实现过程清单。

## 后续方向

本设计完成后，下一步可以单独规划任务运行记录表，保存每次抓取的开始时间、结束时间、触发方式、源级统计和失败原因。该能力应和告警、内部 API 查询入口一起设计，避免本阶段过早扩大数据库模型。
