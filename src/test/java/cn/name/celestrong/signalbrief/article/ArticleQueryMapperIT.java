package cn.name.celestrong.signalbrief.article;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 使用真实 SQL 验证简报查询口径，尤其是时间窗口、created_at 回退和稳定排序。
 */
@SpringBootTest
// 查询排序和时间回退依赖数据库状态，测试前清表能让每个用例独立。
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
        articleMapper.insertIfAbsent(sampleArticle(
                "inside-window",
                "https://example.com/inside",
                "hash-query-001",
                Instant.parse("2026-07-03T00:00:00Z")
        ));
        articleMapper.insertIfAbsent(sampleArticle(
                "outside-window",
                "https://example.com/outside",
                "hash-query-002",
                Instant.parse("2026-06-30T23:59:59Z")
        ));

        List<Article> articles = articleQueryMapper.findBriefCandidates(
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-16T00:00:00Z")
        );

        assertEquals(1, articles.size());
        assertEquals("Test Article inside-window", articles.getFirst().title());
    }

    @Test
    void fallsBackToCreatedAtWhenPublishedAtIsMissing() {
        articleMapper.insertIfAbsent(sampleArticle(
                "created-window",
                "https://example.com/created",
                "hash-query-003",
                null
        ));
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
        articleMapper.insertIfAbsent(sampleArticle(
                "java-old",
                "https://example.com/java-old",
                "hash-query-004",
                Instant.parse("2026-07-03T00:00:00Z"),
                ArticleCategory.JAVA
        ));
        articleMapper.insertIfAbsent(sampleArticle(
                "framework-new",
                "https://example.com/framework-new",
                "hash-query-005",
                Instant.parse("2026-07-05T00:00:00Z"),
                ArticleCategory.FRAMEWORK
        ));
        articleMapper.insertIfAbsent(sampleArticle(
                "framework-newer-id",
                "https://example.com/framework-newer-id",
                "hash-query-006",
                Instant.parse("2026-07-05T00:00:00Z"),
                ArticleCategory.FRAMEWORK
        ));

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

    private NewArticle sampleArticle(
            String guid,
            String url,
            String contentHash,
            Instant publishedAt,
            ArticleCategory category
    ) {
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
