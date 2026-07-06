package cn.name.celestrong.signalbrief.brief;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.test.context.jdbc.Sql;

import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Sql(
        statements = "TRUNCATE TABLE brief_mail_delivery, brief_generation RESTART IDENTITY",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class BriefGenerationMapperIT {

    @Autowired
    private BriefGenerationMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void insertsGeneratingThenMarksSuccessAndFindsArchive() {
        Instant start = Instant.parse("2026-07-01T00:00:00Z");
        Instant end = Instant.parse("2026-07-16T00:00:00Z");
        Instant completedAt = Instant.parse("2026-07-16T01:00:00Z");

        Long id = mapper.insertGenerating(start, end, "# draft\n");
        mapper.markSuccess(id, "## summary\n", completedAt);

        BriefGeneration archive = mapper.findById(id).orElseThrow();

        assertEquals(start, archive.startInclusive());
        assertEquals(end, archive.endExclusive());
        assertEquals(BriefGenerationStatus.SUCCESS, archive.status());
        assertEquals("# draft\n", archive.draftMarkdown());
        assertEquals("## summary\n", archive.summaryMarkdown());
        assertEquals(completedAt, archive.completedAt());
    }

    @Test
    void insertsMultipleArchivesForSameWindowAndKeepsSchemaComments() {
        Instant start = Instant.parse("2026-07-01T00:00:00Z");
        Instant end = Instant.parse("2026-07-16T00:00:00Z");

        Long first = mapper.insertGenerating(start, end, "# first\n");
        Long second = mapper.insertGenerating(start, end, "# second\n");

        assertNotEquals(first, second);
        assertEquals(
                "保存一次简报生成尝试，包括 Markdown 草稿、AI 摘要、状态和错误摘要。",
                jdbcTemplate.queryForObject(
                        "SELECT obj_description('brief_generation'::regclass)",
                        String.class
                )
        );
    }

    @Test
    void marksFailedArchiveWithErrorContext() {
        Instant start = Instant.parse("2026-07-01T00:00:00Z");
        Instant end = Instant.parse("2026-07-16T00:00:00Z");
        Instant completedAt = Instant.parse("2026-07-16T01:00:00Z");

        Long id = mapper.insertGenerating(start, end, "# draft\n");
        int updated = mapper.markFailed(id, "provider down", completedAt);

        BriefGeneration archive = mapper.findById(id).orElseThrow();

        assertEquals(1, updated);
        assertEquals(BriefGenerationStatus.FAILED, archive.status());
        assertEquals("provider down", archive.errorSummary());
        assertEquals(completedAt, archive.completedAt());
        assertNull(archive.summaryMarkdown());
    }

    @Test
    void findsRecentArchivesInDescendingIdOrderWithLimit() {
        Instant start = Instant.parse("2026-07-01T00:00:00Z");
        Instant end = Instant.parse("2026-07-16T00:00:00Z");

        Long first = mapper.insertGenerating(start, end, "# first\n");
        Long second = mapper.insertGenerating(start.plusSeconds(60), end.plusSeconds(60), "# second\n");
        Long third = mapper.insertGenerating(start.plusSeconds(120), end.plusSeconds(120), "# third\n");

        List<BriefGeneration> archives = mapper.findRecent(2);

        assertEquals(List.of(third, second), archives.stream().map(BriefGeneration::id).toList());
        assertEquals("# third\n", archives.getFirst().draftMarkdown());
        assertEquals(BriefGenerationStatus.GENERATING, archives.getFirst().status());
        assertNotEquals(first, archives.getFirst().id());
    }

    @Test
    void ignoresFailureTransitionAfterSuccess() {
        Instant start = Instant.parse("2026-07-01T00:00:00Z");
        Instant end = Instant.parse("2026-07-16T00:00:00Z");
        Instant successCompletedAt = Instant.parse("2026-07-16T01:00:00Z");

        Long id = mapper.insertGenerating(start, end, "# draft\n");
        mapper.markSuccess(id, "## summary\n", successCompletedAt);
        int updated = mapper.markFailed(id, "provider down", successCompletedAt.plusSeconds(60));

        BriefGeneration archive = mapper.findById(id).orElseThrow();

        assertEquals(0, updated);
        assertEquals(BriefGenerationStatus.SUCCESS, archive.status());
        assertEquals("## summary\n", archive.summaryMarkdown());
        assertNull(archive.errorSummary());
        assertEquals(successCompletedAt, archive.completedAt());
    }

    @Test
    void ignoresSuccessTransitionAfterFailure() {
        Instant start = Instant.parse("2026-07-01T00:00:00Z");
        Instant end = Instant.parse("2026-07-16T00:00:00Z");
        Instant failedCompletedAt = Instant.parse("2026-07-16T01:00:00Z");

        Long id = mapper.insertGenerating(start, end, "# draft\n");
        mapper.markFailed(id, "provider down", failedCompletedAt);
        int updated = mapper.markSuccess(id, "## summary\n", failedCompletedAt.plusSeconds(60));

        BriefGeneration archive = mapper.findById(id).orElseThrow();

        assertEquals(0, updated);
        assertEquals(BriefGenerationStatus.FAILED, archive.status());
        assertNull(archive.summaryMarkdown());
        assertEquals("provider down", archive.errorSummary());
        assertEquals(failedCompletedAt, archive.completedAt());
    }

    @Test
    void rejectsInvalidBriefGenerationStatusContext() {
        Instant start = Instant.parse("2026-07-01T00:00:00Z");
        Instant end = Instant.parse("2026-07-16T00:00:00Z");
        Instant completedAt = Instant.parse("2026-07-16T01:00:00Z");

        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        """
                                INSERT INTO brief_generation (
                                    start_inclusive,
                                    end_exclusive,
                                    status,
                                    draft_markdown,
                                    completed_at
                                ) VALUES (
                                    ?,
                                    ?,
                                    'SUCCESS',
                                    ?,
                                    ?
                                )
                                """,
                        timestampWithTimeZone(start),
                        timestampWithTimeZone(end),
                        "# draft\n",
                        timestampWithTimeZone(completedAt)
                )
        );
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        """
                                INSERT INTO brief_generation (
                                    start_inclusive,
                                    end_exclusive,
                                    status,
                                    draft_markdown,
                                    completed_at
                                ) VALUES (
                                    ?,
                                    ?,
                                    'FAILED',
                                    ?,
                                    ?
                                )
                                """,
                        timestampWithTimeZone(start),
                        timestampWithTimeZone(end),
                        "# draft\n",
                        timestampWithTimeZone(completedAt)
                )
        );
    }

    @Test
    void rejectsInvalidMailDeliveryStatusContext() {
        Instant start = Instant.parse("2026-07-01T00:00:00Z");
        Instant end = Instant.parse("2026-07-16T00:00:00Z");
        Long id = mapper.insertGenerating(start, end, "# draft\n");

        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        """
                                INSERT INTO brief_mail_delivery (
                                    brief_generation_id,
                                    recipient,
                                    status,
                                    subject
                                ) VALUES (
                                    ?,
                                    ?,
                                    'SENT',
                                    ?
                                )
                                """,
                        id,
                        "reader@example.com",
                        "SignalBrief 技术半月报"
                )
        );
    }

    private static SqlParameterValue timestampWithTimeZone(Instant instant) {
        return new SqlParameterValue(
                Types.TIMESTAMP_WITH_TIMEZONE,
                OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
        );
    }
}
