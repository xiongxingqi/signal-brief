# SignalBrief 项目说明

SignalBrief 是面向 Java 后端开发者的个人资讯聚合与中文技术半月报系统。项目目标不是抓取尽可能多的信息，而是从官方源和一手源中提取高信号内容，完成采集、去重、分类、摘要、邮件推送和归档。

核心链路：

```text
官方源 / 一手源 -> RSS / Atom 抓取 -> 去重入库 -> 候选文章查询 -> AI 摘要 -> Markdown 简报 -> 邮件推送 -> 归档
```

`Signal` 表示从噪声里保留有效信号，`Brief` 表示最终输出短、清楚、来源可追溯的简报。当前目标读者是项目维护者本人，优先服务稳定的个人半月报工作流。

## 当前阶段

项目已经完成 Spring Boot 工程骨架和 RSS 入库基础能力。当前代码已具备：

- 通过 `signal-brief.feeds` 配置 RSS / Atom 源。
- 使用 Spring `RestClient` 抓取 feed 内容。
- 使用 ROME 解析 RSS / Atom 条目。
- 按 guid、url、contentHash 去重。
- 使用 MyBatis 写入 PostgreSQL `article` 表。
- 通过 Flyway 管理数据库迁移。
- 通过 `signal-brief.ingestion` 控制定时入库任务。
- 通过 `ArticleQueryService` 查询后续简报候选文章。
- 基于候选文章生成确定性的 Markdown 简报草稿。

AI 摘要、邮件推送、归档表和手动触发入口仍属于后续阶段。

## 信息源策略

第一阶段优先使用官方 RSS、官方博客、Release Notes、安全公告和稳定文档源。没有 RSS 的站点先作为候选源记录，后续再评估是否用 jsoup 做低频 HTML 解析。

当前分类以 `ArticleCategory` 为准：

| 分类 | 内容范围 |
| --- | --- |
| `JAVA` | JDK、JEP、JVM、语言特性 |
| `FRAMEWORK` | Spring、中间件、数据库、云原生组件 |
| `INDUSTRY` | 云厂商、开源生态、监管政策、平台动态 |
| `CAREER` | 工程实践、团队协作、招聘和职场趋势 |
| `AI` | AI 工具、模型平台、API、价格和能力变化 |

系统应优先保留原文链接。英文资料不做逐句翻译，而是理解核心变化后输出中文摘要、影响和建议。

## 模块边界

当前主要代码边界如下：

- `config`：绑定项目配置，例如 RSS 源和入库调度配置。
- `feed`：定义 feed 获取与解析边界，屏蔽 HTTP 客户端和 RSS 解析细节。
- `article`：文章模型、去重、入库和候选查询。
- `ingestion`：RSS 入库编排和定时触发。
- `brief`：基于候选文章生成 Markdown 简报草稿，后续可作为 AI 摘要和邮件发送输入。

长期上，AI 摘要、简报生成、邮件发送和归档应继续保持独立边界，避免把抓取、生成和投递逻辑混在一个服务里。

## 数据与去重

当前 `article` 表保存来源名称、来源 URL、分类、标题、文章 URL、guid、发布时间、摘要、内容哈希和创建更新时间。

去重顺序：

1. 有 guid 时，按 `source_name + guid` 判断。
2. 没有 guid 但有 url 时，按 url 判断。
3. guid 和 url 都不可用时，按 contentHash 判断。

应用层去重和数据库唯一约束必须保持一致。新增文章字段时，需要同步更新 Flyway 迁移、`NewArticle`、`Article`、MyBatis Mapper 和测试。

## 配置与运行

关键配置：

- `signal-brief.feeds`：RSS 源列表，第一版由配置文件维护，修改后需要重启应用。
- `signal-brief.ingestion.enabled`：RSS 定时入库开关，默认 `false`。
- `signal-brief.ingestion.cron`：RSS 入库 cron，默认 `0 0 6 1,16 * *`。
- datasource、SMTP、AI provider 等敏感配置通过环境变量注入。

本地日常开发默认只跑基础测试：

```bash
./mvnw -Dspring.docker.compose.enabled=false test
```

数据库集成测试、迁移验证和 Mapper 行为由 CI 运行完整 `verify`，CI 使用独立 PostgreSQL service，并关闭 Spring Boot Docker Compose 接管。

## 技术选型

当前实现以 `pom.xml` 为准：

| 模块 | 选型 |
| --- | --- |
| 主体框架 | Spring Boot 4 |
| 运行时 | Java 25 |
| 构建 | Maven Wrapper |
| 数据库 | PostgreSQL |
| 迁移 | Flyway |
| 数据访问 | MyBatis |
| RSS / Atom | ROME |
| HTTP | Spring `RestClient`，底层使用 Apache HttpClient 5 |
| HTML 兜底 | jsoup |
| 邮件 | Spring Mail |
| 工具库 | Apache Commons、Guava |

Spring Boot 已管理版本的依赖不显式声明版本；未被 Boot 管理的依赖在 `pom.xml` 属性中集中声明。

## AI 与简报原则

AI 模块后续应围绕“可靠简报”设计，而不是追求模型能力堆叠：

- 模型 provider、model、base URL 和 API key 必须配置化。
- 每条重要结论必须能追溯到原文链接。
- 不让模型编造来源、版本、日期或影响范围。
- 输出优先使用稳定 Markdown 结构。
- 英文技术资料保留必要英文术语，中文表达要面向 Java 后端开发者。
- 内容过多时先按条目压缩，再做板块合并，避免一次请求过长。

## 后续路线

近期优先级：

1. 补充真实 RSS 源清单和运行配置。
2. 增加手动触发入口，方便本地或运维侧主动拉取。
3. 增加抓取超时、重试、User-Agent 和失败告警策略。
4. 基于 `ArticleQueryService` 实现 Markdown 简报生成。
5. 接入一个 AI Provider，完成板块摘要。
6. 实现邮件发送和简报归档。
7. 增加任务运行记录，沉淀抓取数、入库数、摘要状态和发送状态。

暂不做：

- 复杂 Web 管理后台。
- 多用户权限系统。
- 全文爬虫系统。
- 向量库和 RAG。
- 多模型复杂路由。
- 实时全量资讯监控。

## 项目记录

长期维护记录放在 `docs/records/`：

- `docs/records/rss-ingestion.md`：RSS 抓取、解析、去重和入库备忘。
- `docs/records/2026-07-04-rss-ingestion-scheduling.md`：RSS 定时入库和文章查询出口记录。

本文档只描述项目定位、当前状态和路线。具体实现细节以源码、测试、迁移脚本和 records 文档为准。
