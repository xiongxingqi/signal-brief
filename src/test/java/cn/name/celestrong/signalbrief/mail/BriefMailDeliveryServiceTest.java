package cn.name.celestrong.signalbrief.mail;

import cn.name.celestrong.signalbrief.brief.BriefGeneration;
import cn.name.celestrong.signalbrief.brief.BriefGenerationMapper;
import cn.name.celestrong.signalbrief.brief.BriefGenerationNotFoundException;
import cn.name.celestrong.signalbrief.brief.BriefGenerationNotReadyException;
import cn.name.celestrong.signalbrief.brief.BriefGenerationStatus;
import cn.name.celestrong.signalbrief.config.BriefMailProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class BriefMailDeliveryServiceTest {

    private static final Long BRIEF_GENERATION_ID = 100L;
    private static final Instant START_INCLUSIVE = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant END_EXCLUSIVE = Instant.parse("2026-07-16T00:00:00Z");
    private static final Instant SENT_AT = Instant.parse("2026-07-16T02:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(SENT_AT, ZoneOffset.UTC);

    @Test
    void sendsToAllRecipientsSuccessfully() {
        RecordingBriefGenerationMapper briefGenerationMapper = new RecordingBriefGenerationMapper(
                successfulArchive(BRIEF_GENERATION_ID)
        );
        RecordingBriefMailDeliveryMapper deliveryMapper = new RecordingBriefMailDeliveryMapper();
        RecordingBriefMailSender sender = new RecordingBriefMailSender();
        BriefMailDeliveryService service = service(
                enabledProperties("reader-a@example.com", "reader-b@example.com"),
                new RecordingObjectProvider(sender),
                briefGenerationMapper,
                deliveryMapper
        );

        BriefMailDeliveryResult result = service.deliver(BRIEF_GENERATION_ID);

        assertEquals(BRIEF_GENERATION_ID, result.briefGenerationId());
        assertEquals(2, result.deliveries().size());
        assertEquals(
                List.of(BriefMailDeliveryStatus.SENT, BriefMailDeliveryStatus.SENT),
                result.deliveries().stream().map(BriefMailDelivery::status).toList()
        );
        assertEquals(
                List.of("reader-a@example.com", "reader-b@example.com"),
                sender.requests.stream().map(SendRequest::recipient).toList()
        );
        assertEquals(
                List.of(
                        "SignalBrief 技术半月报 2026-07-01 至 2026-07-16",
                        "SignalBrief 技术半月报 2026-07-01 至 2026-07-16"
                ),
                sender.requests.stream().map(SendRequest::subject).toList()
        );
        assertEquals(List.of(SENT_AT, SENT_AT), result.deliveries().stream().map(BriefMailDelivery::sentAt).toList());
        assertEquals(2, deliveryMapper.sentCalls);
        assertEquals(0, deliveryMapper.failedCalls);
    }

    @Test
    void recordsOneFailedRecipientWithoutBlockingOtherRecipients(CapturedOutput output) {
        RecordingBriefGenerationMapper briefGenerationMapper = new RecordingBriefGenerationMapper(
                successfulArchive(BRIEF_GENERATION_ID)
        );
        RecordingBriefMailDeliveryMapper deliveryMapper = new RecordingBriefMailDeliveryMapper();
        RecordingBriefMailSender sender = new RecordingBriefMailSender();
        sender.failures.put("reader-b@example.com", new RuntimeException("smtp down"));
        BriefMailDeliveryService service = service(
                enabledProperties("reader-a@example.com", "reader-b@example.com", "reader-c@example.com"),
                new RecordingObjectProvider(sender),
                briefGenerationMapper,
                deliveryMapper
        );

        BriefMailDeliveryResult result = service.deliver(BRIEF_GENERATION_ID);

        assertEquals(
                List.of("reader-a@example.com", "reader-b@example.com", "reader-c@example.com"),
                sender.requests.stream().map(SendRequest::recipient).toList()
        );
        assertEquals(
                List.of(
                        BriefMailDeliveryStatus.SENT,
                        BriefMailDeliveryStatus.FAILED,
                        BriefMailDeliveryStatus.SENT
                ),
                result.deliveries().stream().map(BriefMailDelivery::status).toList()
        );
        assertEquals("邮件发送失败", result.deliveries().get(1).errorSummary());
        assertEquals(2, deliveryMapper.sentCalls);
        assertEquals(1, deliveryMapper.failedCalls);
        assertTrue(output.getOut().contains("Brief mail delivery started: briefGenerationId=100"));
        assertTrue(output.getOut().contains("recipients=3"));
        assertTrue(output.getOut().contains("Brief mail delivery sent: briefGenerationId=100, deliveryId=200"));
        assertTrue(output.getOut().contains("recipient=reader-a@example.com"));
        assertTrue(output.getOut().contains("Brief mail delivery failed: briefGenerationId=100, deliveryId=201"));
        assertTrue(output.getOut().contains("recipient=reader-b@example.com"));
        assertTrue(output.getOut().contains("errorType=RuntimeException"));
        assertTrue(output.getOut().contains("Brief mail delivery completed: briefGenerationId=100, sent=2, failed=1"));
        assertFalse(output.getOut().contains("## summary"));
        assertFalse(output.getOut().contains("smtp down"));
    }

    @Test
    void rejectsNonSuccessArchive() {
        RecordingBriefGenerationMapper briefGenerationMapper = new RecordingBriefGenerationMapper(new BriefGeneration(
                BRIEF_GENERATION_ID,
                START_INCLUSIVE,
                END_EXCLUSIVE,
                BriefGenerationStatus.GENERATING,
                "# draft\n",
                null,
                null,
                Instant.parse("2026-07-16T00:00:00Z"),
                Instant.parse("2026-07-16T00:00:00Z"),
                null
        ));
        RecordingBriefMailDeliveryMapper deliveryMapper = new RecordingBriefMailDeliveryMapper();
        RecordingBriefMailSender sender = new RecordingBriefMailSender();
        BriefMailDeliveryService service = service(
                enabledProperties("reader@example.com"),
                new RecordingObjectProvider(sender),
                briefGenerationMapper,
                deliveryMapper
        );

        BriefGenerationNotReadyException exception = assertThrows(
                BriefGenerationNotReadyException.class,
                () -> service.deliver(BRIEF_GENERATION_ID)
        );

        assertEquals("简报归档记录不可发送: 100, status=GENERATING", exception.getMessage());
        assertEquals(0, deliveryMapper.insertCalls);
        assertTrue(sender.requests.isEmpty());
    }

    @Test
    void rejectsMissingArchive() {
        RecordingBriefGenerationMapper briefGenerationMapper = new RecordingBriefGenerationMapper(Optional.empty());
        RecordingBriefMailDeliveryMapper deliveryMapper = new RecordingBriefMailDeliveryMapper();
        RecordingBriefMailSender sender = new RecordingBriefMailSender();
        BriefMailDeliveryService service = service(
                enabledProperties("reader@example.com"),
                new RecordingObjectProvider(sender),
                briefGenerationMapper,
                deliveryMapper
        );

        BriefGenerationNotFoundException exception = assertThrows(
                BriefGenerationNotFoundException.class,
                () -> service.deliver(BRIEF_GENERATION_ID)
        );

        assertEquals("简报归档记录不存在: 100", exception.getMessage());
        assertEquals(1, briefGenerationMapper.findByIdCalls);
        assertEquals(0, deliveryMapper.insertCalls);
        assertTrue(sender.requests.isEmpty());
    }

    @Test
    void rejectsMissingArchiveBeforeDisabledMail() {
        RecordingBriefGenerationMapper briefGenerationMapper = new RecordingBriefGenerationMapper(Optional.empty());
        RecordingBriefMailDeliveryMapper deliveryMapper = new RecordingBriefMailDeliveryMapper();
        RecordingBriefMailSender sender = new RecordingBriefMailSender();
        RecordingObjectProvider senderProvider = new RecordingObjectProvider(sender);
        BriefMailDeliveryService service = service(
                disabledProperties("reader@example.com"),
                senderProvider,
                briefGenerationMapper,
                deliveryMapper
        );

        BriefGenerationNotFoundException exception = assertThrows(
                BriefGenerationNotFoundException.class,
                () -> service.deliver(BRIEF_GENERATION_ID)
        );

        assertEquals("简报归档记录不存在: 100", exception.getMessage());
        assertEquals(1, briefGenerationMapper.findByIdCalls);
        assertFalse(senderProvider.accessed);
        assertEquals(0, deliveryMapper.insertCalls);
        assertTrue(sender.requests.isEmpty());
    }

    @Test
    void rejectsNonSuccessArchiveBeforeDisabledMail() {
        RecordingBriefGenerationMapper briefGenerationMapper = new RecordingBriefGenerationMapper(new BriefGeneration(
                BRIEF_GENERATION_ID,
                START_INCLUSIVE,
                END_EXCLUSIVE,
                BriefGenerationStatus.FAILED,
                "# draft\n",
                null,
                "provider down",
                Instant.parse("2026-07-16T00:00:00Z"),
                Instant.parse("2026-07-16T01:00:00Z"),
                Instant.parse("2026-07-16T01:00:00Z")
        ));
        RecordingBriefMailDeliveryMapper deliveryMapper = new RecordingBriefMailDeliveryMapper();
        RecordingBriefMailSender sender = new RecordingBriefMailSender();
        RecordingObjectProvider senderProvider = new RecordingObjectProvider(sender);
        BriefMailDeliveryService service = service(
                disabledProperties("reader@example.com"),
                senderProvider,
                briefGenerationMapper,
                deliveryMapper
        );

        BriefGenerationNotReadyException exception = assertThrows(
                BriefGenerationNotReadyException.class,
                () -> service.deliver(BRIEF_GENERATION_ID)
        );

        assertEquals("简报归档记录不可发送: 100, status=FAILED", exception.getMessage());
        assertEquals(1, briefGenerationMapper.findByIdCalls);
        assertFalse(senderProvider.accessed);
        assertEquals(0, deliveryMapper.insertCalls);
        assertTrue(sender.requests.isEmpty());
    }

    @Test
    void rejectsDisabledMailWithoutAccessingSenderProvider() {
        RecordingBriefGenerationMapper briefGenerationMapper = new RecordingBriefGenerationMapper(
                successfulArchive(BRIEF_GENERATION_ID)
        );
        RecordingBriefMailDeliveryMapper deliveryMapper = new RecordingBriefMailDeliveryMapper();
        RecordingObjectProvider senderProvider = new RecordingObjectProvider(new RecordingBriefMailSender());
        BriefMailDeliveryService service = service(
                new BriefMailProperties(false, null, List.of("reader@example.com"), "SignalBrief 技术半月报"),
                senderProvider,
                briefGenerationMapper,
                deliveryMapper
        );

        BriefMailUnavailableException exception = assertThrows(
                BriefMailUnavailableException.class,
                () -> service.deliver(BRIEF_GENERATION_ID)
        );

        assertEquals("邮件发送能力未启用", exception.getMessage());
        assertFalse(senderProvider.accessed);
        assertEquals(1, briefGenerationMapper.findByIdCalls);
        assertEquals(0, deliveryMapper.insertCalls);
    }

    @Test
    void rejectsEnabledMailWhenSenderProviderIsEmpty() {
        RecordingBriefGenerationMapper briefGenerationMapper = new RecordingBriefGenerationMapper(
                successfulArchive(BRIEF_GENERATION_ID)
        );
        RecordingBriefMailDeliveryMapper deliveryMapper = new RecordingBriefMailDeliveryMapper();
        RecordingObjectProvider senderProvider = new RecordingObjectProvider(null);
        BriefMailDeliveryService service = service(
                enabledProperties("reader@example.com"),
                senderProvider,
                briefGenerationMapper,
                deliveryMapper
        );

        BriefMailUnavailableException exception = assertThrows(
                BriefMailUnavailableException.class,
                () -> service.deliver(BRIEF_GENERATION_ID)
        );

        assertEquals("邮件发送器未配置", exception.getMessage());
        assertTrue(senderProvider.accessed);
        assertEquals(1, briefGenerationMapper.findByIdCalls);
        assertEquals(0, deliveryMapper.insertCalls);
    }

    @Test
    void rejectsEnabledMailWhenSenderIsUnavailable() {
        RecordingBriefGenerationMapper briefGenerationMapper = new RecordingBriefGenerationMapper(
                successfulArchive(BRIEF_GENERATION_ID)
        );
        RecordingBriefMailDeliveryMapper deliveryMapper = new RecordingBriefMailDeliveryMapper();
        RecordingObjectProvider senderProvider = new RecordingObjectProvider(new UnavailableBriefMailSender());
        BriefMailDeliveryService service = service(
                enabledProperties("reader@example.com"),
                senderProvider,
                briefGenerationMapper,
                deliveryMapper
        );

        BriefMailUnavailableException exception = assertThrows(
                BriefMailUnavailableException.class,
                () -> service.deliver(BRIEF_GENERATION_ID)
        );

        assertEquals("邮件发送器未配置", exception.getMessage());
        assertTrue(senderProvider.accessed);
        assertEquals(1, briefGenerationMapper.findByIdCalls);
        assertEquals(0, deliveryMapper.insertCalls);
    }

    @Test
    void failsWhenSentDeliveryCannotBeMarkedSent() {
        RecordingBriefGenerationMapper briefGenerationMapper = new RecordingBriefGenerationMapper(
                successfulArchive(BRIEF_GENERATION_ID)
        );
        RecordingBriefMailDeliveryMapper deliveryMapper = new RecordingBriefMailDeliveryMapper();
        deliveryMapper.sentUpdateCount = 0;
        RecordingBriefMailSender sender = new RecordingBriefMailSender();
        BriefMailDeliveryService service = service(
                enabledProperties("reader@example.com"),
                new RecordingObjectProvider(sender),
                briefGenerationMapper,
                deliveryMapper
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.deliver(BRIEF_GENERATION_ID)
        );

        assertEquals("邮件发送状态更新失败: 200", exception.getMessage());
        assertEquals(1, sender.requests.size());
        assertEquals(1, deliveryMapper.sentCalls);
        assertEquals(0, deliveryMapper.findByIdCalls);
    }

    @Test
    void failsWhenSentDeliveryCannotBeLoadedAfterSuccessfulUpdate() {
        RecordingBriefGenerationMapper briefGenerationMapper = new RecordingBriefGenerationMapper(
                successfulArchive(BRIEF_GENERATION_ID)
        );
        RecordingBriefMailDeliveryMapper deliveryMapper = new RecordingBriefMailDeliveryMapper();
        deliveryMapper.findByIdEmpty = true;
        RecordingBriefMailSender sender = new RecordingBriefMailSender();
        BriefMailDeliveryService service = service(
                enabledProperties("reader@example.com"),
                new RecordingObjectProvider(sender),
                briefGenerationMapper,
                deliveryMapper
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.deliver(BRIEF_GENERATION_ID)
        );

        assertEquals("邮件发送记录不存在: 200", exception.getMessage());
        assertEquals(1, sender.requests.size());
        assertEquals(1, deliveryMapper.sentCalls);
        assertEquals(1, deliveryMapper.findByIdCalls);
    }

    @Test
    void failsWhenFailedDeliveryCannotBeMarkedFailed() {
        RecordingBriefGenerationMapper briefGenerationMapper = new RecordingBriefGenerationMapper(
                successfulArchive(BRIEF_GENERATION_ID)
        );
        RecordingBriefMailDeliveryMapper deliveryMapper = new RecordingBriefMailDeliveryMapper();
        deliveryMapper.failedUpdateCount = 0;
        RecordingBriefMailSender sender = new RecordingBriefMailSender();
        RuntimeException sendFailure = new RuntimeException("smtp down");
        sender.failures.put("reader@example.com", sendFailure);
        BriefMailDeliveryService service = service(
                enabledProperties("reader@example.com"),
                new RecordingObjectProvider(sender),
                briefGenerationMapper,
                deliveryMapper
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.deliver(BRIEF_GENERATION_ID)
        );

        assertEquals("邮件发送失败状态更新失败: 200", exception.getMessage());
        assertSame(sendFailure, exception.getCause());
        assertEquals(1, deliveryMapper.failedCalls);
        assertEquals(0, deliveryMapper.findByIdCalls);
    }

    @Test
    void doesNotPersistRawFailureMessage() {
        RecordingBriefGenerationMapper briefGenerationMapper = new RecordingBriefGenerationMapper(
                successfulArchive(BRIEF_GENERATION_ID)
        );
        RecordingBriefMailDeliveryMapper deliveryMapper = new RecordingBriefMailDeliveryMapper();
        RecordingBriefMailSender sender = new RecordingBriefMailSender();
        sender.failures.put("blank@example.com", new RuntimeException(" "));
        sender.failures.put("sensitive@example.com", new RuntimeException("535 user=noreply@example.com password=secret"));
        BriefMailDeliveryService service = service(
                enabledProperties("blank@example.com", "sensitive@example.com"),
                new RecordingObjectProvider(sender),
                briefGenerationMapper,
                deliveryMapper
        );

        BriefMailDeliveryResult result = service.deliver(BRIEF_GENERATION_ID);

        assertEquals(
                List.of(BriefMailDeliveryStatus.FAILED, BriefMailDeliveryStatus.FAILED),
                result.deliveries().stream().map(BriefMailDelivery::status).toList()
        );
        assertEquals("邮件发送失败", result.deliveries().getFirst().errorSummary());
        assertEquals("邮件发送失败", result.deliveries().get(1).errorSummary());
    }

    private static BriefMailDeliveryService service(
            BriefMailProperties properties,
            ObjectProvider<BriefMailSender> senderProvider,
            BriefGenerationMapper briefGenerationMapper,
            BriefMailDeliveryMapper deliveryMapper
    ) {
        return new BriefMailDeliveryService(
                properties,
                senderProvider,
                briefGenerationMapper,
                deliveryMapper,
                FIXED_CLOCK
        );
    }

    private static BriefMailProperties enabledProperties(String... recipients) {
        return new BriefMailProperties(
                true,
                "noreply@example.com",
                List.of(recipients),
                "SignalBrief 技术半月报"
        );
    }

    private static BriefMailProperties disabledProperties(String... recipients) {
        return new BriefMailProperties(
                false,
                null,
                List.of(recipients),
                "SignalBrief 技术半月报"
        );
    }

    private static BriefGeneration successfulArchive(Long id) {
        return new BriefGeneration(
                id,
                START_INCLUSIVE,
                END_EXCLUSIVE,
                BriefGenerationStatus.SUCCESS,
                "# draft\n",
                "## summary\n",
                null,
                Instant.parse("2026-07-16T00:00:00Z"),
                Instant.parse("2026-07-16T01:00:00Z"),
                Instant.parse("2026-07-16T01:00:00Z")
        );
    }

    private static class RecordingObjectProvider implements ObjectProvider<BriefMailSender> {

        private final BriefMailSender sender;
        private boolean accessed;

        private RecordingObjectProvider(BriefMailSender sender) {
            this.sender = sender;
        }

        @Override
        public BriefMailSender getObject() throws BeansException {
            accessed = true;
            return sender;
        }

        @Override
        public BriefMailSender getIfAvailable() throws BeansException {
            accessed = true;
            return sender;
        }
    }

    private static class RecordingBriefMailSender implements BriefMailSender {

        private final List<SendRequest> requests = new ArrayList<>();
        private final Map<String, RuntimeException> failures = new LinkedHashMap<>();

        @Override
        public void send(String from, String recipient, String subject, String text) {
            requests.add(new SendRequest(from, recipient, subject, text));
            RuntimeException failure = failures.get(recipient);
            if (failure != null) {
                throw failure;
            }
        }
    }

    private static class UnavailableBriefMailSender implements BriefMailSender {

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public void send(String from, String recipient, String subject, String text) {
            throw new AssertionError("发送器不可用时不应尝试发送邮件");
        }
    }

    private record SendRequest(
            String from,
            String recipient,
            String subject,
            String text
    ) {
    }

    private static class RecordingBriefGenerationMapper implements BriefGenerationMapper {

        private final Optional<BriefGeneration> archive;
        private int findByIdCalls;

        private RecordingBriefGenerationMapper(BriefGeneration archive) {
            this(Optional.of(archive));
        }

        private RecordingBriefGenerationMapper(Optional<BriefGeneration> archive) {
            this.archive = archive;
        }

        @Override
        public Long insertGenerating(Instant startInclusive, Instant endExclusive, String draftMarkdown) {
            throw new UnsupportedOperationException("服务测试不应创建归档");
        }

        @Override
        public int markSuccess(Long id, String summaryMarkdown, Instant completedAt) {
            throw new UnsupportedOperationException("服务测试不应更新归档成功状态");
        }

        @Override
        public int markFailed(Long id, String errorSummary, Instant completedAt) {
            throw new UnsupportedOperationException("服务测试不应更新归档失败状态");
        }

        @Override
        public Optional<BriefGeneration> findById(Long id) {
            findByIdCalls++;
            return archive.filter(value -> value.id().equals(id));
        }

        @Override
        public List<BriefGeneration> findRecent(int limit) {
            throw new UnsupportedOperationException("服务测试不应查询归档列表");
        }
    }

    private static class RecordingBriefMailDeliveryMapper implements BriefMailDeliveryMapper {

        private static final Instant ROW_CREATED_AT = Instant.parse("2026-07-16T01:30:00Z");

        private final Map<Long, BriefMailDelivery> rows = new LinkedHashMap<>();
        private long nextId = 200L;
        private int sentUpdateCount = 1;
        private int failedUpdateCount = 1;
        private boolean findByIdEmpty;
        private int insertCalls;
        private int sentCalls;
        private int failedCalls;
        private int findByIdCalls;

        @Override
        public Long insertPending(Long briefGenerationId, String recipient, String subject) {
            insertCalls++;
            Long id = nextId++;
            rows.put(id, new BriefMailDelivery(
                    id,
                    briefGenerationId,
                    recipient,
                    BriefMailDeliveryStatus.PENDING,
                    subject,
                    null,
                    ROW_CREATED_AT,
                    ROW_CREATED_AT,
                    null
            ));
            return id;
        }

        @Override
        public int markSent(Long id, Instant sentAt) {
            sentCalls++;
            if (sentUpdateCount != 1) {
                return sentUpdateCount;
            }
            BriefMailDelivery delivery = rows.get(id);
            rows.put(id, new BriefMailDelivery(
                    delivery.id(),
                    delivery.briefGenerationId(),
                    delivery.recipient(),
                    BriefMailDeliveryStatus.SENT,
                    delivery.subject(),
                    null,
                    delivery.createdAt(),
                    sentAt,
                    sentAt
            ));
            return 1;
        }

        @Override
        public int markFailed(Long id, String errorSummary) {
            failedCalls++;
            if (failedUpdateCount != 1) {
                return failedUpdateCount;
            }
            BriefMailDelivery delivery = rows.get(id);
            rows.put(id, new BriefMailDelivery(
                    delivery.id(),
                    delivery.briefGenerationId(),
                    delivery.recipient(),
                    BriefMailDeliveryStatus.FAILED,
                    delivery.subject(),
                    errorSummary,
                    delivery.createdAt(),
                    ROW_CREATED_AT,
                    null
            ));
            return 1;
        }

        @Override
        public Optional<BriefMailDelivery> findById(Long id) {
            findByIdCalls++;
            if (findByIdEmpty) {
                return Optional.empty();
            }
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public List<BriefMailDelivery> findByBriefGenerationId(Long briefGenerationId) {
            return rows.values().stream()
                    .filter(row -> row.briefGenerationId().equals(briefGenerationId))
                    .sorted(Comparator.comparing(BriefMailDelivery::id))
                    .toList();
        }
    }
}
