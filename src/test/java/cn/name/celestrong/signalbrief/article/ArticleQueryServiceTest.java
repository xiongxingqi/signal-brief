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
