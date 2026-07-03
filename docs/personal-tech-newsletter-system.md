# SignalBrief 立项文档

**项目名称**：SignalBrief

**中文描述**：Java 后端技术与行业资讯半月报系统

**项目定位**：个人资讯聚合与 AI 摘要工具，通过官方源聚合和 AI 摘要生成中文技术半月报

**立项人**：celestrong

**开发周期**：2 周，MVP 目标 1 周跑通

**文档版本**：V1.0

**最后更新**：2026-07-02

## 一、为什么做这个项目

Java 后端开发者每天会接触大量碎片化信息：JDK 更新、Spring 生态公告、中间件版本发布、安全漏洞、云厂商动态、政策文件、招聘与职场讨论。信息源分散在官方博客、GitHub、RSS、公众号、自媒体和社区里，人工筛选成本高，而且容易被信息流算法带偏。

`SignalBrief` 要解决的问题不是“抓取越多越好”，而是建立一套可自动运行的个人信息管道：

```text
官方源 / 一手源 -> 抓取 -> 去重 -> 分类 -> AI 摘要 -> 邮件推送 -> 归档
```

名字里的 `Signal` 表示从噪声里提取有效信号，`Brief` 表示最终输出的是短而清楚的简报。半月报的目标读者就是自己。每 15 天读一份结构清楚、来源可追溯、重点明确的简报，替代零散刷资讯。

## 二、项目目标

MVP 目标是跑通一条最小可用链路：

```text
RSS 抓取 -> 去重过滤 -> AI 分类汇总 -> Markdown 简报 -> 邮件推送
```

第一版只追求稳定可运行，不追求复杂后台和可视化配置。长期目标是在稳定运行后扩展到漏洞告警、历史归档、检索和多渠道推送。

## 三、信息源范围

第一阶段优先选择官方 RSS、官方博客、官方公告和稳定文档源。RSS 是首选，因为 RSS 2.0 本身就是面向站点更新订阅的 XML 格式，适合定时聚合；没有 RSS 的站点再考虑用网页解析兜底。

MVP 先覆盖四类：

| 分类 | 内容范围 | 典型来源 |
| --- | --- | --- |
| Java 核心技术 | JDK、JEP、JVM、语言特性 | OpenJDK、Oracle Java、Inside Java |
| 框架与中间件 | Spring、Redis、MySQL、消息队列、云原生组件 | 官方博客、Release Notes、安全公告 |
| 行业政策与平台动态 | 云厂商、监管政策、开源生态、AI 工具 | 官方公告、技术博客 |
| 职场与工程实践 | 招聘趋势、工程效率、团队协作、AI Coding | 官方博客、可信社区精选 |

需要注意，技术源会大量包含英文内容。系统的目标不是逐句翻译英文原文，而是读懂技术逻辑后输出中文简报：

```text
英文技术资料 -> 理解核心变化 -> 提炼开发者影响 -> 用中文结构化表达
```

第一批信息源先选官方入口，不追求数量：

| 分类 | 官方源候选 | 处理方式 |
| --- | --- | --- |
| JDK / Java 语言 | OpenJDK JEP Index、Inside Java | JEP Index 适合周期性扫描；Inside Java 可作为 Java 团队动态源 |
| Spring 生态 | Spring Blog、Spring Release Calendar、Spring Security Advisories | 优先抓 Releases 和 Engineering 类内容 |
| Redis | Redis 官方文档与 Release Notes | 第一版先抓版本说明和安全相关更新 |
| MySQL | MySQL 官方 Release Notes | 按当前主线版本和 LTS 版本关注变更 |
| AI 工具与模型平台 | 阿里云百炼、火山方舟、DeepSeek、OpenAI 官方文档 | 只抓模型、价格、API 兼容性、限流和下线公告 |

这些源在开发前需要逐个验证是否提供 RSS / Atom。没有 feed 的官方页面，先作为人工候选源记录；第二阶段再用 jsoup 做低频页面解析。

## 四、MVP 功能范围

| 模块 | MVP 功能 | 边界 |
| --- | --- | --- |
| 配置管理 | 在 `application.yml` 配置 RSS 源、分类、邮箱、模型 Provider | 第一版不做 Web 配置页 |
| 资讯抓取 | 批量解析 RSS / Atom，提取标题、发布时间、摘要、链接 | 无 RSS 的站点暂不强行爬取全文 |
| 去重过滤 | 按 canonical link / guid / 标题哈希去重，只保留近 15 天内容 | 不做复杂语义去重 |
| 分类归档 | 按源配置和关键词规则归入固定板块 | AI 分类作为辅助，不作为唯一依据 |
| AI 摘要 | 按板块生成 Markdown 简报 | 不让模型编造来源，不输出无原文链接的结论 |
| 定时调度 | 每月 1 日、16 日自动执行；支持手动触发 | 第一版用单实例调度 |
| 邮件推送 | Markdown 转 HTML 正文，附带 `.md` 源文件 | 不做复杂邮件模板系统 |
| 执行日志 | 记录抓取数、入库数、摘要状态、发送状态 | 第一版只保留必要日志和数据库记录 |

第二阶段再考虑：

- 高危漏洞实时告警，不并入半月报。
- 历史简报归档查询和导出。
- 企业微信、飞书等多渠道推送。
- 可视化管理 RSS 源、分类规则和收件人。
- 更细的黑白名单、重要程度评分和人工收藏。

## 五、技术选型

| 模块 | 选型 | 依据 |
| --- | --- | --- |
| 主体框架 | Spring Boot 4.x | 个人 Java 后端项目最快落地，配置、邮件、调度、数据访问生态完整；当前 Spring Boot 4.1.0 文档已经进入稳定版本线 |
| JDK | JDK 25 LTS | 截至 2026-07，Java SE 25 是最新 LTS；Spring Boot 4.1.0 至少要求 Java 17，并兼容到 Java 26 |
| 定时任务 | Spring `@Scheduled` | 半月报是低频单实例任务，不需要先上 Quartz |
| RSS / Atom 解析 | ROME | ROME 是 Java RSS / Atom feed 解析与生成库，适合标准 feed 处理 |
| HTML 兜底解析 | jsoup | jsoup 提供 URL 获取、HTML 解析、DOM / CSS Selector 提取能力，适合少量无 RSS 页面兜底 |
| 数据存储 | PostgreSQL | 适合保存信息源、文章、简报归档和执行日志；后续做检索、JSONB 元数据、全文搜索和多人部署时不用过早迁移 |
| 邮件推送 | Spring Boot Mail + SMTP | Spring Boot 对 `JavaMailSender` 有自动配置支持，适合 HTML 正文和附件发送 |
| AI 接入 | Provider 抽象 + 可配置模型 | 模型变化快，业务代码不能绑定具体厂商和模型 ID |

PostgreSQL 比 SQLite 多一个独立服务，MVP 的部署成本会高一点，但换来的是更清晰的数据库能力边界。SignalBrief 虽然第一版是个人项目，但它天然会沉淀文章、来源、简报、运行日志和后续检索数据，直接使用 PostgreSQL 更利于长期演进。

## 六、AI 模型选型原则

模型选型不要写死成某一个历史版本。大模型的版本、价格、上下文窗口和限流策略变化很快，工程上更重要的是留出可替换能力。

本项目的模型任务是：

```text
中英文资讯理解
技术重点提炼
中文结构化写作
Markdown 格式稳定输出
```

优先级如下：

```text
英文技术理解 > 中文专业表达 > 结构化输出稳定性 > 国内调用稳定性 > 成本
```

推荐 MVP 采用一个国内直连模型 Provider 起步，先不要做多模型分流。当前更稳的策略是：

| 角色 | 建议 |
| --- | --- |
| 默认 Provider | 阿里云百炼 / 通义千问，或火山方舟 / 豆包 |
| 备选 Provider | DeepSeek API |
| 海外备用 | OpenAI API，仅在网络、额度和合规条件稳定时使用 |
| 代码设计 | `ai.provider`、`ai.model`、`ai.base-url`、`ai.api-key` 全部配置化 |

对中英文混合的 Java 技术简报，不建议简单地说“中文资料用豆包，英文资料用海外模型”。自动定时任务首先要求稳定可用，国内直连模型的综合成本、延迟和可维护性更适合个人项目。等系统跑过 2-3 期后，再根据实际输出质量决定是否做分类分流：

```text
英文官方技术文档 -> 偏技术理解能力强的模型
中文政策 / 行业资讯 -> 偏中文表达自然的模型
```

## 七、英文技术资讯摘要 Prompt 模板

MVP 可以先使用一个固定 Prompt。重点是让模型“直接理解后摘要”，不要先全文翻译。

```text
你是资深 Java 后端技术专家和技术编辑。

下面是来自官方或一手来源的技术资讯列表，可能包含中文和英文。请直接理解原文内容，生成一份中文 Markdown 简报。

要求：
1. 不要逐句翻译原文，要提炼「核心变化 + 对 Java 后端开发者的实际影响」。
2. 技术术语使用国内业界通用译法；拿不准的术语保留英文原文。
3. 每条资讯必须保留原文链接。
4. 不要编造原文没有的信息。
5. 同类重复资讯合并表达。
6. 输出只使用 Markdown，不要输出解释性套话。

输出格式：

## 板块名称

### 1. 标题

- 来源：
- 时间：
- 原文：
- 摘要：
- 影响：
- 建议：

待处理资讯：
{{items}}
```

如果某个板块内容过多，先让模型按条目做压缩，再做二次合并，避免一次请求上下文过长。

## 八、核心流程设计

单次任务流程：

```text
读取配置
-> 拉取 RSS / Atom
-> 标准化文章字段
-> 去重与时间过滤
-> 按分类聚合
-> 生成 Prompt
-> 调用 AI
-> 校验 Markdown
-> 发送邮件
-> 写入归档和执行日志
```

核心数据对象：

```text
FeedSource
  id
  name
  url
  category
  enabled

Article
  id
  source_id
  title
  url
  guid
  published_at
  summary
  content_hash
  category
  created_at

Brief
  id
  period_start
  period_end
  title
  markdown
  sent_at
  status

JobRun
  id
  started_at
  finished_at
  fetched_count
  new_count
  status
  error_message
```

去重策略：

```text
优先 guid
其次 canonical url
最后 title + source + published_at 哈希
```

分类策略：

```text
源级默认分类优先
关键词规则修正
AI 分类只作为补充
```

这样可以减少模型误分导致的不可控问题。

## 九、实施计划

第一周目标是手动跑通 MVP：

1. 初始化 Spring Boot 项目。
2. 配置 5-10 个稳定 RSS 源。
3. 用 ROME 拉取并解析文章。
4. 写入 PostgreSQL，完成去重和 15 天过滤。
5. 接入一个 AI Provider，生成 Markdown。
6. 使用 SMTP 发送测试邮件。
7. 提供一个命令行或 HTTP 接口手动触发。

第二周目标是自动化和稳定性：

1. 增加 `@Scheduled` 半月定时任务。
2. 增加执行日志和失败重试。
3. 增加 Markdown 基础格式校验。
4. 增加模型调用超时、重试和降级。
5. 部署到本地常驻环境或轻量服务器。
6. 跑一次完整半月报演练，修正源和 Prompt。

## 十、风险与应对

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| RSS 源失效 | 某些分类缺内容 | 源配置支持启停；执行日志记录失败源 |
| RSS 内容过短 | 摘要信息不足 | 第二阶段再用 jsoup 补抓详情页 |
| 英文技术内容理解偏差 | 简报质量下降 | Prompt 要求保留英文术语；关键条目保留原文链接 |
| 模型输出格式不稳定 | 邮件展示混乱 | 增加 Markdown 校验；失败时重试或降级为原始链接列表 |
| 模型 API 变化 | 定时任务失败 | Provider 抽象，模型 ID 和 base URL 配置化 |
| 邮件发送失败 | 收不到简报 | SMTP 超时配置、发送重试、执行日志保留附件路径 |
| PostgreSQL 运维成本 | 比 SQLite 多一个本地服务 | MVP 用 Docker Compose 或本机服务启动；从第一版开始保留迁移脚本和备份意识 |

## 十一、MVP 不做什么

第一版明确不做：

- 不做复杂 Web 管理后台。
- 不做全文爬虫系统。
- 不做多用户权限系统。
- 不做向量库和 RAG。
- 不做复杂模型路由。
- 不做实时全量资讯监控。

这些能力不是没有价值，而是会拖慢 MVP。先跑通稳定半月报，再按真实使用痛点迭代。

## 十二、验收标准

MVP 验收只看这几件事：

- 手动触发一次任务，能抓取配置内 RSS 源。
- 能过滤重复内容和 15 天之外内容。
- 能生成包含 4 个板块的 Markdown 简报。
- 每条重要资讯有来源链接。
- 邮件正文可读，并附带 `.md` 文件。
- 执行日志能看出成功、失败和失败原因。
- 定时任务能按每月 1 日、16 日触发。

## 十三、官方依据

本方案涉及的技术选型以这些官方或项目文档为依据：

- Spring Boot Email：<https://docs.spring.io/spring-boot/reference/io/email.html>
- Spring Boot System Requirements：<https://docs.spring.io/spring-boot/system-requirements.html>
- Spring Boot SQL Databases：<https://docs.spring.io/spring-boot/reference/data/sql.html>
- Spring Framework Scheduling：<https://docs.spring.io/spring-framework/reference/integration/scheduling.html>
- Spring AI：<https://docs.spring.io/spring-ai/reference/>
- ROME：<https://github.com/rometools/rome>
- jsoup：<https://jsoup.org/>
- Oracle Java SE Support Roadmap：<https://www.oracle.com/java/technologies/java-se-support-roadmap.html>
- OpenJDK JDK 25：<https://jdk.java.net/25/>
- PostgreSQL About：<https://www.postgresql.org/about/>
- RSS 2.0 Specification：<https://www.rssboard.org/rss-specification>
- OpenJDK JEP Index：<https://openjdk.org/jeps/0>
- Inside Java：<https://inside.java/>
- Spring Blog：<https://spring.io/blog/>
- Redis Release Notes：<https://redis.io/docs/latest/operate/oss_and_stack/stack-with-enterprise/release-notes/>
- MySQL 8.4 Release Notes：<https://dev.mysql.com/doc/relnotes/mysql/8.4/en/>
- 阿里云百炼模型文档：<https://help.aliyun.com/zh/model-studio/models>
- 火山方舟文档：<https://www.volcengine.com/docs/82379>
- DeepSeek Models & Pricing：<https://api-docs.deepseek.com/quick_start/pricing>
- OpenAI API Pricing：<https://developers.openai.com/api/docs/pricing>
