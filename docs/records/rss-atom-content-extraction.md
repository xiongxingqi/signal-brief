# RSS / Atom 内容提取增强记录

> 本文档记录 RSS / Atom 条目短摘要、正文片段提取和历史数据兼容策略。RSS 入库总览见 [RSS 抓取入库记录](rss-ingestion.md)，源质量策略见 [RSS 源清单与抓取可靠性记录](rss-source-reliability.md)。实现细节以源码、迁移脚本和测试为准。

## 背景

早期入库只读取 ROME `SyndEntry.description` 写入 `article.summary`。真实试运行中，部分源把有效内容放在 Atom `content` 或 RSS `content:encoded`，导致简报草稿出现较多“暂无摘要”，AI 摘要也缺少可用上下文。

本阶段目标是在不引入网页全文抓取、不改变 AI prompt 和邮件归档流程的前提下，充分利用 feed 中已经存在的内容。

## 组件边界

- `content/HtmlContentCleaner`：通用 HTML 片段清洗组件，只负责把 HTML 或纯文本片段转为纯文本；不依赖 feed、ROME、RSS 或 Atom 类型。
- `feed/FeedEntryContentExtractor`：负责从 ROME `SyndEntry` 中选择短摘要和正文候选字段，并调用清洗组件。
- `feed/RomeFeedParser`：继续负责 XML 解析和 `FetchedArticle` 构建，仍把原始 `InputStream` 交给 ROME `XmlReader`，避免破坏 BOM 或 XML 声明里的编码识别。

字段语义保持分离：`summary` 表示短摘要，继续服务简报展示；`content_text` 表示清洗后的正文片段，主要服务后续 AI 输入增强。

## 提取策略

短摘要优先来自 `entry.getDescription()`，覆盖 RSS `description` 和 Atom `summary` 的常规路径。

正文片段优先来自 `entry.getContents()` 中第一个非空内容，覆盖 Atom `content` 和 ROME 能映射到 synd contents 的扩展内容。若 RSS `content:encoded` 没有进入 `getContents()`，通过 `entry.getForeignMarkup()` 兜底读取 `http://purl.org/rss/1.0/modules/content/` 命名空间下的 `encoded` 元素。

当短摘要为空且正文存在时，正文可降级作为 `summary`，同时仍写入 `content_text`。清洗后为空的内容视为无效，不入库为有效摘要或正文。

## 数据与兼容

`V4__add_article_content_text.sql` 为 `article` 增加可空 `content_text TEXT`，并写入数据库 comment。旧数据不批量回填，因为旧表只有 `summary`，无法可靠推导真实正文；强行复制会混淆字段语义。

重复文章重新抓取时只补齐空字段：

- 已有 `content_text` 为空且本次抓到正文，则补齐 `content_text`。
- 已有 `summary` 为空且本次抓到短摘要，则补齐 `summary`。
- 不覆盖已有非空内容，避免 feed 后续截断、改短或异常内容污染历史数据。
- 补齐时按 `sourceName + guid`、`url`、`contentHash` 依次尝试，只有更新行数大于 0 才停止，避免实际重复键和本次输入优先级不一致时漏补。

`content_text` 不参与现有 `content_hash` 计算，避免改变历史去重口径。兼容期代码用 `COMPATIBILITY` 注释标记历史文章可能没有 `contentText`；移除降级路径需要单独规划，并先确认目标时间窗口内缺失比例可接受。

## 验证结论

已覆盖：

- HTML 清洗、脚本样式剔除、纯文本稳定和空内容行为。
- RSS `description`、Atom `summary`、Atom `content`、RSS `content:encoded` fixture。
- `summary` 和 `contentText` 从 parser 到入库模型、Mapper 写入和查询模型的贯通。
- 重复文章补齐、并发唯一键冲突后补齐、guid 补齐无更新时回退 URL、无 guid/url 时回退 contentHash。

本地基础验证命令：

```bash
./mvnw -Dspring.docker.compose.enabled=false test
git diff --check
```

`./mvnw verify` 依赖可用 PostgreSQL datasource；本地未配置 datasource 时会在 Failsafe 的 Spring context 初始化阶段失败，需交由 CI 或配置好本地数据库后验证 Mapper IT。

## 后续方向

- 将 `content_text` 纳入后续 AI 摘要输入增强，优先使用正文片段，缺失时降级到摘要、标题和链接。
- 新增 RSS 源时用 fixture 或实际响应确认字段位置，包括 RSS `description`、RSS `content:encoded`、Atom `summary` 和 Atom `content`。
- 若 feed 正文片段仍过短，再单独规划文章详情页正文抓取、清洗和截断入库，不与本阶段 feed 内容提取混在一起。
