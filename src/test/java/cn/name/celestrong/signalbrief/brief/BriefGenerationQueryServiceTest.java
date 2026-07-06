package cn.name.celestrong.signalbrief.brief;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BriefGenerationQueryServiceTest {

    @Test
    void findsRecentArchivesWithDefaultLimit() {
        RecordingBriefGenerationMapper mapper = new RecordingBriefGenerationMapper();
        BriefGenerationQueryService service = new BriefGenerationQueryService(mapper);

        service.findRecentArchives(null);

        assertEquals(20, mapper.limit);
    }

    @Test
    void clampsRecentArchivesLimitToMinimum() {
        RecordingBriefGenerationMapper mapper = new RecordingBriefGenerationMapper();
        BriefGenerationQueryService service = new BriefGenerationQueryService(mapper);

        service.findRecentArchives(0);

        assertEquals(1, mapper.limit);
    }

    @Test
    void clampsRecentArchivesLimitToMaximum() {
        RecordingBriefGenerationMapper mapper = new RecordingBriefGenerationMapper();
        BriefGenerationQueryService service = new BriefGenerationQueryService(mapper);

        service.findRecentArchives(101);

        assertEquals(100, mapper.limit);
    }

    @Test
    void findsArchiveById() {
        RecordingBriefGenerationMapper mapper = new RecordingBriefGenerationMapper();
        BriefGenerationQueryService service = new BriefGenerationQueryService(mapper);

        BriefGeneration archive = service.findArchive(100L);

        assertSame(mapper.archive, archive);
        assertEquals(100L, mapper.id);
    }

    @Test
    void rejectsMissingArchive() {
        RecordingBriefGenerationMapper mapper = new RecordingBriefGenerationMapper();
        BriefGenerationQueryService service = new BriefGenerationQueryService(mapper);

        BriefGenerationNotFoundException exception = assertThrows(
                BriefGenerationNotFoundException.class,
                () -> service.findArchive(404L)
        );

        assertEquals("简报归档记录不存在: 404", exception.getMessage());
    }

    static class RecordingBriefGenerationMapper implements BriefGenerationMapper {

        private final BriefGeneration archive = archive();
        private Integer limit;
        private Long id;

        @Override
        public Long insertGenerating(Instant startInclusive, Instant endExclusive, String draftMarkdown) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int markSuccess(Long id, String summaryMarkdown, Instant completedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int markFailed(Long id, String errorSummary, Instant completedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<BriefGeneration> findById(Long id) {
            this.id = id;
            if (id.equals(404L)) {
                return Optional.empty();
            }
            return Optional.of(archive);
        }

        @Override
        public List<BriefGeneration> findRecent(int limit) {
            this.limit = limit;
            return List.of(archive);
        }

        private BriefGeneration archive() {
            Instant now = Instant.parse("2026-07-16T01:00:00Z");
            return new BriefGeneration(
                    100L,
                    Instant.parse("2026-07-01T00:00:00Z"),
                    Instant.parse("2026-07-16T00:00:00Z"),
                    BriefGenerationStatus.SUCCESS,
                    "# SignalBrief 技术半月报\n",
                    "## AI 摘要\n",
                    null,
                    now,
                    now,
                    now
            );
        }
    }
}
