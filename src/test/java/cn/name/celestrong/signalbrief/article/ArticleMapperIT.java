package cn.name.celestrong.signalbrief.article;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用真实数据库约束验证 MyBatis 写入行为，覆盖应用层之外的唯一索引兜底。
 */
@SpringBootTest
// 每个用例独占一张空表，避免唯一索引冲突和自增 id 影响后续断言。
@Sql(statements = "TRUNCATE TABLE article RESTART IDENTITY", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ArticleMapperIT {

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void insertsAndFindsArticleByUrl() {
        NewArticle article = sampleArticle("mapper-url-1", "https://example.com/article-1", "hash-001");

        int insertedRows = articleMapper.insertIfAbsent(article);

        assertEquals(1, insertedRows);
        assertTrue(articleMapper.existsByUrl("https://example.com/article-1"));
        String contentText = jdbcTemplate.queryForObject(
                "SELECT content_text FROM article WHERE url = ?",
                String.class,
                "https://example.com/article-1"
        );
        assertEquals("Body text mapper-url-1", contentText);
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

    @Test
    void fillsOnlyMissingSummaryAndContentTextForDuplicateGuid() {
        NewArticle first = new NewArticle(
                "Test Source",
                "https://example.com/feed.xml",
                ArticleCategory.JAVA,
                "Test Article missing-content",
                "https://example.com/missing-content",
                "missing-content-guid",
                Instant.parse("2026-07-03T00:00:00Z"),
                null,
                null,
                "hash-missing-content"
        );
        assertEquals(1, articleMapper.insertIfAbsent(first));

        int updatedRows = articleMapper.fillMissingContentBySourceNameAndGuid(
                "Test Source",
                "missing-content-guid",
                "Filled summary",
                "Filled content"
        );

        assertEquals(1, updatedRows);
        assertEquals("Filled summary", jdbcTemplate.queryForObject(
                "SELECT summary FROM article WHERE guid = ?",
                String.class,
                "missing-content-guid"
        ));
        assertEquals("Filled content", jdbcTemplate.queryForObject(
                "SELECT content_text FROM article WHERE guid = ?",
                String.class,
                "missing-content-guid"
        ));
    }

    @Test
    void doesNotOverwriteExistingSummaryOrContentText() {
        NewArticle first = sampleArticle("existing-content", "https://example.com/existing-content", "hash-existing-content");
        assertEquals(1, articleMapper.insertIfAbsent(first));

        int updatedRows = articleMapper.fillMissingContentBySourceNameAndGuid(
                "Test Source",
                "existing-content",
                "Replacement summary",
                "Replacement content"
        );

        assertEquals(0, updatedRows);
        assertEquals("Summary", jdbcTemplate.queryForObject(
                "SELECT summary FROM article WHERE guid = ?",
                String.class,
                "existing-content"
        ));
        assertEquals("Body text existing-content", jdbcTemplate.queryForObject(
                "SELECT content_text FROM article WHERE guid = ?",
                String.class,
                "existing-content"
        ));
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
                "Body text " + guid,
                contentHash
        );
    }
}
