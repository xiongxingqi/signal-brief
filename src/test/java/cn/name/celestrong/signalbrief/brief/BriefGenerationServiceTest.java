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
    void generatesMarkdownFromBriefCandidatesInRequestedWindow() {
        Instant startInclusive = Instant.parse("2026-07-01T00:00:00Z");
        Instant endExclusive = Instant.parse("2026-07-16T00:00:00Z");
        RecordingArticleQueryMapper mapper = new RecordingArticleQueryMapper(List.of(article()));
        BriefGenerationService service = new BriefGenerationService(
                new ArticleQueryService(mapper),
                new BriefMarkdownRenderer()
        );

        String markdown = service.generate(startInclusive, endExclusive);

        assertEquals(startInclusive, mapper.startInclusive);
        assertEquals(endExclusive, mapper.endExclusive);
        assertTrue(markdown.contains("# SignalBrief 技术半月报"));
        assertTrue(markdown.contains("## JAVA"));
        assertTrue(markdown.contains("Java 25 发布"));
    }

    private Article article() {
        return new Article(
                1L,
                "InfoQ",
                "https://example.com/feed.xml",
                ArticleCategory.JAVA,
                "Java 25 发布",
                "https://example.com/java-25",
                "guid",
                Instant.parse("2026-07-02T09:15:30Z"),
                "新版本发布摘要。",
                null,
                "hash",
                Instant.parse("2026-07-04T00:00:00Z"),
                Instant.parse("2026-07-04T00:00:00Z")
        );
    }

    private static class RecordingArticleQueryMapper implements ArticleQueryMapper {

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
