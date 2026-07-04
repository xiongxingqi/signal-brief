# 代码上下文保存规范

本文档补充 [Java 编码规范](java-coding-standard.md)，重点回答一个问题：怎样让 SignalBrief 的代码保留足够业务语义，避免维护者只看到“值”和“流程”，却看不出这些值代表什么、规则从哪里来、边界如何定义。

## 核心原则

- 优先用命名、类型、枚举、record 和方法边界保存上下文。
- 注释用于说明原因、约束和取舍，不用于翻译代码。
- 规则要靠近所属概念，不能散落在多个 `if` 中各自演化。
- 同一业务概念在配置、代码、数据库、测试和文档中尽量使用同一套词。
- 如果一段代码需要大量注释才能读懂，优先重命名、拆方法或提取对象。

本项目当前最重要的业务词包括：RSS 源、文章、分类、去重、入库、简报候选文章、时间窗口、定时任务、AI 摘要和邮件推送。

## 上下文放在哪里

| 上下文类型 | 优先载体 | 本项目示例 |
| --- | --- | --- |
| 固定取值范围 | 枚举 | `ArticleCategory` |
| 配置结构 | `@ConfigurationProperties` record | `FeedProperties`、`IngestionProperties` |
| 外部输入归一化结果 | record | `FetchedArticle` |
| 数据库持久化对象 | record + Mapper 映射 | `Article`、`NewArticle` |
| 同一组查询边界 | 参数命名或 query 对象 | `startInclusive`、`endExclusive` |
| 编排结果 | 结果对象 | `FeedIngestionResult` |
| 历史原因和外部限制 | 注释、测试、records 文档 | RSS 编码、定时任务默认关闭 |

不要把所有上下文都塞进注释。能用类型表达的，用类型；能用方法名表达的，用方法名；只有代码结构表达不完整的原因和约束，才交给注释或文档。

## 命名要表达业务角色

反例：

```java
String name;
String url;
String type;
```

这些字段离开局部代码后，很难判断它们属于 RSS 源、文章、邮件还是模型配置。

推荐：

```java
public record FeedSource(
        String name,
        URI url,
        ArticleCategory category,
        boolean enabled
) {
}
```

在跨边界对象中，命名要保留来源和角色：

```java
public record FetchedArticle(
        String sourceName,
        URI sourceUrl,
        ArticleCategory category,
        String title,
        String url,
        String guid,
        Instant publishedAt,
        String summary
) {
}
```

`sourceUrl` 和 `url` 必须区分：前者是 feed 源地址，后者是文章原文地址。不要为了简短改成 `feed`、`link`、`dataUrl` 这类含义漂移的名字。

## 裸值要升级成概念

反例：

```java
if ("FRAMEWORK".equals(category)) {
    // ...
}
```

推荐：

```java
if (article.category() == ArticleCategory.FRAMEWORK) {
    // ...
}
```

文章分类、任务状态、邮件发送状态、AI Provider 类型等固定取值都应优先枚举化。数据库值、配置值和外部协议值可以在边界处转换，业务代码不要直接散落字符串或数字。

常量也要表达业务含义：

```java
private static final String DEFAULT_CRON = "0 0 6 1,16 * *";
```

这个常量表达的是“默认半月入库 cron”，不是一个普通字符串。若后续出现多个定时任务，常量名应继续补足所属任务语义，例如 `DEFAULT_RSS_INGESTION_CRON`。

## 参数要保存关系

多个同类型参数最容易丢上下文。时间窗口尤其要明确边界：

```java
List<Article> findBriefCandidates(Instant startInclusive, Instant endExclusive);
```

`startInclusive` 和 `endExclusive` 明确表达半开区间 `[startInclusive, endExclusive)`。不要改成 `start`、`end` 后再靠注释解释边界。

当参数继续增多时，应提取 query 或 command 对象：

```java
public record BriefCandidateQuery(
        Instant startInclusive,
        Instant endExclusive,
        List<ArticleCategory> categories
) {
}
```

命名建议：

- `Command`：一次写操作意图，例如后续 `GenerateBriefCommand`。
- `Query` / `Criteria`：查询条件，例如 `BriefCandidateQuery`。
- `Result`：编排结果，例如 `FeedIngestionResult`。
- `Properties`：配置绑定对象，例如 `FeedProperties`。
- `Context`：跨多个规则所需的运行上下文。

不要创建“什么都能塞”的大对象。如果一个对象同时承载 RSS 源、文章、邮件收件人和 AI 参数，说明边界已经混乱。

## 规则入口要稳定

去重规则不能散落在 Mapper、Service 和调度器中各写一遍。当前规则入口是 `ArticleDeduplicationService`，数据库唯一索引用于兜底。

推荐结构：

```text
ArticleDeduplicationService -> 判断 guid / url / contentHash
ArticleMapper               -> insertIfAbsent 数据库兜底
ArticleIngestionService     -> 编排一次文章入库
```

维护原则：

- 调整去重顺序时，同时检查应用层判断和数据库唯一约束。
- 调整文章有效时间口径时，同时检查 `ArticleQueryService`、`ArticleQueryMapper` 和集成测试。
- 调整分类规则时，优先扩展 `ArticleCategory` 或分类策略，不在调用处散落字符串判断。

规则入口的名字要能被搜索到。比起 `process()`，`ingestEnabledFeeds()`、`insertIfAbsent()`、`findBriefCandidates()` 更能保存维护上下文。

## 边界处先翻译成内部语言

系统边界包括：

- `application.yaml` 和环境变量。
- RSS / Atom XML。
- HTTP 响应。
- PostgreSQL 行数据。
- 未来的 AI API 响应。
- 未来的 SMTP 发送结果。

边界处应该尽快转换为内部对象：

```text
signal-brief.feeds -> FeedProperties.FeedSource
RSS / Atom item    -> FetchedArticle
article table row  -> Article
ingestion summary  -> FeedIngestionResult
```

外部字段名、状态码、XML 编码、HTML 内容和模型输出都不要直接在业务流程里长期传播。先归一化，再进入内部服务。

## 中间变量保存推理过程

复杂条件要让维护者看出判断意图：

```java
boolean hasPublishedAt = article.publishedAt() != null;
boolean isInWindow = !effectiveTime.isBefore(startInclusive)
        && effectiveTime.isBefore(endExclusive);
boolean isConfiguredCategory = categories.contains(article.category());

return hasPublishedAt && isInWindow && isConfiguredCategory;
```

适合提取中间变量的场景：

- 时间窗口判断。
- 去重 key 选择。
- 是否跳过某个 feed。
- 是否允许定时任务注册。
- AI 输出是否满足最小 Markdown 结构。

如果表达式本身已经清楚，不要机械拆出无意义变量。变量名要保存业务判断，而不是重复 API 行为。

## 注释保存原因和约束

适合注释的内容：

- XML 编码、BOM、RSS 兼容性等非直觉技术约束。
- 定时任务默认关闭的原因。
- 第三方 API、模型输出或邮件客户端的限制。
- 临时兼容方案的退出条件。
- 安全、事务、并发、资源生命周期上的特殊取舍。

推荐：

```java
// 保留原始字节流，让 XML 解析器根据 BOM 和 XML 声明判断编码。
InputStream fetch(FeedSource source);
```

不推荐：

```java
// 返回输入流
InputStream fetch(FeedSource source);
```

如果删除注释后不知道代码“做什么”，优先改代码；如果知道做什么但不知道“为什么必须这样”，注释才有价值。

## 测试也是上下文

测试名和 fixture 要保存业务规则。不要只写 `test1`、`success`、`failed`。

推荐命名方向：

- `returnsOnlyEnabledFeeds`
- `keepsCreatedAtAsFallbackWhenPublishedAtIsMissing`
- `continuesIngestionWhenOneSourceFails`
- `rejectsInvalidBriefCandidateWindow`

测试 fixture 放在 `src/test/resources`，命名表达来源和场景，例如 `fixtures/rss/spring-blog.xml`。fixture 是项目语义样本，不是随手生成的临时文件。

## 项目 Review 检查清单

- 新增字段名是否区分了 RSS 源、文章原文、简报、邮件或 AI Provider。
- 固定取值是否使用枚举，而不是字符串或数字。
- 多个同类型参数是否有清晰命名或参数对象。
- 时间窗口是否明确 inclusive / exclusive。
- 去重、分类、查询时间口径是否只有稳定入口。
- 外部输入是否已经转换成内部对象。
- 注释是否解释 why，而不是翻译 what。
- 测试名是否保存了业务条件、边界和预期。
- 日志是否包含源名称、文章标识、任务结果等最小定位上下文。
- records 文档是否只记录决策和维护备忘，不复制大段源码。

## 参考

- [Java 编码规范](java-coding-standard.md)
- [SignalBrief 项目说明](personal-tech-newsletter-system.md)
- [RSS 抓取入库备忘](records/rss-ingestion.md)
