# Markdown 简报生成记录

> 本文档记录 Markdown 简报草稿生成的当前设计。实现细节以源码和测试为准。

## 定位

Markdown 简报生成是 RSS 入库之后的第一段内容出口，目标是把本地候选文章转换为结构稳定、可人工审阅、可继续交给 AI 摘要和邮件发送的 Markdown 草稿。

当前已经覆盖：候选文章查询编排、分类分组、Markdown 渲染、空值降级、基础 Markdown 转义和内部 API 手动生成。

当前暂不覆盖：AI 摘要、邮件发送、简报归档和多格式输出。

## 当前模块

- `brief/BriefGenerationService`：简报生成入口，接收半开时间窗口，调用 `ArticleQueryService.findBriefCandidates(startInclusive, endExclusive)`，再委托渲染器输出 Markdown。
- `brief/BriefMarkdownRenderer`：纯 Markdown 渲染组件，不访问数据库、不调用外部服务、不产生持久化副作用。
- `internal/ManualTriggerController`：在内部 API 开启后，按指定时间窗口调用 `BriefGenerationService` 生成 Markdown 草稿。
- `internal/OpenApiConfiguration`：在 OpenAPI 开启后提供 internal 分组和接口元信息。

`BriefGenerationService` 不重复实现时间窗口校验，窗口合法性继续由 `ArticleQueryService` 兜底，避免查询口径分叉。

## 输出结构

Markdown 使用固定结构：

```markdown
# SignalBrief 技术半月报

时间范围：2026-07-01 00:00:00 UTC 至 2026-07-16 00:00:00 UTC

## JAVA

### InfoQ: Java 25 发布
- 来源：InfoQ
- 发布时间：2026-07-02 09:15:30 UTC
- 链接：https://example.com/java-25

新版本发布摘要。
```

规则：

- 时间统一按 UTC 输出，格式为 `yyyy-MM-dd HH:mm:ss UTC`。
- 没有候选文章时输出“本期暂无候选文章。”。
- 按 `ArticleCategory` 分组，分类顺序以文章列表中首次出现的顺序为准，组内保持原始顺序。
- 链接为空白时不输出链接行。
- 发布时间为空时显示“未知”。
- 摘要为空白时显示“暂无摘要。”。

## Markdown 转义

RSS 标题、来源和摘要都视为外部输入。渲染时会对这些文本做最小转义，避免破坏简报结构：

- 转义行内控制字符：反斜杠、反引号、星号、下划线、方括号。
- 转义行首块级语法：标题、引用、无序列表、有序列表和分隔线。
- 支持 Markdown 允许的 0 到 3 个前导空格，例如 `  > quote`、`  - item`。
- URL 保持原样输出，不做 Markdown 转义。

元数据列表和摘要之间保留空行，避免 CommonMark 把摘要解析为上一条列表项的延续内容。

## 测试与验证

当前测试覆盖：

- `BriefMarkdownRendererTest`：空简报、多分类分组、空链接、空发布时间、空摘要、Markdown 控制字符和行首块级语法转义。
- `BriefGenerationServiceTest`：验证时间窗口委托给 `ArticleQueryService`，并使用真实 `BriefMarkdownRenderer` 生成 Markdown。
- `ManualTriggerControllerTest`：验证内部 API 能按请求窗口生成 Markdown，并将非法请求映射为 HTTP 错误响应。

本地基础验证：

```bash
./mvnw -o -Dspring.docker.compose.enabled=false test
```

## 维护约束

- 不要把查询、AI、邮件或归档逻辑塞进 `BriefMarkdownRenderer`。
- 调整简报候选查询规则时，应优先修改 `ArticleQueryService` / `ArticleQueryMapper`，保持 `brief` 模块只消费查询结果。
- 修改 Markdown 格式时，同步更新完整字符串断言，避免输出结构被无意改变。
- 接入 AI 时，优先让 AI 基于确定性 Markdown 草稿做摘要、压缩或润色，不让模型接管查询、分组和来源追溯。
- 增加归档能力时，再引入简报文档对象或简报表，不提前扩大当前 `String` 返回模型。

## 后续事项

- 基于 Markdown 草稿接入 AI 摘要和压缩。
- 复用 Markdown 输出作为邮件正文或邮件模板输入。
- 增加简报归档，保存时间窗口、Markdown 内容、生成状态和发送状态。
