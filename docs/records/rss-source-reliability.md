# RSS 源清单与抓取可靠性记录

> 本文档记录 RSS 源清单和 HTTP 抓取可靠性的当前设计。长期总览见 [RSS 抓取入库记录](rss-ingestion.md)，实现细节以源码、测试和配置为准。

## 背景

RSS 入库链路已经具备配置源读取、RSS / Atom 解析、去重入库、定时触发、手动触发、运行记录持久化和简报候选查询。为了支撑后续 AI 摘要、Markdown 简报、邮件推送和归档，系统需要一组可维护的真实 RSS 源，并且在外部网络波动、站点限流或服务端错误时能给出清晰、可诊断的失败信息。

当前仍不引入源管理表、告警系统、代理池、全文爬虫或 Web 管理界面。

## 当前范围

已完成：

- 在公共配置中补充第一批官方或一手 RSS / Atom 源。
- 新增 `signal-brief.feed-http` 配置，集中管理 User-Agent、连接超时、读取超时和重试策略。
- 为 feed 抓取装配专用 `feedRestClient`，底层使用 Apache HttpClient 5。
- `HttpFeedClient` 对非 2xx、客户端 I/O 异常和未预期异常进行分类，并保留状态码、尝试次数等上下文。
- `FeedIngestionService` 保持单源失败隔离，一个源失败不影响后续源。
- `rss_ingestion_run` 和 `rss_ingestion_source_run` 持久化运行级统计与源级失败明细。
- 补充配置绑定、HTTP 成功路径、失败分类、可重试状态、不可重试状态和重试次数测试。

未包含：

- RSS 源数据库管理和动态启停。
- 失败告警、指标上报或可视化运维页面。
- 运行记录保留、归档和清理策略。
- 对解析失败、入库失败的自动重试。

## 源清单策略

源清单继续通过 `signal-brief.feeds` 维护，修改后需要重启应用。第一批源只使用官方或一手 RSS / Atom 地址：

- Spring Blog：`https://spring.io/blog.atom`，分类 `FRAMEWORK`。
- Inside Java：`https://inside.java/feed.xml`，分类 `JAVA`。
- Kubernetes Blog：`https://kubernetes.io/feed.xml`，分类 `FRAMEWORK`。
- OpenAI News：`https://openai.com/news/rss.xml`，分类 `AI`。

新增真实源时，必须优先从官方网站、官方页面链接或实际 HTTP 响应确认，不使用第三方 RSS 代理或聚合站推测出的地址。源本身可以保持 `enabled=true`，但调度任务默认关闭，因此普通启动不会自动访问外部站点。

不同源对 RSS / Atom 字段使用不一致。新增源时需要用 fixture 或实际响应确认短摘要和正文位置：RSS `description`、RSS `content:encoded`、Atom `summary`、Atom `content` 都可能出现。

## HTTP 抓取配置

`signal-brief.feed-http` 只作用于外部 feed 抓取客户端，不影响内部 API、OpenAPI、AI Provider 或后续其他 HTTP 调用。

当前配置项：

- `user-agent`：RSS 抓取请求的 User-Agent，默认 `signal-brief/0.0.1`。
- `connect-timeout`：连接建立超时，默认 `3s`。
- `read-timeout`：响应读取超时，默认 `10s`。
- `retry.max-attempts`：总尝试次数，默认 `2`。
- `retry.backoff`：两次尝试之间的等待时间，默认 `1s`。

配置通过 `FeedHttpProperties` 绑定和校验。新增或调整环境变量时，需要同步 `.env.example`、配置约定文档和相关 record。

## 失败分类与重试

HTTP 层只负责 feed 拉取，不处理 RSS / Atom 解析、去重和数据库写入的重试。

当前失败类型：

- `HTTP_STATUS`：远端返回非 2xx 状态。
- `CLIENT_IO`：连接、超时、读取等客户端 I/O 异常。
- `UNEXPECTED`：未预期异常。

重试策略保持保守：

- 网络异常、连接超时、读取超时、`429` 和 `5xx` 可以重试。
- `4xx` 中除 `429` 外不重试。
- RSS / Atom 解析失败、数据校验失败和数据库入库失败不在 HTTP 层重试。
- `max-attempts` 表示总尝试次数，不是额外重试次数。

如果重试等待期间线程被中断，必须恢复中断标记，并把已知失败上下文保留下来，避免吞掉调度线程生命周期信号。

## 日志与诊断

`FeedFetchException` 携带失败类型、HTTP 状态、当前尝试次数和最大尝试次数。`FeedIngestionService` 记录源名称、URL、失败类型、状态码和尝试次数，便于定位问题源。

当前除了在 `FeedIngestionResult` 中累计失败源数量，也会把运行级统计写入 `rss_ingestion_run`，并把每个源的执行结果写入 `rss_ingestion_source_run`。源级失败明细包含失败类型、HTTP 状态、尝试次数、最大尝试次数和错误摘要，内部 API 可查询最近运行和单次运行明细。连续失败告警、指标上报和运行记录清理仍属于后续设计。

## 落地文件

- `src/main/java/cn/name/celestrong/signalbrief/config/FeedHttpProperties.java`
- `src/main/java/cn/name/celestrong/signalbrief/config/FeedHttpConfiguration.java`
- `src/main/java/cn/name/celestrong/signalbrief/feed/FeedFetchFailureType.java`
- `src/main/java/cn/name/celestrong/signalbrief/feed/FeedFetchException.java`
- `src/main/java/cn/name/celestrong/signalbrief/feed/HttpFeedClient.java`
- `src/main/java/cn/name/celestrong/signalbrief/ingestion/FeedIngestionService.java`
- `src/test/java/cn/name/celestrong/signalbrief/config/FeedHttpPropertiesTest.java`
- `src/test/java/cn/name/celestrong/signalbrief/feed/HttpFeedClientTest.java`
- `src/main/resources/application.yaml`
- `.env.example`

## 验证方式

本地基础验证：

```bash
./mvnw -Dspring.docker.compose.enabled=false test
```

关注 feed HTTP 行为时可运行：

```bash
./mvnw -Dspring.docker.compose.enabled=false -Dtest=FeedHttpPropertiesTest,HttpFeedClientTest test
```

数据库集成测试继续交给 CI 的 `verify`，本地排查 Mapper 或迁移问题时再单独运行。

## 后续方向

- 为连续失败源增加告警策略。
- 增加运行记录保留、归档和清理策略。
- 根据真实运行结果调整源清单、超时和重试默认值。
- 接入 AI 摘要、Markdown 简报、邮件发送和归档后，补充端到端运行记录。
