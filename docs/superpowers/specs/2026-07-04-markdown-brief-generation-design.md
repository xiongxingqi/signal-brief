# Markdown 简报生成 MVP 设计

## 背景

SignalBrief 已完成 RSS / Atom 抓取、去重入库、定时触发和候选文章查询。下一步需要把本地文章数据转换为可审阅的中文 Markdown 简报草稿，为后续 AI 摘要、邮件发送和归档提供稳定输入。

本阶段优先做规则化 Markdown 生成，不接入 AI、不发送邮件、不新增归档表，避免在核心数据出口尚未稳定时引入外部依赖。

## 目标

- 基于 `ArticleQueryService.findBriefCandidates(startInclusive, endExclusive)` 查询指定时间窗口内的文章。
- 按 `ArticleCategory` 分组，生成结构稳定、来源可追溯的 Markdown 文档。
- 输出可直接人工审阅，也可作为后续 AI 压缩和邮件发送的输入。
- 覆盖无文章、多分类、排序稳定和 Markdown 转义等核心测试。

## 非目标

- 不调用 AI Provider。
- 不发送邮件。
- 不把简报保存到数据库或文件系统。
- 不新增 Web 管理后台。
- 不改变 RSS 入库、去重和查询规则。

## 方案取舍

推荐方案：新增独立 `brief` 模块，先生成确定性的 Markdown 草稿。它复用现有查询出口，边界清晰，测试成本低，后续接 AI 时也能把模型限制在“润色和压缩”层面。

备选方案一是直接在 `ArticleQueryService` 中拼接 Markdown。实现最少，但会把查询和展示格式耦合在一起，后续接 AI、归档或多格式输出时容易扩散。

备选方案二是直接引入 AI 生成完整简报。产品效果看起来更快，但会把提示词、模型异常、费用和内容可追溯性问题提前拉进来，不适合作为当前 MVP 起点。

## 模块设计

新增包 `cn.name.celestrong.signalbrief.brief`：

- `BriefGenerationService`：简报生成入口，接收时间窗口，调用 `ArticleQueryService`，返回 Markdown 字符串。
- `BriefMarkdownRenderer`：只负责把文章集合渲染为 Markdown，保持无外部副作用。

第一版固定让 `BriefGenerationService` 返回 `String`，不引入 `BriefDocument`。如果后续需要归档元数据、生成状态或多格式输出，再增加独立文档对象。

## 输出格式

Markdown 使用固定结构：

```markdown
# SignalBrief 技术半月报

时间范围：2026-07-01 00:00:00 UTC 至 2026-07-16 00:00:00 UTC

## FRAMEWORK

### Spring Blog: 示例标题

- 来源：Spring Blog
- 发布时间：2026-07-02 10:00:00 UTC
- 链接：https://example.com/article

摘要内容。
```

规则：

- 分类顺序沿用查询结果顺序；当前 Mapper 已按 `category`、有效时间和 `id` 排序。
- 无文章时输出标题、时间范围和“本期暂无候选文章。”。
- 标题、来源和摘要中的 Markdown 特殊字符需要做最小转义，避免破坏结构。
- 链接为空时不输出链接行。
- 发布时间为空时显示“未知”。

## 数据流

```text
调用方
-> BriefGenerationService.generate(startInclusive, endExclusive)
-> ArticleQueryService.findBriefCandidates(...)
-> BriefMarkdownRenderer.render(...)
-> Markdown 字符串
```

时间窗口校验继续交给 `ArticleQueryService` 兜底。`BriefGenerationService` 不重复实现一套边界规则，只负责把参数传递给现有查询出口。

## 错误处理

- 查询参数非法时沿用 `ArticleQueryService` 的 `IllegalArgumentException`。
- Markdown 渲染不访问外部资源，不吞异常。
- 单篇文章缺失摘要、链接或发布时间时降级输出，不影响整份简报。

## 测试策略

- `BriefMarkdownRendererTest`：覆盖空文章、多分类、多文章、空链接、空发布时间和 Markdown 转义。
- `BriefGenerationServiceTest`：使用轻量 fake `ArticleQueryMapper` 构造 `ArticleQueryService`，验证时间窗口委托和输出结果。
- 暂不新增数据库集成测试；本功能只消费已有查询出口，数据库行为已有 `ArticleQueryMapperIT` 覆盖。

## 后续扩展

- 接入 AI Provider 时，在 Markdown 草稿之后增加摘要压缩或板块点评步骤。
- 增加邮件发送时，复用 Markdown 输出作为邮件正文或模板输入。
- 增加归档时，把时间窗口、Markdown 内容、生成状态和发送状态写入独立简报表。
