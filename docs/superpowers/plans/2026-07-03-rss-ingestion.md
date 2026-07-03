# RSS Ingestion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first MVP slice that reads enabled RSS source configuration, parses RSS / Atom entries, deduplicates articles, and persists new articles to PostgreSQL.

**Architecture:** Feed sources remain in YAML for MVP. ROME parses feed XML into `FetchedArticle`; article services normalize, hash, deduplicate, and save via MyBatis. A single orchestration service ingests all enabled feeds and returns execution statistics.

**Tech Stack:** Java 25, Spring Boot 4, Maven, PostgreSQL, Flyway, MyBatis, ROME, JUnit 5.

---

## Current Constraints

- Work happens on branch `feat/rss-ingestion`.
- Existing unrelated worktree changes must be ignored: `compose.yaml` modified and `mvnw.cmd` deleted.
- `./mvnw -B test` is the baseline command and currently passes.
- `./mvnw -B verify` needs a reachable PostgreSQL instance.
- Do not add AI summary, mail sending, scheduled jobs, Web UI, or database-managed feed sources in this plan.

## File Structure

- Modify `src/main/java/cn/name/celestrong/signalbrief/SignalBriefApplication.java`: enable `@ConfigurationPropertiesScan`.
- Create `src/main/java/cn/name/celestrong/signalbrief/article/ArticleCategory.java`: fixed article categories.
- Create `src/main/java/cn/name/celestrong/signalbrief/config/FeedProperties.java`: bind `signal-brief.feeds`.
- Modify `src/main/resources/application.yaml`: add empty `signal-brief.feeds` default.
- Create `src/test/java/cn/name/celestrong/signalbrief/config/FeedPropertiesTest.java`: unit coverage for enabled feed filtering and validation.
- Create `src/main/java/cn/name/celestrong/signalbrief/feed/FetchedArticle.java`: parsed feed item DTO.
- Create `src/main/java/cn/name/celestrong/signalbrief/feed/FeedParser.java`: parser interface.
- Create `src/main/java/cn/name/celestrong/signalbrief/feed/RomeFeedParser.java`: ROME parser implementation.
- Create `src/main/java/cn/name/celestrong/signalbrief/feed/FeedParseException.java`: parser exception.
- Create `src/test/resources/fixtures/rss/spring-blog.xml`: RSS fixture.
- Create `src/test/resources/fixtures/rss/inside-java.atom`: Atom fixture.
- Create `src/test/java/cn/name/celestrong/signalbrief/feed/RomeFeedParserTest.java`: parser tests.
- Create `src/main/java/cn/name/celestrong/signalbrief/article/Article.java`: persisted article model.
- Create `src/main/java/cn/name/celestrong/signalbrief/article/NewArticle.java`: insert command.
- Create `src/main/java/cn/name/celestrong/signalbrief/article/ArticleMapper.java`: MyBatis persistence boundary.
- Create `src/main/resources/db/migration/V1__create_article_table.sql`: article table and indexes.
- Create `src/test/java/cn/name/celestrong/signalbrief/article/ArticleMapperIT.java`: database integration tests.
- Create `src/main/java/cn/name/celestrong/signalbrief/article/ArticleDeduplicationService.java`: hash and duplicate checks.
- Create `src/main/java/cn/name/celestrong/signalbrief/article/ArticleIngestionService.java`: save parsed articles.
- Create `src/test/java/cn/name/celestrong/signalbrief/article/ArticleDeduplicationServiceTest.java`: hash and duplicate-key behavior.
- Create `src/test/java/cn/name/celestrong/signalbrief/article/ArticleIngestionServiceTest.java`: ingestion behavior with fake mapper.
- Create `src/main/java/cn/name/celestrong/signalbrief/feed/FeedClient.java`: feed byte retrieval boundary.
- Create `src/main/java/cn/name/celestrong/signalbrief/feed/HttpFeedClient.java`: HTTP implementation.
- Create `src/main/java/cn/name/celestrong/signalbrief/ingestion/FeedIngestionResult.java`: batch statistics.
- Create `src/main/java/cn/name/celestrong/signalbrief/ingestion/FeedIngestionService.java`: orchestration.
- Create `src/test/java/cn/name/celestrong/signalbrief/ingestion/FeedIngestionServiceTest.java`: orchestration tests using fakes.
- Modify `README.md`: document `signal-brief.feeds` configuration.

---

### Task 1: Feed Configuration

**Files:**
- Modify: `src/main/java/cn/name/celestrong/signalbrief/SignalBriefApplication.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/article/ArticleCategory.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/config/FeedProperties.java`
- Modify: `src/main/resources/application.yaml`
- Test: `src/test/java/cn/name/celestrong/signalbrief/config/FeedPropertiesTest.java`

- [ ] **Step 1: Write failing tests for feed configuration**

Create `src/test/java/cn/name/celestrong/signalbrief/config/FeedPropertiesTest.java`:

```java
package cn.name.celestrong.signalbrief.config;

import cn.name.celestrong.signalbrief.article.ArticleCategory;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeedPropertiesTest {

    @Test
    void enabledFeedsReturnsOnlyEnabledSources() {
        FeedProperties properties = new FeedProperties(List.of(
                new FeedProperties.FeedSource("Spring Blog", URI.create("https://spring.io/blog.atom"), ArticleCategory.FRAMEWORK, true),
                new FeedProperties.FeedSource("Disabled", URI.create("https://example.com/rss.xml"), ArticleCategory.INDUSTRY, false)
        ));

        List<FeedProperties.FeedSource> enabledFeeds = properties.enabledFeeds();

        assertEquals(1, enabledFeeds.size());
        assertEquals("Spring Blog", enabledFeeds.getFirst().name());
    }

    @Test
    void rejectsBlankFeedName() {
        assertThrows(IllegalArgumentException.class, () -> new FeedProperties.FeedSource(
                " ",
                URI.create("https://spring.io/blog.atom"),
                ArticleCategory.FRAMEWORK,
                true
        ));
    }

    @Test
    void rejectsMissingUrl() {
        assertThrows(IllegalArgumentException.class, () -> new FeedProperties.FeedSource(
                "Spring Blog",
                null,
                ArticleCategory.FRAMEWORK,
                true
        ));
    }
}
```

- [ ] **Step 2: Run the config test and verify it fails**

Run:

```bash
./mvnw -B -Dtest=FeedPropertiesTest test
```

Expected: compilation fails because `FeedProperties` and `ArticleCategory` do not exist.

- [ ] **Step 3: Add article category enum**

Create `src/main/java/cn/name/celestrong/signalbrief/article/ArticleCategory.java`:

```java
package cn.name.celestrong.signalbrief.article;

public enum ArticleCategory {
    JAVA,
    FRAMEWORK,
    INDUSTRY,
    CAREER,
    AI
}
```

- [ ] **Step 4: Add feed properties**

Create `src/main/java/cn/name/celestrong/signalbrief/config/FeedProperties.java`:

```java
package cn.name.celestrong.signalbrief.config;

import cn.name.celestrong.signalbrief.article.ArticleCategory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.List;

@ConfigurationProperties(prefix = "signal-brief")
public record FeedProperties(List<FeedSource> feeds) {

    public FeedProperties {
        feeds = feeds == null ? List.of() : List.copyOf(feeds);
    }

    public List<FeedSource> enabledFeeds() {
        return feeds.stream()
                .filter(FeedSource::enabled)
                .toList();
    }

    public record FeedSource(
            String name,
            URI url,
            ArticleCategory category,
            boolean enabled
    ) {
        public FeedSource {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Feed source name must not be blank");
            }
            if (url == null) {
                throw new IllegalArgumentException("Feed source url must not be null");
            }
            if (category == null) {
                throw new IllegalArgumentException("Feed source category must not be null");
            }
        }
    }
}
```

- [ ] **Step 5: Enable configuration properties scanning**

Modify `src/main/java/cn/name/celestrong/signalbrief/SignalBriefApplication.java`:

```java
package cn.name.celestrong.signalbrief;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class SignalBriefApplication {

    public static void main(String[] args) {
        SpringApplication.run(SignalBriefApplication.class, args);
    }

}
```

- [ ] **Step 6: Add default feed configuration**

Modify `src/main/resources/application.yaml` by adding the root-level block:

```yaml
signal-brief:
  feeds: []
```

Keep the existing `spring:` block unchanged.

- [ ] **Step 7: Run config test**

Run:

```bash
./mvnw -B -Dtest=FeedPropertiesTest test
```

Expected: build success.

- [ ] **Step 8: Commit configuration task**

```bash
git add src/main/java/cn/name/celestrong/signalbrief/SignalBriefApplication.java \
  src/main/java/cn/name/celestrong/signalbrief/article/ArticleCategory.java \
  src/main/java/cn/name/celestrong/signalbrief/config/FeedProperties.java \
  src/main/resources/application.yaml \
  src/test/java/cn/name/celestrong/signalbrief/config/FeedPropertiesTest.java
git commit -m "feat(feed): 添加 RSS 源配置"
```

---

### Task 2: ROME Feed Parser

**Files:**
- Create: `src/main/java/cn/name/celestrong/signalbrief/feed/FetchedArticle.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/feed/FeedParser.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/feed/RomeFeedParser.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/feed/FeedParseException.java`
- Create: `src/test/resources/fixtures/rss/spring-blog.xml`
- Create: `src/test/resources/fixtures/rss/inside-java.atom`
- Test: `src/test/java/cn/name/celestrong/signalbrief/feed/RomeFeedParserTest.java`

- [ ] **Step 1: Add local RSS and Atom fixtures**

Create `src/test/resources/fixtures/rss/spring-blog.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0">
  <channel>
    <title>Spring Blog</title>
    <link>https://spring.io/blog</link>
    <description>Spring Blog fixture</description>
    <item>
      <title>Spring Boot 4.0.7 available now</title>
      <link>https://spring.io/blog/2026/07/01/spring-boot-4-0-7-available-now</link>
      <guid>spring-boot-4.0.7</guid>
      <pubDate>Wed, 01 Jul 2026 10:00:00 GMT</pubDate>
      <description>Spring Boot 4.0.7 has been released.</description>
    </item>
  </channel>
</rss>
```

Create `src/test/resources/fixtures/rss/inside-java.atom`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom">
  <title>Inside Java</title>
  <link href="https://inside.java/feed.xml"/>
  <updated>2026-07-02T08:30:00Z</updated>
  <entry>
    <title>JDK update notes</title>
    <link href="https://inside.java/2026/07/02/jdk-update-notes"/>
    <id>inside-java-jdk-update-notes</id>
    <updated>2026-07-02T08:30:00Z</updated>
    <summary>Notes from the Java team.</summary>
  </entry>
</feed>
```

- [ ] **Step 2: Write failing parser tests**

Create `src/test/java/cn/name/celestrong/signalbrief/feed/RomeFeedParserTest.java`:

```java
package cn.name.celestrong.signalbrief.feed;

import cn.name.celestrong.signalbrief.article.ArticleCategory;
import cn.name.celestrong.signalbrief.config.FeedProperties;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RomeFeedParserTest {

    private final RomeFeedParser parser = new RomeFeedParser();

    @Test
    void parsesRssItem() throws Exception {
        FeedProperties.FeedSource source = new FeedProperties.FeedSource(
                "Spring Blog",
                URI.create("https://spring.io/blog.atom"),
                ArticleCategory.FRAMEWORK,
                true
        );

        try (InputStream inputStream = fixture("fixtures/rss/spring-blog.xml")) {
            List<FetchedArticle> articles = parser.parse(source, inputStream);

            assertEquals(1, articles.size());
            FetchedArticle article = articles.getFirst();
            assertEquals("Spring Blog", article.sourceName());
            assertEquals(ArticleCategory.FRAMEWORK, article.category());
            assertEquals("Spring Boot 4.0.7 available now", article.title());
            assertEquals("https://spring.io/blog/2026/07/01/spring-boot-4-0-7-available-now", article.url());
            assertEquals("spring-boot-4.0.7", article.guid());
            assertEquals(Instant.parse("2026-07-01T10:00:00Z"), article.publishedAt());
            assertEquals("Spring Boot 4.0.7 has been released.", article.summary());
        }
    }

    @Test
    void parsesAtomEntryUpdatedDateWhenPublishedDateIsMissing() throws Exception {
        FeedProperties.FeedSource source = new FeedProperties.FeedSource(
                "Inside Java",
                URI.create("https://inside.java/feed.xml"),
                ArticleCategory.JAVA,
                true
        );

        try (InputStream inputStream = fixture("fixtures/rss/inside-java.atom")) {
            List<FetchedArticle> articles = parser.parse(source, inputStream);

            assertEquals(1, articles.size());
            FetchedArticle article = articles.getFirst();
            assertEquals("inside-java-jdk-update-notes", article.guid());
            assertEquals("https://inside.java/2026/07/02/jdk-update-notes", article.url());
            assertEquals(Instant.parse("2026-07-02T08:30:00Z"), article.publishedAt());
            assertNotNull(article.summary());
        }
    }

    private InputStream fixture(String path) {
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        if (inputStream == null) {
            throw new IllegalArgumentException("Fixture not found: " + path);
        }
        return inputStream;
    }
}
```

- [ ] **Step 3: Run parser test and verify it fails**

Run:

```bash
./mvnw -B -Dtest=RomeFeedParserTest test
```

Expected: compilation fails because parser classes do not exist.

- [ ] **Step 4: Add parsed article record and parser interface**

Create `src/main/java/cn/name/celestrong/signalbrief/feed/FetchedArticle.java`:

```java
package cn.name.celestrong.signalbrief.feed;

import cn.name.celestrong.signalbrief.article.ArticleCategory;

import java.time.Instant;

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

Create `src/main/java/cn/name/celestrong/signalbrief/feed/FeedParser.java`:

```java
package cn.name.celestrong.signalbrief.feed;

import cn.name.celestrong.signalbrief.config.FeedProperties;

import java.io.InputStream;
import java.util.List;

public interface FeedParser {

    List<FetchedArticle> parse(FeedProperties.FeedSource source, InputStream inputStream);
}
```

- [ ] **Step 5: Add ROME parser**

Create `src/main/java/cn/name/celestrong/signalbrief/feed/RomeFeedParser.java`:

```java
package cn.name.celestrong.signalbrief.feed;

import cn.name.celestrong.signalbrief.config.FeedProperties;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Component
public class RomeFeedParser implements FeedParser {

    @Override
    public List<FetchedArticle> parse(FeedProperties.FeedSource source, InputStream inputStream) {
        try {
            SyndFeed feed = new SyndFeedInput().build(new XmlReader(inputStream));
            return feed.getEntries().stream()
                    .map(entry -> toFetchedArticle(source, entry))
                    .toList();
        } catch (Exception ex) {
            throw new FeedParseException("Failed to parse feed source: " + source.name(), ex);
        }
    }

    private FetchedArticle toFetchedArticle(FeedProperties.FeedSource source, SyndEntry entry) {
        return new FetchedArticle(
                source.name(),
                source.url(),
                source.category(),
                clean(entry.getTitle()),
                clean(entry.getLink()),
                clean(entry.getUri()),
                toInstant(entry.getPublishedDate(), entry.getUpdatedDate()),
                entry.getDescription() == null ? null : clean(entry.getDescription().getValue())
        );
    }

    private Instant toInstant(Date publishedDate, Date updatedDate) {
        Date date = publishedDate != null ? publishedDate : updatedDate;
        return date == null ? null : date.toInstant();
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
```

Create `src/main/java/cn/name/celestrong/signalbrief/feed/FeedParseException.java`:

```java
package cn.name.celestrong.signalbrief.feed;

public class FeedParseException extends RuntimeException {

    public FeedParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 6: Run parser test**

Run:

```bash
./mvnw -B -Dtest=RomeFeedParserTest test
```

Expected: build success.

- [ ] **Step 7: Commit parser task**

```bash
git add src/main/java/cn/name/celestrong/signalbrief/feed \
  src/test/java/cn/name/celestrong/signalbrief/feed/RomeFeedParserTest.java \
  src/test/resources/fixtures/rss
git commit -m "feat(feed): 解析 RSS 和 Atom 条目"
```

---

### Task 3: Article Table and Mapper

**Files:**
- Create: `src/main/resources/db/migration/V1__create_article_table.sql`
- Create: `src/main/java/cn/name/celestrong/signalbrief/article/Article.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/article/NewArticle.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/article/ArticleMapper.java`
- Test: `src/test/java/cn/name/celestrong/signalbrief/article/ArticleMapperIT.java`

- [ ] **Step 1: Write failing mapper integration test**

Create `src/test/java/cn/name/celestrong/signalbrief/article/ArticleMapperIT.java`:

```java
package cn.name.celestrong.signalbrief.article;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ArticleMapperIT {

    @Autowired
    private ArticleMapper articleMapper;

    @Test
    void insertsAndFindsArticleByUrl() {
        NewArticle article = sampleArticle("mapper-url-1", "https://example.com/article-1", "hash-001");

        int insertedRows = articleMapper.insertIfAbsent(article);

        assertEquals(1, insertedRows);
        assertTrue(articleMapper.existsByUrl("https://example.com/article-1"));
    }

    @Test
    void skipsDuplicateUrl() {
        NewArticle first = sampleArticle("mapper-url-2", "https://example.com/article-2", "hash-002");
        NewArticle duplicateUrl = sampleArticle("mapper-url-3", "https://example.com/article-2", "hash-003");

        assertEquals(1, articleMapper.insertIfAbsent(first));
        assertEquals(0, articleMapper.insertIfAbsent(duplicateUrl));
    }

    @Test
    void skipsDuplicateSourceGuid() {
        NewArticle first = sampleArticle("mapper-guid", "https://example.com/article-3", "hash-004");
        NewArticle duplicateGuid = sampleArticle("mapper-guid", "https://example.com/article-4", "hash-005");

        assertEquals(1, articleMapper.insertIfAbsent(first));
        assertEquals(0, articleMapper.insertIfAbsent(duplicateGuid));
    }

    @Test
    void skipsDuplicateContentHash() {
        NewArticle first = sampleArticle("mapper-hash-1", "https://example.com/article-5", "hash-006");
        NewArticle duplicateHash = sampleArticle("mapper-hash-2", "https://example.com/article-6", "hash-006");

        assertEquals(1, articleMapper.insertIfAbsent(first));
        assertEquals(0, articleMapper.insertIfAbsent(duplicateHash));
    }

    private NewArticle sampleArticle(String guid, String url, String contentHash) {
        return new NewArticle(
                "Test Source",
                "https://example.com/feed.xml",
                ArticleCategory.JAVA,
                "Test Article " + guid,
                url,
                guid,
                Instant.parse("2026-07-03T00:00:00Z"),
                "Summary",
                contentHash
        );
    }
}
```

- [ ] **Step 2: Run mapper test and verify it fails**

Run with PostgreSQL available:

```bash
SPRING_PROFILES_ACTIVE=test \
SPRING_DATASOURCE_USERNAME=celestrong \
SPRING_DATASOURCE_PASSWORD=123456 \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/signal_brief \
SERVER_PORT=8080 \
./mvnw -B -Dit.test=ArticleMapperIT verify
```

Expected: compilation fails because mapper and article model do not exist.

- [ ] **Step 3: Add Flyway migration**

Create `src/main/resources/db/migration/V1__create_article_table.sql`:

```sql
CREATE TABLE article (
    id BIGSERIAL PRIMARY KEY,
    source_name VARCHAR(200) NOT NULL,
    source_url TEXT NOT NULL,
    category VARCHAR(50) NOT NULL,
    title TEXT NOT NULL,
    url TEXT,
    guid TEXT,
    published_at TIMESTAMPTZ,
    summary TEXT,
    content_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_article_url
    ON article (url)
    WHERE url IS NOT NULL;

CREATE UNIQUE INDEX uk_article_source_guid
    ON article (source_name, guid)
    WHERE guid IS NOT NULL;

CREATE UNIQUE INDEX uk_article_content_hash
    ON article (content_hash);

CREATE INDEX idx_article_published_at
    ON article (published_at);
```

- [ ] **Step 4: Add article records**

Create `src/main/java/cn/name/celestrong/signalbrief/article/Article.java`:

```java
package cn.name.celestrong.signalbrief.article;

import java.time.Instant;

public record Article(
        Long id,
        String sourceName,
        String sourceUrl,
        ArticleCategory category,
        String title,
        String url,
        String guid,
        Instant publishedAt,
        String summary,
        String contentHash,
        Instant createdAt,
        Instant updatedAt
) {
}
```

Create `src/main/java/cn/name/celestrong/signalbrief/article/NewArticle.java`:

```java
package cn.name.celestrong.signalbrief.article;

import java.time.Instant;

public record NewArticle(
        String sourceName,
        String sourceUrl,
        ArticleCategory category,
        String title,
        String url,
        String guid,
        Instant publishedAt,
        String summary,
        String contentHash
) {
    public NewArticle {
        if (sourceName == null || sourceName.isBlank()) {
            throw new IllegalArgumentException("Article sourceName must not be blank");
        }
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("Article sourceUrl must not be blank");
        }
        if (category == null) {
            throw new IllegalArgumentException("Article category must not be null");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Article title must not be blank");
        }
        if (contentHash == null || contentHash.isBlank()) {
            throw new IllegalArgumentException("Article contentHash must not be blank");
        }
    }
}
```

- [ ] **Step 5: Add MyBatis mapper**

Create `src/main/java/cn/name/celestrong/signalbrief/article/ArticleMapper.java`:

```java
package cn.name.celestrong.signalbrief.article;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ArticleMapper {

    @Insert("""
            INSERT INTO article (
                source_name,
                source_url,
                category,
                title,
                url,
                guid,
                published_at,
                summary,
                content_hash
            ) VALUES (
                #{sourceName},
                #{sourceUrl},
                #{category},
                #{title},
                #{url},
                #{guid},
                #{publishedAt},
                #{summary},
                #{contentHash}
            )
            ON CONFLICT DO NOTHING
            """)
    int insertIfAbsent(NewArticle article);

    @Select("""
            SELECT EXISTS (
                SELECT 1 FROM article
                WHERE source_name = #{sourceName}
                  AND guid = #{guid}
            )
            """)
    boolean existsBySourceNameAndGuid(@Param("sourceName") String sourceName, @Param("guid") String guid);

    @Select("""
            SELECT EXISTS (
                SELECT 1 FROM article
                WHERE url = #{url}
            )
            """)
    boolean existsByUrl(@Param("url") String url);

    @Select("""
            SELECT EXISTS (
                SELECT 1 FROM article
                WHERE content_hash = #{contentHash}
            )
            """)
    boolean existsByContentHash(@Param("contentHash") String contentHash);
}
```

- [ ] **Step 6: Run mapper integration test**

Run with PostgreSQL available:

```bash
SPRING_PROFILES_ACTIVE=test \
SPRING_DATASOURCE_USERNAME=celestrong \
SPRING_DATASOURCE_PASSWORD=123456 \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/signal_brief \
SERVER_PORT=8080 \
./mvnw -B -Dit.test=ArticleMapperIT verify
```

Expected: build success.

- [ ] **Step 7: Commit mapper task**

```bash
git add src/main/resources/db/migration/V1__create_article_table.sql \
  src/main/java/cn/name/celestrong/signalbrief/article/Article.java \
  src/main/java/cn/name/celestrong/signalbrief/article/NewArticle.java \
  src/main/java/cn/name/celestrong/signalbrief/article/ArticleMapper.java \
  src/test/java/cn/name/celestrong/signalbrief/article/ArticleMapperIT.java
git commit -m "feat(article): 添加文章表和持久化映射"
```

---

### Task 4: Deduplication and Article Ingestion

**Files:**
- Create: `src/main/java/cn/name/celestrong/signalbrief/article/ArticleDeduplicationService.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/article/ArticleIngestionService.java`
- Test: `src/test/java/cn/name/celestrong/signalbrief/article/ArticleDeduplicationServiceTest.java`
- Test: `src/test/java/cn/name/celestrong/signalbrief/article/ArticleIngestionServiceTest.java`

- [ ] **Step 1: Write failing deduplication tests**

Create `src/test/java/cn/name/celestrong/signalbrief/article/ArticleDeduplicationServiceTest.java`:

```java
package cn.name.celestrong.signalbrief.article;

import cn.name.celestrong.signalbrief.feed.FetchedArticle;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleDeduplicationServiceTest {

    @Test
    void contentHashIsStableForEquivalentWhitespace() {
        ArticleDeduplicationService service = new ArticleDeduplicationService(new FakeArticleMapper());

        String first = service.contentHash(article("  Title  ", "https://example.com/a", "guid-1"));
        String second = service.contentHash(article("Title", "https://example.com/a", "guid-1"));

        assertEquals(first, second);
        assertEquals(64, first.length());
    }

    @Test
    void duplicateUsesSourceGuidBeforeOtherKeys() {
        FakeArticleMapper mapper = new FakeArticleMapper();
        mapper.existsBySourceNameAndGuid = true;
        ArticleDeduplicationService service = new ArticleDeduplicationService(mapper);

        assertTrue(service.exists(article("Title", "https://example.com/a", "guid-1")));
        assertEquals(1, mapper.sourceGuidChecks);
        assertEquals(0, mapper.urlChecks);
        assertEquals(0, mapper.hashChecks);
    }

    @Test
    void duplicateUsesUrlWhenGuidIsMissing() {
        FakeArticleMapper mapper = new FakeArticleMapper();
        mapper.existsByUrl = true;
        ArticleDeduplicationService service = new ArticleDeduplicationService(mapper);

        assertTrue(service.exists(article("Title", "https://example.com/a", null)));
        assertEquals(0, mapper.sourceGuidChecks);
        assertEquals(1, mapper.urlChecks);
        assertEquals(0, mapper.hashChecks);
    }

    @Test
    void duplicateFallsBackToContentHashWhenGuidAndUrlAreMissing() {
        FakeArticleMapper mapper = new FakeArticleMapper();
        mapper.existsByContentHash = false;
        ArticleDeduplicationService service = new ArticleDeduplicationService(mapper);

        assertFalse(service.exists(article("Title", null, null)));
        assertEquals(0, mapper.sourceGuidChecks);
        assertEquals(0, mapper.urlChecks);
        assertEquals(1, mapper.hashChecks);
    }

    private FetchedArticle article(String title, String url, String guid) {
        return new FetchedArticle(
                "Test Source",
                "https://example.com/feed.xml",
                ArticleCategory.JAVA,
                title,
                url,
                guid,
                Instant.parse("2026-07-03T00:00:00Z"),
                "Summary"
        );
    }

    private static final class FakeArticleMapper implements ArticleMapper {
        private boolean existsBySourceNameAndGuid;
        private boolean existsByUrl;
        private boolean existsByContentHash;
        private int sourceGuidChecks;
        private int urlChecks;
        private int hashChecks;

        @Override
        public int insertIfAbsent(NewArticle article) {
            return 0;
        }

        @Override
        public boolean existsBySourceNameAndGuid(String sourceName, String guid) {
            sourceGuidChecks++;
            return existsBySourceNameAndGuid;
        }

        @Override
        public boolean existsByUrl(String url) {
            urlChecks++;
            return existsByUrl;
        }

        @Override
        public boolean existsByContentHash(String contentHash) {
            hashChecks++;
            return existsByContentHash;
        }
    }
}
```

- [ ] **Step 2: Run deduplication test and verify it fails**

```bash
./mvnw -B -Dtest=ArticleDeduplicationServiceTest test
```

Expected: compilation fails because `ArticleDeduplicationService` does not exist.

- [ ] **Step 3: Implement deduplication service**

Create `src/main/java/cn/name/celestrong/signalbrief/article/ArticleDeduplicationService.java`:

```java
package cn.name.celestrong.signalbrief.article;

import cn.name.celestrong.signalbrief.feed.FetchedArticle;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class ArticleDeduplicationService {

    private final ArticleMapper articleMapper;

    public ArticleDeduplicationService(ArticleMapper articleMapper) {
        this.articleMapper = articleMapper;
    }

    public boolean exists(FetchedArticle article) {
        if (hasText(article.guid())) {
            return articleMapper.existsBySourceNameAndGuid(article.sourceName(), article.guid());
        }
        if (hasText(article.url())) {
            return articleMapper.existsByUrl(article.url());
        }
        return articleMapper.existsByContentHash(contentHash(article));
    }

    public String contentHash(FetchedArticle article) {
        String source = normalize(article.title())
                + "|"
                + normalize(article.url())
                + "|"
                + normalize(article.sourceName())
                + "|"
                + normalize(article.publishedAt());
        return sha256(source);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private String normalize(Instant value) {
        return value == null ? "" : value.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }
}
```

- [ ] **Step 4: Run deduplication test**

```bash
./mvnw -B -Dtest=ArticleDeduplicationServiceTest test
```

Expected: build success.

- [ ] **Step 5: Write failing article ingestion tests**

Create `src/test/java/cn/name/celestrong/signalbrief/article/ArticleIngestionServiceTest.java`:

```java
package cn.name.celestrong.signalbrief.article;

import cn.name.celestrong.signalbrief.feed.FetchedArticle;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArticleIngestionServiceTest {

    @Test
    void savesNewArticle() {
        FakeArticleMapper mapper = new FakeArticleMapper();
        ArticleDeduplicationService deduplicationService = new ArticleDeduplicationService(mapper);
        ArticleIngestionService ingestionService = new ArticleIngestionService(mapper, deduplicationService);

        ArticleIngestionService.Result result = ingestionService.ingest(article("guid-1", "https://example.com/a"));

        assertEquals(1, result.insertedCount());
        assertEquals(0, result.skippedCount());
        assertEquals(1, mapper.insertCalls);
    }

    @Test
    void skipsDuplicateArticle() {
        FakeArticleMapper mapper = new FakeArticleMapper();
        mapper.existsBySourceNameAndGuid = true;
        ArticleDeduplicationService deduplicationService = new ArticleDeduplicationService(mapper);
        ArticleIngestionService ingestionService = new ArticleIngestionService(mapper, deduplicationService);

        ArticleIngestionService.Result result = ingestionService.ingest(article("guid-1", "https://example.com/a"));

        assertEquals(0, result.insertedCount());
        assertEquals(1, result.skippedCount());
        assertEquals(0, mapper.insertCalls);
    }

    private FetchedArticle article(String guid, String url) {
        return new FetchedArticle(
                "Test Source",
                URI.create("https://example.com/feed.xml"),
                ArticleCategory.JAVA,
                "Test Article",
                url,
                guid,
                Instant.parse("2026-07-03T00:00:00Z"),
                "Summary"
        );
    }

    private static final class FakeArticleMapper implements ArticleMapper {
        private boolean existsBySourceNameAndGuid;
        private int insertCalls;

        @Override
        public int insertIfAbsent(NewArticle article) {
            insertCalls++;
            return 1;
        }

        @Override
        public boolean existsBySourceNameAndGuid(String sourceName, String guid) {
            return existsBySourceNameAndGuid;
        }

        @Override
        public boolean existsByUrl(String url) {
            return false;
        }

        @Override
        public boolean existsByContentHash(String contentHash) {
            return false;
        }
    }
}
```

- [ ] **Step 6: Run ingestion test and verify it fails**

```bash
./mvnw -B -Dtest=ArticleIngestionServiceTest test
```

Expected: compilation fails because `ArticleIngestionService` does not exist.

- [ ] **Step 7: Implement article ingestion service**

Create `src/main/java/cn/name/celestrong/signalbrief/article/ArticleIngestionService.java`:

```java
package cn.name.celestrong.signalbrief.article;

import cn.name.celestrong.signalbrief.feed.FetchedArticle;
import org.springframework.stereotype.Service;

@Service
public class ArticleIngestionService {

    private final ArticleMapper articleMapper;
    private final ArticleDeduplicationService deduplicationService;

    public ArticleIngestionService(
            ArticleMapper articleMapper,
            ArticleDeduplicationService deduplicationService
    ) {
        this.articleMapper = articleMapper;
        this.deduplicationService = deduplicationService;
    }

    public Result ingest(FetchedArticle article) {
        if (deduplicationService.exists(article)) {
            return new Result(0, 1);
        }

        NewArticle newArticle = new NewArticle(
                article.sourceName(),
                article.sourceUrl().toString(),
                article.category(),
                article.title(),
                article.url(),
                article.guid(),
                article.publishedAt(),
                article.summary(),
                deduplicationService.contentHash(article)
        );
        int insertedRows = articleMapper.insertIfAbsent(newArticle);
        return insertedRows == 1 ? new Result(1, 0) : new Result(0, 1);
    }

    public record Result(int insertedCount, int skippedCount) {
    }
}
```

- [ ] **Step 8: Run article tests**

```bash
./mvnw -B -Dtest=ArticleDeduplicationServiceTest,ArticleIngestionServiceTest test
```

Expected: build success.

- [ ] **Step 9: Commit deduplication and ingestion task**

```bash
git add src/main/java/cn/name/celestrong/signalbrief/article/ArticleDeduplicationService.java \
  src/main/java/cn/name/celestrong/signalbrief/article/ArticleIngestionService.java \
  src/test/java/cn/name/celestrong/signalbrief/article/ArticleDeduplicationServiceTest.java \
  src/test/java/cn/name/celestrong/signalbrief/article/ArticleIngestionServiceTest.java
git commit -m "feat(article): 实现文章去重入库"
```

---

### Task 5: Feed Ingestion Orchestration

**Files:**
- Create: `src/main/java/cn/name/celestrong/signalbrief/feed/FeedClient.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/feed/HttpFeedClient.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/feed/FeedFetchException.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/ingestion/FeedIngestionResult.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/ingestion/FeedIngestionService.java`
- Test: `src/test/java/cn/name/celestrong/signalbrief/ingestion/FeedIngestionServiceTest.java`

- [ ] **Step 1: Write failing orchestration tests**

Create `src/test/java/cn/name/celestrong/signalbrief/ingestion/FeedIngestionServiceTest.java`:

```java
package cn.name.celestrong.signalbrief.ingestion;

import cn.name.celestrong.signalbrief.article.ArticleCategory;
import cn.name.celestrong.signalbrief.article.ArticleIngestionService;
import cn.name.celestrong.signalbrief.config.FeedProperties;
import cn.name.celestrong.signalbrief.feed.FetchedArticle;
import cn.name.celestrong.signalbrief.feed.FeedClient;
import cn.name.celestrong.signalbrief.feed.FeedParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeedIngestionServiceTest {

    @Test
    void ingestsEnabledFeedsAndAggregatesStatistics() {
        FeedProperties properties = new FeedProperties(List.of(
                new FeedProperties.FeedSource("Enabled", URI.create("https://example.com/feed.xml"), ArticleCategory.JAVA, true),
                new FeedProperties.FeedSource("Disabled", URI.create("https://example.com/disabled.xml"), ArticleCategory.JAVA, false)
        ));
        FakeArticleIngestionService articleIngestionService = new FakeArticleIngestionService();
        FeedIngestionService service = new FeedIngestionService(
                properties,
                new FakeFeedClient(),
                new FakeFeedParser(),
                articleIngestionService
        );

        FeedIngestionResult result = service.ingestEnabledFeeds();

        assertEquals(1, result.sourceCount());
        assertEquals(2, result.fetchedCount());
        assertEquals(1, result.insertedCount());
        assertEquals(1, result.skippedCount());
        assertEquals(0, result.failedSourceCount());
        assertEquals(2, articleIngestionService.calls);
    }

    @Test
    void continuesWhenOneSourceFails() {
        FeedProperties properties = new FeedProperties(List.of(
                new FeedProperties.FeedSource("Failing", URI.create("https://example.com/failing.xml"), ArticleCategory.JAVA, true),
                new FeedProperties.FeedSource("Enabled", URI.create("https://example.com/feed.xml"), ArticleCategory.JAVA, true)
        ));
        FakeArticleIngestionService articleIngestionService = new FakeArticleIngestionService();
        FeedIngestionService service = new FeedIngestionService(
                properties,
                new FakeFeedClient(),
                new FakeFeedParser(),
                articleIngestionService
        );

        FeedIngestionResult result = service.ingestEnabledFeeds();

        assertEquals(2, result.sourceCount());
        assertEquals(2, result.fetchedCount());
        assertEquals(1, result.failedSourceCount());
        assertEquals(2, articleIngestionService.calls);
    }

    private static final class FakeFeedClient implements FeedClient {
        @Override
        public InputStream fetch(FeedProperties.FeedSource source) {
            if ("Failing".equals(source.name())) {
                throw new IllegalStateException("feed unavailable");
            }
            return new ByteArrayInputStream("<feed/>".getBytes(StandardCharsets.UTF_8));
        }
    }

    private static final class FakeFeedParser implements FeedParser {
        @Override
        public List<FetchedArticle> parse(FeedProperties.FeedSource source, InputStream inputStream) {
            return List.of(
                    article(source, "guid-1"),
                    article(source, "guid-2")
            );
        }

        private FetchedArticle article(FeedProperties.FeedSource source, String guid) {
            return new FetchedArticle(
                    source.name(),
                    source.url(),
                    source.category(),
                    "Title " + guid,
                    "https://example.com/" + guid,
                    guid,
                    Instant.parse("2026-07-03T00:00:00Z"),
                    "Summary"
            );
        }
    }

    private static final class FakeArticleIngestionService extends ArticleIngestionService {
        private int calls;

        private FakeArticleIngestionService() {
            super(null, null);
        }

        @Override
        public Result ingest(FetchedArticle article) {
            calls++;
            return "guid-1".equals(article.guid()) ? new Result(1, 0) : new Result(0, 1);
        }
    }
}
```

- [ ] **Step 2: Run orchestration test and verify it fails**

```bash
./mvnw -B -Dtest=FeedIngestionServiceTest test
```

Expected: compilation fails because orchestration classes do not exist.

- [ ] **Step 3: Add feed client boundary and HTTP implementation**

Create `src/main/java/cn/name/celestrong/signalbrief/feed/FeedClient.java`:

```java
package cn.name.celestrong.signalbrief.feed;

import cn.name.celestrong.signalbrief.config.FeedProperties;

import java.io.InputStream;

public interface FeedClient {

    InputStream fetch(FeedProperties.FeedSource source);
}
```

Create `src/main/java/cn/name/celestrong/signalbrief/feed/FeedFetchException.java`:

```java
package cn.name.celestrong.signalbrief.feed;

public class FeedFetchException extends RuntimeException {

    public FeedFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

Create `src/main/java/cn/name/celestrong/signalbrief/feed/HttpFeedClient.java`:

```java
package cn.name.celestrong.signalbrief.feed;

import cn.name.celestrong.signalbrief.config.FeedProperties;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class HttpFeedClient implements FeedClient {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public InputStream fetch(FeedProperties.FeedSource source) {
        try {
            HttpRequest request = HttpRequest.newBuilder(source.url())
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new FeedFetchException("Feed source returned HTTP " + response.statusCode() + ": " + source.name(), null);
            }
            return new ByteArrayInputStream(response.body());
        } catch (FeedFetchException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new FeedFetchException("Failed to fetch feed source: " + source.name(), ex);
        }
    }
}
```

- [ ] **Step 4: Add ingestion result and service**

Create `src/main/java/cn/name/celestrong/signalbrief/ingestion/FeedIngestionResult.java`:

```java
package cn.name.celestrong.signalbrief.ingestion;

public record FeedIngestionResult(
        int sourceCount,
        int fetchedCount,
        int insertedCount,
        int skippedCount,
        int failedSourceCount
) {
    public FeedIngestionResult plus(FeedIngestionResult other) {
        return new FeedIngestionResult(
                sourceCount + other.sourceCount,
                fetchedCount + other.fetchedCount,
                insertedCount + other.insertedCount,
                skippedCount + other.skippedCount,
                failedSourceCount + other.failedSourceCount
        );
    }

    public static FeedIngestionResult empty() {
        return new FeedIngestionResult(0, 0, 0, 0, 0);
    }
}
```

Create `src/main/java/cn/name/celestrong/signalbrief/ingestion/FeedIngestionService.java`:

```java
package cn.name.celestrong.signalbrief.ingestion;

import cn.name.celestrong.signalbrief.article.ArticleIngestionService;
import cn.name.celestrong.signalbrief.config.FeedProperties;
import cn.name.celestrong.signalbrief.feed.FetchedArticle;
import cn.name.celestrong.signalbrief.feed.FeedClient;
import cn.name.celestrong.signalbrief.feed.FeedParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

@Service
public class FeedIngestionService {

    private static final Logger log = LoggerFactory.getLogger(FeedIngestionService.class);

    private final FeedProperties feedProperties;
    private final FeedClient feedClient;
    private final FeedParser feedParser;
    private final ArticleIngestionService articleIngestionService;

    public FeedIngestionService(
            FeedProperties feedProperties,
            FeedClient feedClient,
            FeedParser feedParser,
            ArticleIngestionService articleIngestionService
    ) {
        this.feedProperties = feedProperties;
        this.feedClient = feedClient;
        this.feedParser = feedParser;
        this.articleIngestionService = articleIngestionService;
    }

    public FeedIngestionResult ingestEnabledFeeds() {
        FeedIngestionResult result = FeedIngestionResult.empty();
        for (FeedProperties.FeedSource source : feedProperties.enabledFeeds()) {
            result = result.plus(ingestSource(source));
        }
        log.info(
                "Feed ingestion completed: sources={}, fetched={}, inserted={}, skipped={}, failedSources={}",
                result.sourceCount(),
                result.fetchedCount(),
                result.insertedCount(),
                result.skippedCount(),
                result.failedSourceCount()
        );
        return result;
    }

    private FeedIngestionResult ingestSource(FeedProperties.FeedSource source) {
        try (InputStream inputStream = feedClient.fetch(source)) {
            List<FetchedArticle> articles = feedParser.parse(source, inputStream);
            int insertedCount = 0;
            int skippedCount = 0;
            for (FetchedArticle article : articles) {
                ArticleIngestionService.Result result = articleIngestionService.ingest(article);
                insertedCount += result.insertedCount();
                skippedCount += result.skippedCount();
            }
            return new FeedIngestionResult(1, articles.size(), insertedCount, skippedCount, 0);
        } catch (Exception ex) {
            log.warn("Failed to ingest feed source name={}, url={}", source.name(), source.url(), ex);
            return new FeedIngestionResult(1, 0, 0, 0, 1);
        }
    }
}
```

- [ ] **Step 5: Run orchestration test**

```bash
./mvnw -B -Dtest=FeedIngestionServiceTest test
```

Expected: build success.

- [ ] **Step 6: Commit orchestration task**

```bash
git add src/main/java/cn/name/celestrong/signalbrief/feed/FeedClient.java \
  src/main/java/cn/name/celestrong/signalbrief/feed/HttpFeedClient.java \
  src/main/java/cn/name/celestrong/signalbrief/feed/FeedFetchException.java \
  src/main/java/cn/name/celestrong/signalbrief/ingestion \
  src/test/java/cn/name/celestrong/signalbrief/ingestion/FeedIngestionServiceTest.java
git commit -m "feat(feed): 编排 RSS 抓取入库"
```

---

### Task 6: Documentation and Final Verification

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Add README configuration example**

Modify `README.md` by adding this section under “配置说明”:

````markdown
### RSS 源配置

RSS 源通过 `signal-brief.feeds` 配置：

```yaml
signal-brief:
  feeds:
    - name: Spring Blog
      url: https://spring.io/blog.atom
      category: FRAMEWORK
      enabled: true
```

第一版 RSS 源不入库；修改源配置后需要重启应用。
````

- [ ] **Step 2: Run unit test suite**

```bash
./mvnw -B test
```

Expected: build success.

- [ ] **Step 3: Run full verification with PostgreSQL**

Start PostgreSQL through the project Compose setup or an equivalent local database, then run:

```bash
SPRING_PROFILES_ACTIVE=test \
SPRING_DATASOURCE_USERNAME=celestrong \
SPRING_DATASOURCE_PASSWORD=123456 \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/signal_brief \
SERVER_PORT=8080 \
./mvnw -B verify
```

Expected: build success.

- [ ] **Step 4: Confirm no misspelled datasource variable remains**

```bash
rg --hidden -n "SPRING_DATASOURCE_UERNAME" -g '!target' -g '!.git' .
```

Expected: no output and exit code 1.

- [ ] **Step 5: Commit documentation and verification updates**

```bash
git add README.md
git commit -m "docs: 说明 RSS 源配置"
```

---

## Spec Coverage Checklist

- YAML feed source configuration: Task 1 and Task 6.
- ROME RSS / Atom parsing: Task 2.
- Article database schema: Task 3.
- Deduplication by `guid`, `url`, and `content_hash`: Task 3 and Task 4.
- PostgreSQL persistence through MyBatis: Task 3.
- Batch ingestion service and execution statistics: Task 5.
- Error handling that continues after one source fails: Task 5.
- Automated tests without 真实公网 RSS: Task 2, Task 4, Task 5.
- Final `test` and PostgreSQL-backed `verify`: Task 6.
