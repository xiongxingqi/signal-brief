# RSS Ingestion Scheduling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有 RSS 入库服务接入可配置定时任务，并提供后续简报生成使用的文章时间窗口查询出口。

**Architecture:** 保持现有 `FeedIngestionService` 作为 RSS 抓取入库编排核心，新增一个很薄的 scheduler 负责触发。文章读取侧新增独立查询 mapper 和 query service，避免扩大现有 `ArticleMapper` 写入/去重接口。

**Tech Stack:** Java 25, Spring Boot 4, Spring Scheduling, Spring Boot Configuration Properties, MyBatis, JUnit 5, Spring Boot Test.

---

## Implementation Notes

- 不自动提交代码；完成实现和验证后等待用户明确要求再提交。
- 本地默认只跑基础测试：`./mvnw -o -Dspring.docker.compose.enabled=false test`。
- 数据库集成测试继续由 CI 的 `./mvnw -B verify` 覆盖。
- 不新增真实外部 RSS 网络测试。

## Task 1: Add RSS Ingestion Runtime Configuration

**Files:**
- Create: `src/main/java/cn/name/celestrong/signalbrief/config/IngestionProperties.java`
- Modify: `src/main/resources/application.yaml`
- Test: `src/test/java/cn/name/celestrong/signalbrief/config/IngestionPropertiesTest.java`

- [ ] **Step 1: Write `IngestionPropertiesTest` first**

Create unit tests for default values and explicit binding:

```java
package cn.name.celestrong.signalbrief.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngestionPropertiesTest {

    @Test
    void defaultsDisableScheduledIngestion() {
        IngestionProperties properties = new IngestionProperties(false, null);

        assertFalse(properties.enabled());
        assertEquals("0 0 6 1,16 * *", properties.cron());
    }

    @Test
    void bindsExplicitConfiguration() throws Exception {
        String yaml = """
                signal-brief:
                  ingestion:
                    enabled: true
                    cron: "0 30 7 * * *"
                """;

        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.addFirst(new YamlPropertySourceLoader().load(
                "test",
                new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8))
        ).getFirst());

        IngestionProperties properties = Binder.get(environment)
                .bind("signal-brief.ingestion", Bindable.of(IngestionProperties.class))
                .orElseThrow();

        assertTrue(properties.enabled());
        assertEquals("0 30 7 * * *", properties.cron());
    }
}
```

Run:

```bash
./mvnw -o -Dspring.docker.compose.enabled=false -Dtest=IngestionPropertiesTest test
```

Expected: fail because `IngestionProperties` does not exist.

- [ ] **Step 2: Add `IngestionProperties`**

Implement a dedicated configuration record:

```java
package cn.name.celestrong.signalbrief.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "signal-brief.ingestion")
public record IngestionProperties(
        boolean enabled,
        String cron
) {
    private static final String DEFAULT_CRON = "0 0 6 1,16 * *";

    public IngestionProperties {
        cron = cron == null || cron.isBlank() ? DEFAULT_CRON : cron;
    }
}
```

- [ ] **Step 3: Add default config**

Update `src/main/resources/application.yaml`:

```yaml
signal-brief:
  ingestion:
    enabled: ${SIGNAL_BRIEF_INGESTION_ENABLED:false}
    cron: "${SIGNAL_BRIEF_INGESTION_CRON:0 0 6 1,16 * *}"
  feeds: []
```

- [ ] **Step 4: Run focused config test**

Run:

```bash
./mvnw -o -Dspring.docker.compose.enabled=false -Dtest=IngestionPropertiesTest test
```

Expected: pass.

## Task 2: Add Conditional RSS Ingestion Scheduler

**Files:**
- Create: `src/main/java/cn/name/celestrong/signalbrief/ingestion/FeedIngestionScheduler.java`
- Test: `src/test/java/cn/name/celestrong/signalbrief/ingestion/FeedIngestionSchedulerTest.java`

- [ ] **Step 1: Write scheduler test**

Create a focused Spring context test:

```java
package cn.name.celestrong.signalbrief.ingestion;

import cn.name.celestrong.signalbrief.config.FeedProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedIngestionSchedulerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(FeedIngestionScheduler.class, TestConfiguration.class);

    @Test
    void doesNotRegisterSchedulerWhenPropertyIsMissing() {
        contextRunner.run(context -> assertFalse(context.containsBean("feedIngestionScheduler")));
    }

    @Test
    void doesNotRegisterSchedulerWhenDisabled() {
        contextRunner
                .withPropertyValues("signal-brief.ingestion.enabled=false")
                .run(context -> assertFalse(context.containsBean("feedIngestionScheduler")));
    }

    @Test
    void registersSchedulerWhenEnabled() {
        contextRunner
                .withPropertyValues("signal-brief.ingestion.enabled=true")
                .run(context -> assertTrue(context.containsBean("feedIngestionScheduler")));
    }

    @Test
    void scheduledMethodTriggersIngestionOnce() {
        contextRunner
                .withPropertyValues("signal-brief.ingestion.enabled=true")
                .run(context -> {
                    FeedIngestionScheduler scheduler = context.getBean(FeedIngestionScheduler.class);
                    CountingFeedIngestionService service = context.getBean(CountingFeedIngestionService.class);

                    scheduler.ingestEnabledFeeds();

                    assertEquals(1, service.calls);
                });
    }

    @Configuration
    static class TestConfiguration {

        @Bean
        FeedProperties feedProperties() {
            return new FeedProperties(List.of());
        }

        @Bean
        CountingFeedIngestionService feedIngestionService() {
            return new CountingFeedIngestionService();
        }
    }

    static class CountingFeedIngestionService extends FeedIngestionService {
        private int calls;

        CountingFeedIngestionService() {
            super(null, null, null, null);
        }

        @Override
        public FeedIngestionResult ingestEnabledFeeds() {
            calls++;
            return FeedIngestionResult.empty();
        }
    }
}
```

Run:

```bash
./mvnw -o -Dspring.docker.compose.enabled=false -Dtest=FeedIngestionSchedulerTest test
```

Expected: fail because `FeedIngestionScheduler` does not exist.

- [ ] **Step 2: Add scheduler**

Implement the scheduler:

```java
package cn.name.celestrong.signalbrief.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "signal-brief.ingestion", name = "enabled", havingValue = "true")
public class FeedIngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(FeedIngestionScheduler.class);

    private final FeedIngestionService feedIngestionService;

    public FeedIngestionScheduler(FeedIngestionService feedIngestionService) {
        this.feedIngestionService = feedIngestionService;
    }

    @Scheduled(cron = "${signal-brief.ingestion.cron}")
    public void ingestEnabledFeeds() {
        FeedIngestionResult result = feedIngestionService.ingestEnabledFeeds();
        log.info(
                "Scheduled feed ingestion finished: sources={}, fetched={}, inserted={}, skipped={}, failedSources={}",
                result.sourceCount(),
                result.fetchedCount(),
                result.insertedCount(),
                result.skippedCount(),
                result.failedSourceCount()
        );
    }
}
```

- [ ] **Step 3: Run focused scheduler test**

Run:

```bash
./mvnw -o -Dspring.docker.compose.enabled=false -Dtest=FeedIngestionSchedulerTest test
```

Expected: pass.

## Task 3: Add Article Query Service for Brief Candidates

**Files:**
- Create: `src/main/java/cn/name/celestrong/signalbrief/article/ArticleQueryMapper.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/article/ArticleQueryService.java`
- Test: `src/test/java/cn/name/celestrong/signalbrief/article/ArticleQueryServiceTest.java`
- Test: `src/test/java/cn/name/celestrong/signalbrief/article/ArticleQueryMapperIT.java`

- [ ] **Step 1: Write query service unit test**

Create validation-focused unit tests:

```java
package cn.name.celestrong.signalbrief.article;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArticleQueryServiceTest {

    @Test
    void rejectsMissingStartTime() {
        ArticleQueryService service = new ArticleQueryService((startInclusive, endExclusive) -> List.of());

        assertThrows(IllegalArgumentException.class, () -> service.findBriefCandidates(null, Instant.parse("2026-07-16T00:00:00Z")));
    }

    @Test
    void rejectsMissingEndTime() {
        ArticleQueryService service = new ArticleQueryService((startInclusive, endExclusive) -> List.of());

        assertThrows(IllegalArgumentException.class, () -> service.findBriefCandidates(Instant.parse("2026-07-01T00:00:00Z"), null));
    }

    @Test
    void rejectsEmptyOrNegativeWindow() {
        ArticleQueryService service = new ArticleQueryService((startInclusive, endExclusive) -> List.of());

        assertThrows(IllegalArgumentException.class, () -> service.findBriefCandidates(
                Instant.parse("2026-07-16T00:00:00Z"),
                Instant.parse("2026-07-16T00:00:00Z")
        ));
        assertThrows(IllegalArgumentException.class, () -> service.findBriefCandidates(
                Instant.parse("2026-07-17T00:00:00Z"),
                Instant.parse("2026-07-16T00:00:00Z")
        ));
    }

    @Test
    void delegatesValidWindowToMapper() {
        Instant start = Instant.parse("2026-07-01T00:00:00Z");
        Instant end = Instant.parse("2026-07-16T00:00:00Z");
        RecordingArticleQueryMapper mapper = new RecordingArticleQueryMapper();
        ArticleQueryService service = new ArticleQueryService(mapper);

        List<Article> articles = service.findBriefCandidates(start, end);

        assertEquals(List.of(), articles);
        assertEquals(start, mapper.startInclusive);
        assertEquals(end, mapper.endExclusive);
    }

    private static final class RecordingArticleQueryMapper implements ArticleQueryMapper {
        private Instant startInclusive;
        private Instant endExclusive;

        @Override
        public List<Article> findBriefCandidates(Instant startInclusive, Instant endExclusive) {
            this.startInclusive = startInclusive;
            this.endExclusive = endExclusive;
            return List.of();
        }
    }
}
```

Run:

```bash
./mvnw -o -Dspring.docker.compose.enabled=false -Dtest=ArticleQueryServiceTest test
```

Expected: fail because query types do not exist.

- [ ] **Step 2: Add query mapper interface**

Create `ArticleQueryMapper`:

```java
package cn.name.celestrong.signalbrief.article;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;

@Mapper
public interface ArticleQueryMapper {

    @Select("""
            SELECT
                id,
                source_name AS "sourceName",
                source_url AS "sourceUrl",
                category,
                title,
                url,
                guid,
                published_at AS "publishedAt",
                summary,
                content_hash AS "contentHash",
                created_at AS "createdAt",
                updated_at AS "updatedAt"
            FROM article
            WHERE COALESCE(published_at, created_at) >= #{startInclusive}
              AND COALESCE(published_at, created_at) < #{endExclusive}
            ORDER BY category ASC, COALESCE(published_at, created_at) DESC, id DESC
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "sourceName", javaType = String.class),
            @Arg(column = "sourceUrl", javaType = String.class),
            @Arg(column = "category", javaType = ArticleCategory.class),
            @Arg(column = "title", javaType = String.class),
            @Arg(column = "url", javaType = String.class),
            @Arg(column = "guid", javaType = String.class),
            @Arg(column = "publishedAt", javaType = Instant.class),
            @Arg(column = "summary", javaType = String.class),
            @Arg(column = "contentHash", javaType = String.class),
            @Arg(column = "createdAt", javaType = Instant.class),
            @Arg(column = "updatedAt", javaType = Instant.class)
    })
    List<Article> findBriefCandidates(
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive
    );
}
```

- [ ] **Step 3: Add query service**

Create `ArticleQueryService`:

```java
package cn.name.celestrong.signalbrief.article;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class ArticleQueryService {

    private final ArticleQueryMapper articleQueryMapper;

    public ArticleQueryService(ArticleQueryMapper articleQueryMapper) {
        this.articleQueryMapper = articleQueryMapper;
    }

    public List<Article> findBriefCandidates(Instant startInclusive, Instant endExclusive) {
        if (startInclusive == null) {
            throw new IllegalArgumentException("Brief candidate start time must not be null");
        }
        if (endExclusive == null) {
            throw new IllegalArgumentException("Brief candidate end time must not be null");
        }
        if (!startInclusive.isBefore(endExclusive)) {
            throw new IllegalArgumentException("Brief candidate start time must be before end time");
        }
        return articleQueryMapper.findBriefCandidates(startInclusive, endExclusive);
    }
}
```

- [ ] **Step 4: Run query service unit test**

Run:

```bash
./mvnw -o -Dspring.docker.compose.enabled=false -Dtest=ArticleQueryServiceTest test
```

Expected: pass.

- [ ] **Step 5: Add mapper integration test**

Create `ArticleQueryMapperIT`:

```java
package cn.name.celestrong.signalbrief.article;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Sql(statements = "TRUNCATE TABLE article RESTART IDENTITY", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ArticleQueryMapperIT {

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private ArticleQueryMapper articleQueryMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findsCandidatesByPublishedAtWindow() {
        articleMapper.insertIfAbsent(sampleArticle("inside-window", "https://example.com/inside", "hash-query-001", Instant.parse("2026-07-03T00:00:00Z")));
        articleMapper.insertIfAbsent(sampleArticle("outside-window", "https://example.com/outside", "hash-query-002", Instant.parse("2026-06-30T23:59:59Z")));

        List<Article> articles = articleQueryMapper.findBriefCandidates(
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-16T00:00:00Z")
        );

        assertEquals(1, articles.size());
        assertEquals("Test Article inside-window", articles.getFirst().title());
    }

    @Test
    void fallsBackToCreatedAtWhenPublishedAtIsMissing() {
        articleMapper.insertIfAbsent(sampleArticle("created-window", "https://example.com/created", "hash-query-003", null));
        jdbcTemplate.update("UPDATE article SET created_at = TIMESTAMPTZ '2026-07-04T00:00:00Z' WHERE guid = 'created-window'");

        List<Article> articles = articleQueryMapper.findBriefCandidates(
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-16T00:00:00Z")
        );

        assertEquals(1, articles.size());
        assertEquals("Test Article created-window", articles.getFirst().title());
    }

    @Test
    void sortsByCategoryEffectiveTimeAndId() {
        articleMapper.insertIfAbsent(sampleArticle("java-old", "https://example.com/java-old", "hash-query-004", Instant.parse("2026-07-03T00:00:00Z"), ArticleCategory.JAVA));
        articleMapper.insertIfAbsent(sampleArticle("framework-new", "https://example.com/framework-new", "hash-query-005", Instant.parse("2026-07-05T00:00:00Z"), ArticleCategory.FRAMEWORK));
        articleMapper.insertIfAbsent(sampleArticle("framework-newer-id", "https://example.com/framework-newer-id", "hash-query-006", Instant.parse("2026-07-05T00:00:00Z"), ArticleCategory.FRAMEWORK));

        List<Article> articles = articleQueryMapper.findBriefCandidates(
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-16T00:00:00Z")
        );

        assertEquals(List.of(
                "Test Article framework-newer-id",
                "Test Article framework-new",
                "Test Article java-old"
        ), articles.stream().map(Article::title).toList());
    }

    private NewArticle sampleArticle(String guid, String url, String contentHash, Instant publishedAt) {
        return sampleArticle(guid, url, contentHash, publishedAt, ArticleCategory.JAVA);
    }

    private NewArticle sampleArticle(String guid, String url, String contentHash, Instant publishedAt, ArticleCategory category) {
        return new NewArticle(
                "Test Source",
                "https://example.com/feed.xml",
                category,
                "Test Article " + guid,
                url,
                guid,
                publishedAt,
                "Summary",
                contentHash
        );
    }
}
```

Run locally only when intentionally checking database behavior:

```bash
./mvnw -B verify
```

Expected: pass in CI with PostgreSQL service. Local default workflow does not require this command.

## Task 4: Update Documentation and Environment Examples

**Files:**
- Modify: `.env.example`
- Modify: `README.md`
- Modify: `docs/rss-ingestion.md`

- [ ] **Step 1: Update `.env.example`**

Add safe defaults:

```dotenv
SIGNAL_BRIEF_INGESTION_ENABLED=false
SIGNAL_BRIEF_INGESTION_CRON="0 0 6 1,16 * *"
```

- [ ] **Step 2: Update README**

In the RSS configuration section, document:

````markdown
RSS 入库任务默认关闭，避免本地启动时自动访问外部源。如需开启：

```bash
SIGNAL_BRIEF_INGESTION_ENABLED=true ./mvnw spring-boot:run
```

默认 cron 为 `0 0 6 1,16 * *`，表示每月 1 日和 16 日 06:00 执行。可通过 `SIGNAL_BRIEF_INGESTION_CRON` 覆盖。
````

- [ ] **Step 3: Update RSS maintenance note**

In `docs/rss-ingestion.md`, update the current implementation snapshot and follow-up list:

```markdown
- `FeedIngestionScheduler` 在 `signal-brief.ingestion.enabled=true` 时按 cron 触发入库，默认关闭。
- `ArticleQueryService` 提供简报候选文章查询，按 `published_at` 优先、缺失时回退 `created_at`。
```

Remove the old follow-up item “增加定时触发或手动触发入口” or change it to “后续可按需要增加手动触发入口”。

## Final Verification

- [ ] **Step 1: Run local baseline tests**

Run:

```bash
./mvnw -o -Dspring.docker.compose.enabled=false test
```

Expected: all local unit tests pass.

- [ ] **Step 2: Run static diff checks**

Run:

```bash
git diff --check
```

Expected: exit code 0.

- [ ] **Step 3: Review changed files**

Run:

```bash
git status --short --branch
git diff --stat
```

Expected: changes are limited to configuration, scheduler, article query, tests, and docs for this feature.

## Acceptance Criteria

- RSS 定时任务默认关闭，开启后按 `signal-brief.ingestion.cron` 调用现有入库服务。
- 后续简报模块可以通过 `ArticleQueryService.findBriefCandidates(startInclusive, endExclusive)` 获取候选文章。
- 查询窗口为半开区间，时间口径为 `COALESCE(published_at, created_at)`。
- 不新增真实外部网络测试，不改变现有 RSS 抓取、解析和入库语义。
- 本地基础测试通过；数据库查询行为由 CI 集成测试验证。
