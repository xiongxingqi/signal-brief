package cn.name.celestrong.signalbrief.article;

import cn.name.celestrong.signalbrief.feed.FetchedArticle;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 这些测试需要锁定去重短路顺序，因此使用可记录调用次数的手写 fake。
 */
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
                URI.create("https://example.com/feed.xml"),
                ArticleCategory.JAVA,
                title,
                url,
                guid,
                Instant.parse("2026-07-03T00:00:00Z"),
                "Summary",
                null
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

        @Override
        public int fillMissingContentBySourceNameAndGuid(
                String sourceName,
                String guid,
                String summary,
                String contentText
        ) {
            return 0;
        }

        @Override
        public int fillMissingContentByUrl(String url, String summary, String contentText) {
            return 0;
        }

        @Override
        public int fillMissingContentByContentHash(String contentHash, String summary, String contentText) {
            return 0;
        }
    }
}
