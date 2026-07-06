package cn.name.celestrong.signalbrief.mail;

import cn.name.celestrong.signalbrief.brief.BriefGenerationMapper;
import cn.name.celestrong.signalbrief.brief.BriefGenerationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@Sql(
        statements = "TRUNCATE TABLE brief_mail_delivery, brief_generation RESTART IDENTITY",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class BriefMailDeliveryMapperIT {

    @Autowired
    private BriefGenerationMapper briefGenerationMapper;

    @Autowired
    private BriefMailDeliveryMapper mapper;

    @Test
    void insertsPendingDeliveriesAndMarksTerminalStatesInIdOrder() {
        Long briefGenerationId = successfulBriefGeneration();
        Long sentDeliveryId = mapper.insertPending(
                briefGenerationId,
                "reader-a@example.com",
                "SignalBrief 技术半月报"
        );
        Long failedDeliveryId = mapper.insertPending(
                briefGenerationId,
                "reader-b@example.com",
                "SignalBrief 技术半月报"
        );
        Instant sentAt = Instant.parse("2026-07-16T02:00:00Z");

        int sentUpdated = mapper.markSent(sentDeliveryId, sentAt);
        int failedUpdated = mapper.markFailed(failedDeliveryId, "smtp down");

        List<BriefMailDelivery> deliveries = mapper.findByBriefGenerationId(briefGenerationId);

        assertEquals(1, sentUpdated);
        assertEquals(1, failedUpdated);
        assertEquals(List.of(sentDeliveryId, failedDeliveryId), deliveries.stream().map(BriefMailDelivery::id).toList());

        BriefMailDelivery sent = deliveries.getFirst();
        assertEquals(briefGenerationId, sent.briefGenerationId());
        assertEquals("reader-a@example.com", sent.recipient());
        assertEquals(BriefMailDeliveryStatus.SENT, sent.status());
        assertEquals("SignalBrief 技术半月报", sent.subject());
        assertNull(sent.errorSummary());
        assertEquals(sentAt, sent.sentAt());

        BriefMailDelivery failed = deliveries.get(1);
        assertEquals(BriefMailDeliveryStatus.FAILED, failed.status());
        assertEquals("smtp down", failed.errorSummary());
        assertNull(failed.sentAt());
    }

    @Test
    void doesNotOverwriteTerminalDeliveryRows() {
        Long briefGenerationId = successfulBriefGeneration();
        Long sentDeliveryId = mapper.insertPending(
                briefGenerationId,
                "sent@example.com",
                "SignalBrief 技术半月报"
        );
        Long failedDeliveryId = mapper.insertPending(
                briefGenerationId,
                "failed@example.com",
                "SignalBrief 技术半月报"
        );
        Instant sentAt = Instant.parse("2026-07-16T02:00:00Z");
        mapper.markSent(sentDeliveryId, sentAt);
        mapper.markFailed(failedDeliveryId, "smtp down");

        int sentToFailedUpdated = mapper.markFailed(sentDeliveryId, "late failure");
        int failedToSentUpdated = mapper.markSent(failedDeliveryId, sentAt.plusSeconds(60));

        BriefMailDelivery sent = mapper.findById(sentDeliveryId).orElseThrow();
        BriefMailDelivery failed = mapper.findById(failedDeliveryId).orElseThrow();

        assertEquals(0, sentToFailedUpdated);
        assertEquals(0, failedToSentUpdated);
        assertEquals(BriefMailDeliveryStatus.SENT, sent.status());
        assertEquals(sentAt, sent.sentAt());
        assertNull(sent.errorSummary());
        assertEquals(BriefMailDeliveryStatus.FAILED, failed.status());
        assertEquals("smtp down", failed.errorSummary());
        assertNull(failed.sentAt());
    }

    private Long successfulBriefGeneration() {
        Long id = briefGenerationMapper.insertGenerating(
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-16T00:00:00Z"),
                "# draft\n"
        );
        briefGenerationMapper.markSuccess(id, "## summary\n", Instant.parse("2026-07-16T01:00:00Z"));
        assertEquals(BriefGenerationStatus.SUCCESS, briefGenerationMapper.findById(id).orElseThrow().status());
        return id;
    }
}
