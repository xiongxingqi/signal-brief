package cn.name.celestrong.signalbrief.mail;

import cn.name.celestrong.signalbrief.brief.BriefGeneration;
import cn.name.celestrong.signalbrief.brief.BriefGenerationMapper;
import cn.name.celestrong.signalbrief.brief.BriefGenerationNotFoundException;
import cn.name.celestrong.signalbrief.brief.BriefGenerationStatus;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BriefMailDeliveryQueryServiceTest {

    @Test
    void findsDeliveriesForExistingArchive() {
        RecordingBriefGenerationMapper briefGenerationMapper = new RecordingBriefGenerationMapper();
        RecordingBriefMailDeliveryMapper deliveryMapper = new RecordingBriefMailDeliveryMapper();
        BriefMailDeliveryQueryService service = new BriefMailDeliveryQueryService(
                briefGenerationMapper,
                deliveryMapper
        );

        List<BriefMailDelivery> deliveries = service.findDeliveries(100L);

        assertSame(deliveryMapper.deliveries, deliveries);
        assertEquals(100L, briefGenerationMapper.id);
        assertEquals(100L, deliveryMapper.briefGenerationId);
    }

    @Test
    void returnsEmptyDeliveriesForArchiveWithoutMailAttempts() {
        RecordingBriefGenerationMapper briefGenerationMapper = new RecordingBriefGenerationMapper();
        RecordingBriefMailDeliveryMapper deliveryMapper = new RecordingBriefMailDeliveryMapper();
        deliveryMapper.deliveries = List.of();
        BriefMailDeliveryQueryService service = new BriefMailDeliveryQueryService(
                briefGenerationMapper,
                deliveryMapper
        );

        List<BriefMailDelivery> deliveries = service.findDeliveries(100L);

        assertEquals(List.of(), deliveries);
    }

    @Test
    void rejectsMissingArchive() {
        RecordingBriefGenerationMapper briefGenerationMapper = new RecordingBriefGenerationMapper();
        RecordingBriefMailDeliveryMapper deliveryMapper = new RecordingBriefMailDeliveryMapper();
        BriefMailDeliveryQueryService service = new BriefMailDeliveryQueryService(
                briefGenerationMapper,
                deliveryMapper
        );

        BriefGenerationNotFoundException exception = assertThrows(
                BriefGenerationNotFoundException.class,
                () -> service.findDeliveries(404L)
        );

        assertEquals("简报归档记录不存在: 404", exception.getMessage());
    }

    static class RecordingBriefGenerationMapper implements BriefGenerationMapper {

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
            Instant now = Instant.parse("2026-07-16T01:00:00Z");
            return Optional.of(new BriefGeneration(
                    id,
                    Instant.parse("2026-07-01T00:00:00Z"),
                    Instant.parse("2026-07-16T00:00:00Z"),
                    BriefGenerationStatus.SUCCESS,
                    "# SignalBrief 技术半月报\n",
                    "## AI 摘要\n",
                    null,
                    now,
                    now,
                    now
            ));
        }

        @Override
        public List<BriefGeneration> findRecent(int limit) {
            throw new UnsupportedOperationException();
        }
    }

    static class RecordingBriefMailDeliveryMapper implements BriefMailDeliveryMapper {

        private List<BriefMailDelivery> deliveries = List.of(delivery());
        private Long briefGenerationId;

        @Override
        public Long insertPending(Long briefGenerationId, String recipient, String subject) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int markSent(Long id, Instant sentAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int markFailed(Long id, String errorSummary) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<BriefMailDelivery> findById(Long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<BriefMailDelivery> findByBriefGenerationId(Long briefGenerationId) {
            this.briefGenerationId = briefGenerationId;
            return deliveries;
        }

        private static BriefMailDelivery delivery() {
            Instant now = Instant.parse("2026-07-16T02:00:00Z");
            return new BriefMailDelivery(
                    200L,
                    100L,
                    "reader@example.com",
                    BriefMailDeliveryStatus.SENT,
                    "SignalBrief 技术半月报 2026-07-01 至 2026-07-16",
                    null,
                    now,
                    now,
                    now
            );
        }
    }
}
