# Markdown Brief Generation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 基于已入库文章生成确定性的中文 Markdown 技术半月报草稿。

**Architecture:** 新增独立 `brief` 包，`BriefGenerationService` 负责查询和编排，`BriefMarkdownRenderer` 负责纯 Markdown 渲染。第一版不接 AI、不发邮件、不持久化简报，只返回 `String`。

**Tech Stack:** Java 25, Spring Boot 4, JUnit 5, Apache Commons Lang `StringUtils`

**Execution policy:** 执行本计划时不要自动提交；只有用户明确要求提交时才运行 `git commit`。

---

## File Structure

- Create: `src/main/java/cn/name/celestrong/signalbrief/brief/BriefMarkdownRenderer.java`
  - 纯渲染组件，把时间窗口和文章列表转换成稳定 Markdown。
- Create: `src/main/java/cn/name/celestrong/signalbrief/brief/BriefGenerationService.java`
  - Spring Service，调用 `ArticleQueryService` 查询候选文章，再委托 renderer。
- Create: `src/test/java/cn/name/celestrong/signalbrief/brief/BriefMarkdownRendererTest.java`
  - 普通 JUnit 5 单元测试，不启动 Spring。
- Create: `src/test/java/cn/name/celestrong/signalbrief/brief/BriefGenerationServiceTest.java`
  - 使用手写 fake `ArticleQueryMapper` 验证查询委托和输出。
- Modify: `README.md`
  - 在当前能力和项目结构中补充 Markdown 简报生成。
- Modify: `docs/personal-tech-newsletter-system.md`
  - 同步当前阶段和模块边界。

## Task 1: Markdown Renderer

**Files:**
- Create: `src/test/java/cn/name/celestrong/signalbrief/brief/BriefMarkdownRendererTest.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/brief/BriefMarkdownRenderer.java`

- [ ] **Step 1: Write the failing renderer test**

Create `src/test/java/cn/name/celestrong/signalbrief/brief/BriefMarkdownRendererTest.java`:

```java
package cn.name.celestrong.signalbrief.brief;

import cn.name.celestrong.signalbrief.article.Article;
import cn.name.celestrong.signalbrief.article.ArticleCategory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BriefMarkdownRendererTest {

    private final BriefMarkdownRenderer renderer = new BriefMarkdownRenderer();

    @Test
    void rendersEmptyBrief() {
        String markdown = renderer.render(
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-16T00:00:00Z"),
                List.of()
        );

        assertEquals("""
                # SignalBrief 技术半月报

                时间范围：2026-07-01 00:00:00 UTC 至 2026-07-16 00:00:00 UTC

                本期暂无候选文章。
                """, markdown);
    }

    @Test
    void groupsArticlesByCategoryAndKeepsInputOrderWithinGroup() {
        List<Article> articles = List.of(
                article(1L, ArticleCategory.FRAMEWORK, "Spring Blog", "Spring Framework 7", "https://example.com/spring", Instant.parse("2026-07-03T10:15:30Z"), "Framework summary."),
                article(2L, ArticleCategory.FRAMEWORK, "Spring Blog", "Spring Boot 4", null, null, null),
                article(3L, ArticleCategory.JAVA, "Inside Java", "JDK 26", "https://example.com/jdk", Instant.parse("2026-07-04T00:00:00Z"), "JDK summary.")
        );

        String markdown = renderer.render(
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-16T00:00:00Z"),
                articles
        );

        assertEquals("""
                # SignalBrief 技术半月报

                时间范围：2026-07-01 00:00:00 UTC 至 2026-07-16 00:00:00 UTC

                ## FRAMEWORK

                ### Spring Blog: Spring Framework 7

                - 来源：Spring Blog
                - 发布时间：2026-07-03 10:15:30 UTC
                - 链接：https://example.com/spring

                Framework summary.

                ### Spring Blog: Spring Boot 4

                - 来源：Spring Blog
                - 发布时间：未知

                暂无摘要。

                ## JAVA

                ### Inside Java: JDK 26

                - 来源：Inside Java
                - 发布时间：2026-07-04 00:00:00 UTC
                - 链接：https://example.com/jdk

                JDK summary.

                """, markdown);
    }

    @Test
    void escapesMarkdownControlCharactersInTextFields() {
        Article article = article(
                1L,
                ArticleCategory.AI,
                "AI [Lab]",
                "# Model *Update*",
                "https://example.com/model",
                Instant.parse("2026-07-05T00:00:00Z"),
                "> use `RestClient` and Guava_List"
        );

        String markdown = renderer.render(
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-16T00:00:00Z"),
                List.of(article)
        );

        assertEquals("""
                # SignalBrief 技术半月报

                时间范围：2026-07-01 00:00:00 UTC 至 2026-07-16 00:00:00 UTC

                ## AI

                ### AI \\[Lab\\]: \\# Model \\*Update\\*

                - 来源：AI \\[Lab\\]
                - 发布时间：2026-07-05 00:00:00 UTC
                - 链接：https://example.com/model

                \\> use \\`RestClient\\` and Guava\\_List

                """, markdown);
    }

    private Article article(
            Long id,
            ArticleCategory category,
            String sourceName,
            String title,
            String url,
            Instant publishedAt,
            String summary
    ) {
        return new Article(
                id,
                sourceName,
                "https://example.com/feed.xml",
                category,
                title,
                url,
                "guid-" + id,
                publishedAt,
                summary,
                "hash-" + id,
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-01T00:00:00Z")
        );
    }
}
```

- [ ] **Step 2: Run renderer test to verify it fails**

Run:

```bash
./mvnw -o -Dspring.docker.compose.enabled=false -Dtest=BriefMarkdownRendererTest test
```

Expected: compilation fails because `BriefMarkdownRenderer` does not exist.

- [ ] **Step 3: Implement the renderer**

Create `src/main/java/cn/name/celestrong/signalbrief/brief/BriefMarkdownRenderer.java`:

```java
package cn.name.celestrong.signalbrief.brief;

import cn.name.celestrong.signalbrief.article.Article;
import cn.name.celestrong.signalbrief.article.ArticleCategory;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Markdown 简报渲染器。
 *
 * <p>渲染器保持无外部副作用，便于后续在 AI 摘要、邮件正文和归档流程中复用。</p>
 */
@Component
public class BriefMarkdownRenderer {

    private static final DateTimeFormatter UTC_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
            .withZone(ZoneOffset.UTC);

    public String render(Instant startInclusive, Instant endExclusive, List<Article> articles) {
        List<Article> safeArticles = articles == null ? List.of() : articles;
        StringBuilder markdown = new StringBuilder();
        markdown.append("# SignalBrief 技术半月报\n\n");
        markdown.append("时间范围：")
                .append(formatInstant(startInclusive))
                .append(" 至 ")
                .append(formatInstant(endExclusive))
                .append("\n\n");

        if (safeArticles.isEmpty()) {
            markdown.append("本期暂无候选文章。\n");
            return markdown.toString();
        }

        Map<ArticleCategory, List<Article>> articlesByCategory = safeArticles.stream()
                .collect(Collectors.groupingBy(Article::category, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<ArticleCategory, List<Article>> entry : articlesByCategory.entrySet()) {
            markdown.append("## ").append(entry.getKey()).append("\n\n");
            for (Article article : entry.getValue()) {
                appendArticle(markdown, article);
            }
        }
        return markdown.toString();
    }

    private void appendArticle(StringBuilder markdown, Article article) {
        String sourceName = displayText(article.sourceName(), "未知来源");
        String title = displayText(article.title(), "无标题");
        markdown.append("### ")
                .append(escapeMarkdown(sourceName))
                .append(": ")
                .append(escapeMarkdown(title))
                .append("\n\n");
        markdown.append("- 来源：").append(escapeMarkdown(sourceName)).append("\n");
        markdown.append("- 发布时间：").append(formatInstant(article.publishedAt())).append("\n");
        if (StringUtils.isNotBlank(article.url())) {
            markdown.append("- 链接：").append(article.url()).append("\n");
        }
        markdown.append("\n");
        markdown.append(escapeMarkdown(displayText(article.summary(), "暂无摘要。"))).append("\n\n");
    }

    private String formatInstant(Instant instant) {
        if (instant == null) {
            return "未知";
        }
        return UTC_FORMATTER.format(instant);
    }

    private String displayText(String text, String fallback) {
        if (StringUtils.isBlank(text)) {
            return fallback;
        }
        return text.strip();
    }

    private String escapeMarkdown(String text) {
        String escaped = text
                .replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("[", "\\[")
                .replace("]", "\\]");
        return escaped.lines()
                .map(this::escapeMarkdownLinePrefix)
                .collect(Collectors.joining("\n"));
    }

    private String escapeMarkdownLinePrefix(String line) {
        if (line.startsWith("#") || line.startsWith(">")) {
            return "\\" + line;
        }
        if (line.startsWith("- ")) {
            return "\\- " + line.substring(2);
        }
        return line;
    }
}
```

- [ ] **Step 4: Run renderer test to verify it passes**

Run:

```bash
./mvnw -o -Dspring.docker.compose.enabled=false -Dtest=BriefMarkdownRendererTest test
```

Expected: build succeeds and `BriefMarkdownRendererTest` passes.

## Task 2: Brief Generation Service

**Files:**
- Create: `src/test/java/cn/name/celestrong/signalbrief/brief/BriefGenerationServiceTest.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/brief/BriefGenerationService.java`

- [ ] **Step 1: Write the failing service test**

Create `src/test/java/cn/name/celestrong/signalbrief/brief/BriefGenerationServiceTest.java`:

```java
package cn.name.celestrong.signalbrief.brief;

import cn.name.celestrong.signalbrief.article.Article;
import cn.name.celestrong.signalbrief.article.ArticleCategory;
import cn.name.celestrong.signalbrief.article.ArticleQueryMapper;
import cn.name.celestrong.signalbrief.article.ArticleQueryService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BriefGenerationServiceTest {

    @Test
    void delegatesWindowToArticleQueryServiceAndRendersMarkdown() {
        Instant start = Instant.parse("2026-07-01T00:00:00Z");
        Instant end = Instant.parse("2026-07-16T00:00:00Z");
        RecordingArticleQueryMapper mapper = new RecordingArticleQueryMapper(List.of(article()));
        ArticleQueryService articleQueryService = new ArticleQueryService(mapper);
        BriefGenerationService service = new BriefGenerationService(articleQueryService, new BriefMarkdownRenderer());

        String markdown = service.generate(start, end);

        assertEquals(start, mapper.startInclusive);
        assertEquals(end, mapper.endExclusive);
        assertTrue(markdown.contains("# SignalBrief 技术半月报"));
        assertTrue(markdown.contains("## JAVA"));
        assertTrue(markdown.contains("### Inside Java: JDK 26"));
    }

    private Article article() {
        return new Article(
                1L,
                "Inside Java",
                "https://inside.java/feed.xml",
                ArticleCategory.JAVA,
                "JDK 26",
                "https://inside.java/articles/jdk-26",
                "inside-java-1",
                Instant.parse("2026-07-04T00:00:00Z"),
                "JDK summary.",
                "hash-1",
                Instant.parse("2026-07-04T00:00:00Z"),
                Instant.parse("2026-07-04T00:00:00Z")
        );
    }

    private static final class RecordingArticleQueryMapper implements ArticleQueryMapper {
        private final List<Article> articles;
        private Instant startInclusive;
        private Instant endExclusive;

        private RecordingArticleQueryMapper(List<Article> articles) {
            this.articles = articles;
        }

        @Override
        public List<Article> findBriefCandidates(Instant startInclusive, Instant endExclusive) {
            this.startInclusive = startInclusive;
            this.endExclusive = endExclusive;
            return articles;
        }
    }
}
```

- [ ] **Step 2: Run service test to verify it fails**

Run:

```bash
./mvnw -o -Dspring.docker.compose.enabled=false -Dtest=BriefGenerationServiceTest test
```

Expected: compilation fails because `BriefGenerationService` does not exist.

- [ ] **Step 3: Implement the service**

Create `src/main/java/cn/name/celestrong/signalbrief/brief/BriefGenerationService.java`:

```java
package cn.name.celestrong.signalbrief.brief;

import cn.name.celestrong.signalbrief.article.Article;
import cn.name.celestrong.signalbrief.article.ArticleQueryService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Markdown 简报生成服务。
 *
 * <p>第一版只编排候选文章查询和确定性 Markdown 渲染，不调用 AI、不发送邮件。</p>
 */
@Service
public class BriefGenerationService {

    private final ArticleQueryService articleQueryService;
    private final BriefMarkdownRenderer briefMarkdownRenderer;

    public BriefGenerationService(ArticleQueryService articleQueryService, BriefMarkdownRenderer briefMarkdownRenderer) {
        this.articleQueryService = articleQueryService;
        this.briefMarkdownRenderer = briefMarkdownRenderer;
    }

    public String generate(Instant startInclusive, Instant endExclusive) {
        List<Article> articles = articleQueryService.findBriefCandidates(startInclusive, endExclusive);
        return briefMarkdownRenderer.render(startInclusive, endExclusive, articles);
    }
}
```

- [ ] **Step 4: Run service test to verify it passes**

Run:

```bash
./mvnw -o -Dspring.docker.compose.enabled=false -Dtest=BriefGenerationServiceTest test
```

Expected: build succeeds and `BriefGenerationServiceTest` passes.

## Task 3: Documentation and Full Local Verification

**Files:**
- Modify: `README.md`
- Modify: `docs/personal-tech-newsletter-system.md`

- [ ] **Step 1: Update README current status**

In `README.md`, change the current status block near the top from:

```markdown
> 当前仓库处于项目初始化阶段，已完成 Spring Boot 工程骨架、基础配置、PostgreSQL Compose 配置和项目文档沉淀；核心业务模块仍在开发中。
```

to:

```markdown
> 当前仓库已完成 Spring Boot 工程骨架、RSS 抓取入库、定时采集、候选文章查询和 Markdown 简报草稿生成；AI 摘要、邮件推送和归档仍在开发中。
```

- [ ] **Step 2: Update README flow description**

In `README.md`, change the MVP flow from:

```text
RSS 抓取 -> 去重过滤 -> AI 分类汇总 -> Markdown 简报 -> 邮件推送
```

to:

```text
RSS 抓取 -> 去重过滤 -> Markdown 简报草稿 -> AI 分类汇总 -> 邮件推送
```

In the “项目结构” code block, keep the top-level layout unchanged because it lists only major directories, not Java packages.

- [ ] **Step 3: Update project overview**

In `docs/personal-tech-newsletter-system.md`, add this item to the “当前代码已具备” list:

```markdown
- 基于候选文章生成确定性的 Markdown 简报草稿。
```

In the “模块边界” section, add:

```markdown
- `brief`：基于候选文章生成 Markdown 简报草稿，后续可作为 AI 摘要和邮件发送输入。
```

In the “AI 摘要、Markdown 简报生成、邮件推送、归档表和手动触发入口仍属于后续阶段。” sentence, remove “Markdown 简报生成、” so it becomes:

```markdown
AI 摘要、邮件推送、归档表和手动触发入口仍属于后续阶段。
```

- [ ] **Step 4: Run focused brief tests**

Run:

```bash
./mvnw -o -Dspring.docker.compose.enabled=false -Dtest=BriefMarkdownRendererTest,BriefGenerationServiceTest test
```

Expected: build succeeds and both brief tests pass.

- [ ] **Step 5: Run local baseline tests**

Run:

```bash
./mvnw -o -Dspring.docker.compose.enabled=false test
```

Expected: build succeeds. A logged `feed unavailable` exception from existing feed ingestion tests is acceptable if Maven reports zero failures and zero errors.

- [ ] **Step 6: Check working tree**

Run:

```bash
git status --short --branch
```

Expected: only the brief implementation, brief tests, README, project overview, spec and plan files are changed.
