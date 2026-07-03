# RSS 抓取与入库基础设计

## 背景

SignalBrief 的 MVP 目标是跑通“RSS 抓取 -> 去重过滤 -> AI 分类汇总 -> Markdown 简报 -> 邮件推送”。当前仓库只有 Spring Boot 工程骨架、PostgreSQL 配置、Flyway 目录和项目文档，还没有业务模型或入库链路。

本设计聚焦第一条可运行业务链路：从配置文件读取 RSS 源，拉取 RSS / Atom 内容，标准化文章字段，按稳定规则去重，并写入 PostgreSQL。AI 摘要、邮件推送、定时调度和 Web 管理不纳入本阶段。

## 目标

- 在配置文件中维护第一批 RSS 源。
- 使用 ROME 解析 RSS / Atom。
- 将 feed item 标准化为内部文章对象。
- 按 `guid`、`url`、`content_hash` 顺序去重。
- 将新文章保存到 PostgreSQL。
- 为后续分类、摘要、邮件和归档提供稳定数据基础。

## 非目标

- 不做 RSS 源的数据库管理和 Web 配置页面。
- 不抓取无 RSS 的网页全文。
- 不做 AI 分类和摘要。
- 不做邮件推送。
- 不做分布式调度、锁或多实例并发控制。
- 不依赖真实公网 RSS 完成自动化测试。

## 配置设计

配置命名空间为 `signal-brief.feeds`。每个源包含：

```yaml
signal-brief:
  feeds:
    - name: Spring Blog
      url: https://spring.io/blog.atom
      category: FRAMEWORK
      enabled: true
```

字段含义：

- `name`：源名称，用于日志、去重上下文和入库字段。
- `url`：RSS / Atom 地址。
- `category`：源默认分类，第一版用字符串或枚举承载固定板块。
- `enabled`：是否参与抓取。

配置读取使用 Spring `@ConfigurationProperties`。启动时校验必填字段，禁用源不参与抓取。

## 数据模型

第一版只新增 `article` 表，不新增 `feed_source` 表。RSS 源配置保留在 YAML 中，避免第一阶段引入管理入口和种子数据。

`article` 字段：

```text
id
source_name
source_url
category
title
url
guid
published_at
summary
content_hash
created_at
updated_at
```

约束策略：

- `url` 唯一，适用于绝大多数官方 RSS。
- `source_name + guid` 唯一，适用于有稳定 guid 的源。
- `content_hash` 唯一，作为缺少 guid 或 url 时的兜底。

PostgreSQL 的唯一约束对 `NULL` 不冲突，因此 `guid`、`url` 为空时仍需在保存逻辑中按可用字段选择去重路径。`content_hash` 必须始终生成。

## 模块设计

### 配置模块

包建议：`cn.name.celestrong.signalbrief.config`

职责：

- 绑定 `signal-brief.feeds`。
- 暴露启用的 feed 配置。
- 对 `name`、`url`、`category` 做基础校验。

### 抓取模块

包建议：`cn.name.celestrong.signalbrief.feed`

核心对象：

- `FeedSourceConfig`：单个配置源。
- `FetchedArticle`：解析后的临时文章对象。
- `FeedClient`：按 URL 获取 feed 内容。
- `RomeFeedParser`：用 ROME 将 feed item 转为 `FetchedArticle`。

抓取模块只负责外部输入和解析，不直接写数据库。

### 文章模块

包建议：`cn.name.celestrong.signalbrief.article`

核心对象：

- `Article`：数据库文章记录。
- `ArticleRepository` 或 MyBatis `ArticleMapper`：查询和保存文章。
- `ArticleDeduplicationService`：生成 `content_hash`，判断是否已存在。
- `ArticleIngestionService`：将 `FetchedArticle` 转换并保存。

`content_hash` 规则：

```text
normalized(title) + "|" + normalized(url) + "|" + source_name + "|" + published_at
```

其中 `normalized` 至少做 trim 和空值处理。后续如果发现 RSS 源 title 或发布时间不稳定，再调整 hash 策略。

### 编排模块

包建议：`cn.name.celestrong.signalbrief.ingestion`

核心服务：

- `FeedIngestionService.ingestEnabledFeeds()`

职责：

1. 读取启用的 RSS 源。
2. 逐个抓取并解析。
3. 将解析结果交给文章模块去重入库。
4. 返回本次执行统计：源数量、抓取条数、新增条数、跳过条数、失败源数量。

第一版先提供应用服务方法和测试入口，不强制暴露 HTTP 接口。定时任务可以在下一阶段通过 `@Scheduled` 调用该服务。

## 数据流

```text
application.yaml
-> FeedProperties
-> FeedIngestionService
-> FeedClient
-> RomeFeedParser
-> FetchedArticle
-> ArticleIngestionService
-> ArticleDeduplicationService
-> ArticleMapper
-> PostgreSQL article
```

## 错误处理

- 单个 RSS 源拉取失败时记录 `warn` 日志，并继续处理其他源。
- 单条 item 字段异常时跳过该 item，记录源名称、标题或 guid、异常原因。
- 数据库唯一约束冲突视为重复文章，不让整批任务失败。
- 网络超时、解析失败和数据库失败使用清晰异常类型或日志上下文区分。
- 日志至少包含 `source_name`、`source_url` 和本次执行统计。

## 测试策略

### 单元测试

- `RomeFeedParser` 使用本地 RSS / Atom fixture，验证标题、链接、guid、发布时间、摘要提取。
- `ArticleDeduplicationService` 覆盖 `guid`、`url`、`content_hash` 三种去重路径。
- `FeedProperties` 覆盖启用/禁用源和必填字段校验。

### 集成测试

- Flyway 能创建 `article` 表。
- MyBatis mapper 能插入和查询文章。
- 重复 `guid`、重复 `url`、重复 `content_hash` 不会产生多条记录。
- `FeedIngestionService` 使用本地 fixture 或 fake client 跑通“多源 -> 多文章 -> 去重保存”。

自动化测试不访问真实公网 RSS，避免 CI 受网络和第三方可用性影响。

## 实施顺序

1. 新增 Flyway migration，创建 `article` 表和唯一索引。
2. 新增配置属性类和 feed 配置结构。
3. 新增 ROME 解析器和本地 fixture 测试。
4. 新增文章 mapper、去重服务和入库服务。
5. 新增编排服务和集成测试。
6. 更新 README 或示例配置，说明 RSS 源配置方式。

## 验收标准

- 配置一个启用 RSS 源后，服务能完成解析和入库。
- 同一 feed 重复执行不会重复插入已有文章。
- `./mvnw test` 通过。
- 在可用 PostgreSQL 环境下 `./mvnw verify` 通过。
- 代码遵守 `docs/java-coding-standard.md` 和 `docs/preserving-context-in-code.md`。
