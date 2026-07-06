# Brief Archive Mail Delivery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add durable brief generation archives and manual mail delivery for archived AI summaries.

**Architecture:** Keep generation, persistence, and mail delivery as separate boundaries. `brief` owns archived brief records, `mail` owns delivery attempts, and `internal` only adapts HTTP requests and errors. Generation is still two-step at the product level: create an archive first, then send that archived summary.

**Tech Stack:** Java 25, Spring Boot 4, MyBatis annotations, Flyway, PostgreSQL, Spring Mail `JavaMailSender`, JUnit 5, Spring Boot test slices.

---

## File Map

- Create `src/main/resources/db/migration/V3__create_brief_archive_mail_delivery_tables.sql`: tables, constraints, indexes, and PostgreSQL `COMMENT ON` statements.
- Create `src/main/java/cn/name/celestrong/signalbrief/brief/BriefGenerationStatus.java`: archive generation status enum.
- Create `src/main/java/cn/name/celestrong/signalbrief/brief/BriefGeneration.java`: archive record model.
- Create `src/main/java/cn/name/celestrong/signalbrief/brief/BriefGenerationMapper.java`: MyBatis mapper for archive insert, update, and lookup.
- Create `src/main/java/cn/name/celestrong/signalbrief/brief/BriefArchiveService.java`: orchestrates Markdown draft generation, archive insert, AI summary, and status updates.
- Create `src/main/java/cn/name/celestrong/signalbrief/brief/BriefArchiveGenerationException.java`: carries failed archive ID for HTTP 502 response.
- Create `src/main/java/cn/name/celestrong/signalbrief/brief/BriefGenerationNotFoundException.java`: maps missing archive to 404.
- Create `src/main/java/cn/name/celestrong/signalbrief/brief/BriefGenerationNotReadyException.java`: maps non-success archive to 409.
- Create `src/main/java/cn/name/celestrong/signalbrief/config/BriefMailProperties.java`: binds `signal-brief.mail`.
- Create `src/main/java/cn/name/celestrong/signalbrief/mail/BriefMailSender.java`: thin sender interface for testable mail boundary.
- Create `src/main/java/cn/name/celestrong/signalbrief/mail/JavaMailBriefMailSender.java`: production sender using `JavaMailSender`.
- Create `src/main/java/cn/name/celestrong/signalbrief/mail/BriefMailDeliveryStatus.java`: mail delivery status enum.
- Create `src/main/java/cn/name/celestrong/signalbrief/mail/BriefMailDelivery.java`: delivery record model.
- Create `src/main/java/cn/name/celestrong/signalbrief/mail/BriefMailDeliveryResult.java`: service response model.
- Create `src/main/java/cn/name/celestrong/signalbrief/mail/BriefMailDeliveryMapper.java`: MyBatis mapper for delivery insert, update, and lookup.
- Create `src/main/java/cn/name/celestrong/signalbrief/mail/BriefMailDeliveryService.java`: sends archived summaries to configured recipients.
- Create `src/main/java/cn/name/celestrong/signalbrief/mail/BriefMailUnavailableException.java`: maps disabled or incomplete mail config to 503.
- Create `src/main/java/cn/name/celestrong/signalbrief/internal/BriefArchiveErrorResponse.java`: error response with `briefGenerationId`.
- Create `src/main/java/cn/name/celestrong/signalbrief/internal/BriefMailDeliveryResponse.java`: HTTP response wrapper for delivery results.
- Modify `src/main/java/cn/name/celestrong/signalbrief/internal/ManualTriggerController.java`: add archive and delivery endpoints.
- Modify `src/main/java/cn/name/celestrong/signalbrief/internal/ManualTriggerExceptionHandler.java`: add 404, 409, 502-with-ID, and mail 503 mappings.
- Modify `src/main/resources/application.yaml`: add `signal-brief.mail` defaults.
- Modify `.env.example`, `README.md`, `docs/personal-tech-newsletter-system.md`, and records docs after behavior is implemented.

## Repository Rule

Do not commit automatically during implementation. At each checkpoint, run `git status --short --branch` and report the state. Commit only when the user explicitly asks.

---

### Task 1: Flyway Schema And Archive Mapper

**Files:**
- Create: `src/main/resources/db/migration/V3__create_brief_archive_mail_delivery_tables.sql`
- Create: `src/main/java/cn/name/celestrong/signalbrief/brief/BriefGenerationStatus.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/brief/BriefGeneration.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/brief/BriefGenerationMapper.java`
- Test: `src/test/java/cn/name/celestrong/signalbrief/brief/BriefGenerationMapperIT.java`

- [ ] **Step 1: Write the mapper integration test first**

Create `BriefGenerationMapperIT` with two tests:

```java
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
}
```

- [ ] **Step 2: Run the new IT and verify it fails before implementation**

Run:

```bash
./mvnw -Dspring.docker.compose.enabled=false -Dtest=BriefGenerationMapperIT test
```

Expected: fail to compile because `BriefGenerationMapper`, `BriefGeneration`, and `BriefGenerationStatus` do not exist.

- [ ] **Step 3: Add the Flyway migration with comments**

Create `V3__create_brief_archive_mail_delivery_tables.sql` with both tables. Include PostgreSQL comments for every table and important column:

```sql
CREATE TABLE brief_generation (
    id BIGSERIAL PRIMARY KEY,
    start_inclusive TIMESTAMPTZ NOT NULL,
    end_exclusive TIMESTAMPTZ NOT NULL,
    status VARCHAR(50) NOT NULL,
    draft_markdown TEXT NOT NULL,
    summary_markdown TEXT,
    error_summary VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT ck_brief_generation_window
        CHECK (start_inclusive < end_exclusive),
    CONSTRAINT ck_brief_generation_status
        CHECK (status IN ('GENERATING', 'SUCCESS', 'FAILED')),
    CONSTRAINT ck_brief_generation_status_context
        CHECK (
            (status = 'GENERATING' AND summary_markdown IS NULL
                AND error_summary IS NULL AND completed_at IS NULL)
            OR (status = 'SUCCESS' AND summary_markdown IS NOT NULL
                AND error_summary IS NULL AND completed_at IS NOT NULL)
            OR (status = 'FAILED' AND summary_markdown IS NULL
                AND error_summary IS NOT NULL
                AND completed_at IS NOT NULL)
        )
);

CREATE INDEX idx_brief_generation_created_at
    ON brief_generation (created_at DESC, id DESC);

CREATE INDEX idx_brief_generation_window
    ON brief_generation (start_inclusive, end_exclusive, id DESC);

CREATE TABLE brief_mail_delivery (
    id BIGSERIAL PRIMARY KEY,
    brief_generation_id BIGINT NOT NULL REFERENCES brief_generation (id) ON DELETE CASCADE,
    recipient VARCHAR(320) NOT NULL,
    status VARCHAR(50) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    error_summary VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ,
    CONSTRAINT ck_brief_mail_delivery_status
        CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT ck_brief_mail_delivery_status_context
        CHECK (
            (status = 'PENDING' AND error_summary IS NULL AND sent_at IS NULL)
            OR (status = 'SENT' AND error_summary IS NULL AND sent_at IS NOT NULL)
            OR (status = 'FAILED' AND error_summary IS NOT NULL AND sent_at IS NULL)
        )
);

CREATE INDEX idx_brief_mail_delivery_generation_id
    ON brief_mail_delivery (brief_generation_id, id);

COMMENT ON TABLE brief_generation IS
    '保存一次简报生成尝试，包括 Markdown 草稿、AI 摘要、状态和错误摘要。';
COMMENT ON COLUMN brief_generation.status IS
    '生成状态：GENERATING 表示生成中，SUCCESS 表示 AI 摘要成功，FAILED 表示生成失败。';
COMMENT ON COLUMN brief_generation.draft_markdown IS
    '确定性 Markdown 简报草稿，由候选文章渲染得到。';
COMMENT ON COLUMN brief_generation.summary_markdown IS
    'AI 摘要 Markdown，生成成功时写入。';
COMMENT ON COLUMN brief_generation.error_summary IS
    '生成失败摘要，截断后保存，不包含完整堆栈或 Provider 原始响应。';

COMMENT ON TABLE brief_mail_delivery IS
    '保存归档简报的邮件发送结果，按收件人记录。';
COMMENT ON COLUMN brief_mail_delivery.brief_generation_id IS
    '关联的简报生成归档 ID。';
COMMENT ON COLUMN brief_mail_delivery.recipient IS
    '本次发送的收件人邮箱。';
COMMENT ON COLUMN brief_mail_delivery.status IS
    '发送状态：PENDING 表示待发送，SENT 表示已发送，FAILED 表示发送失败。';
COMMENT ON COLUMN brief_mail_delivery.subject IS
    '邮件主题。';
COMMENT ON COLUMN brief_mail_delivery.error_summary IS
    '发送失败摘要，截断后保存，不包含完整堆栈或 SMTP 敏感信息。';
```

- [ ] **Step 4: Add archive enum and record**

Create:

```java
public enum BriefGenerationStatus {
    GENERATING,
    SUCCESS,
    FAILED
}
```

Create:

```java
public record BriefGeneration(
        Long id,
        Instant startInclusive,
        Instant endExclusive,
        BriefGenerationStatus status,
        String draftMarkdown,
        String summaryMarkdown,
        String errorSummary,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
}
```

- [ ] **Step 5: Add `BriefGenerationMapper`**

Implement methods:

```java
@Mapper
public interface BriefGenerationMapper {

    @Select("""
            INSERT INTO brief_generation (
                start_inclusive,
                end_exclusive,
                status,
                draft_markdown
            ) VALUES (
                #{startInclusive},
                #{endExclusive},
                'GENERATING',
                #{draftMarkdown}
            )
            RETURNING id
            """)
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    Long insertGenerating(
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive,
            @Param("draftMarkdown") String draftMarkdown
    );

    @Update("""
            UPDATE brief_generation
            SET status = 'SUCCESS',
                summary_markdown = #{summaryMarkdown},
                error_summary = NULL,
                completed_at = #{completedAt},
                updated_at = now()
            WHERE id = #{id}
                AND status = 'GENERATING'
            """)
    int markSuccess(
            @Param("id") Long id,
            @Param("summaryMarkdown") String summaryMarkdown,
            @Param("completedAt") Instant completedAt
    );

    @Update("""
            UPDATE brief_generation
            SET status = 'FAILED',
                summary_markdown = NULL,
                error_summary = #{errorSummary},
                completed_at = #{completedAt},
                updated_at = now()
            WHERE id = #{id}
                AND status = 'GENERATING'
            """)
    int markFailed(
            @Param("id") Long id,
            @Param("errorSummary") String errorSummary,
            @Param("completedAt") Instant completedAt
    );

    @Select("""
            SELECT
                id,
                start_inclusive AS "startInclusive",
                end_exclusive AS "endExclusive",
                status,
                draft_markdown AS "draftMarkdown",
                summary_markdown AS "summaryMarkdown",
                error_summary AS "errorSummary",
                created_at AS "createdAt",
                updated_at AS "updatedAt",
                completed_at AS "completedAt"
            FROM brief_generation
            WHERE id = #{id}
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "startInclusive", javaType = Instant.class),
            @Arg(column = "endExclusive", javaType = Instant.class),
            @Arg(column = "status", javaType = BriefGenerationStatus.class),
            @Arg(column = "draftMarkdown", javaType = String.class),
            @Arg(column = "summaryMarkdown", javaType = String.class),
            @Arg(column = "errorSummary", javaType = String.class),
            @Arg(column = "createdAt", javaType = Instant.class),
            @Arg(column = "updatedAt", javaType = Instant.class),
            @Arg(column = "completedAt", javaType = Instant.class)
    })
    Optional<BriefGeneration> findById(@Param("id") Long id);
}
```

- [ ] **Step 6: Compile and optionally run the mapper IT**

Compile test sources without requiring a local PostgreSQL instance:

```bash
./mvnw -Dspring.docker.compose.enabled=false -DskipTests test
```

Expected: test sources compile.

Only when a local test PostgreSQL is already available, run:

```bash
./mvnw -Dspring.docker.compose.enabled=false -Dtest=BriefGenerationMapperIT test
```

Expected with local database: pass. If local database is not available, do not start Docker Compose just for this task; CI `./mvnw -B verify` will run `*IT`.

- [ ] **Checkpoint**

Run:

```bash
git status --short --branch
```

Report changed files. Do not commit unless the user explicitly asks.

---

### Task 2: Brief Archive Service

**Files:**
- Create: `src/main/java/cn/name/celestrong/signalbrief/brief/BriefArchiveService.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/brief/BriefArchiveGenerationException.java`
- Create: `src/test/java/cn/name/celestrong/signalbrief/brief/BriefArchiveServiceTest.java`

- [ ] **Step 1: Write service tests with fakes**

Create tests covering success, provider failure, and disabled AI:

```java
@Test
void archivesSuccessfulAiSummary() {
    RecordingBriefGenerationService briefService = new RecordingBriefGenerationService("# draft\n");
    RecordingAiSummaryService aiService = new RecordingAiSummaryService("## summary\n");
    RecordingBriefGenerationMapper mapper = new RecordingBriefGenerationMapper();
    BriefArchiveService service = new BriefArchiveService(
            briefService,
            aiService,
            mapper,
            Clock.fixed(Instant.parse("2026-07-16T01:00:00Z"), ZoneOffset.UTC)
    );

    BriefGeneration archive = service.archiveAiSummary(
            Instant.parse("2026-07-01T00:00:00Z"),
            Instant.parse("2026-07-16T00:00:00Z")
    );

    assertEquals(BriefGenerationStatus.SUCCESS, archive.status());
    assertEquals("# draft\n", archive.draftMarkdown());
    assertEquals("## summary\n", archive.summaryMarkdown());
    assertEquals(1, mapper.insertCalls);
    assertEquals(1, mapper.successCalls);
}

@Test
void savesFailedArchiveWhenProviderFails() {
    RecordingBriefGenerationService briefService = new RecordingBriefGenerationService("# draft\n");
    RecordingAiSummaryService aiService = new RecordingAiSummaryService(new AiSummaryException("provider down"));
    RecordingBriefGenerationMapper mapper = new RecordingBriefGenerationMapper();
    BriefArchiveService service = new BriefArchiveService(
            briefService,
            aiService,
            mapper,
            Clock.fixed(Instant.parse("2026-07-16T01:00:00Z"), ZoneOffset.UTC)
    );

    BriefArchiveGenerationException exception = assertThrows(
            BriefArchiveGenerationException.class,
            () -> service.archiveAiSummary(
                    Instant.parse("2026-07-01T00:00:00Z"),
                    Instant.parse("2026-07-16T00:00:00Z")
            )
    );

    assertEquals(100L, exception.briefGenerationId());
    assertEquals(1, mapper.failedCalls);
    assertEquals("provider down", mapper.errorSummary);
}

@Test
void doesNotCreateArchiveWhenAiSummaryIsDisabled() {
    RecordingBriefGenerationService briefService = new RecordingBriefGenerationService("# draft\n");
    RecordingAiSummaryService aiService = new RecordingAiSummaryService(new AiSummaryUnavailableException("AI 摘要能力未启用"));
    RecordingBriefGenerationMapper mapper = new RecordingBriefGenerationMapper();
    BriefArchiveService service = new BriefArchiveService(briefService, aiService, mapper, Clock.systemUTC());

    assertThrows(
            AiSummaryUnavailableException.class,
            () -> service.archiveAiSummary(
                    Instant.parse("2026-07-01T00:00:00Z"),
                    Instant.parse("2026-07-16T00:00:00Z")
            )
    );
    assertEquals(0, briefService.calls);
    assertEquals(0, mapper.insertCalls);
}
```

Use fake classes instead of Mockito. The fake AI service should override `requireAvailable()` and `summarizeMarkdown()` so tests do not start Spring or HTTP clients.

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
./mvnw -Dspring.docker.compose.enabled=false -Dtest=BriefArchiveServiceTest test
```

Expected: fail to compile because `BriefArchiveService` and `BriefArchiveGenerationException` do not exist.

- [ ] **Step 3: Implement `BriefArchiveGenerationException`**

```java
public class BriefArchiveGenerationException extends RuntimeException {

    private final Long briefGenerationId;

    public BriefArchiveGenerationException(Long briefGenerationId, String message, Throwable cause) {
        super(message, cause);
        this.briefGenerationId = briefGenerationId;
    }

    public Long briefGenerationId() {
        return briefGenerationId;
    }
}
```

- [ ] **Step 4: Implement `BriefArchiveService`**

Use constructor injection and a package-private constructor with `Clock` for tests:

```java
@Service
public class BriefArchiveService {

    private static final int MAX_ERROR_SUMMARY_LENGTH = 1_000;

    private final BriefGenerationService briefGenerationService;
    private final AiSummaryService aiSummaryService;
    private final BriefGenerationMapper mapper;
    private final Clock clock;

    @Autowired
    public BriefArchiveService(
            BriefGenerationService briefGenerationService,
            AiSummaryService aiSummaryService,
            BriefGenerationMapper mapper
    ) {
        this(briefGenerationService, aiSummaryService, mapper, Clock.systemUTC());
    }

    BriefArchiveService(
            BriefGenerationService briefGenerationService,
            AiSummaryService aiSummaryService,
            BriefGenerationMapper mapper,
            Clock clock
    ) {
        this.briefGenerationService = Objects.requireNonNull(briefGenerationService, "briefGenerationService must not be null");
        this.aiSummaryService = Objects.requireNonNull(aiSummaryService, "aiSummaryService must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public BriefGeneration archiveAiSummary(Instant startInclusive, Instant endExclusive) {
        aiSummaryService.requireAvailable();
        String draftMarkdown = briefGenerationService.generate(startInclusive, endExclusive);
        Long id = mapper.insertGenerating(startInclusive, endExclusive, draftMarkdown);

        try {
            String summaryMarkdown = aiSummaryService.summarizeMarkdown(draftMarkdown);
            mapper.markSuccess(id, summaryMarkdown, clock.instant());
            return mapper.findById(id).orElseThrow();
        } catch (AiSummaryException ex) {
            mapper.markFailed(id, truncate(ex.getMessage()), clock.instant());
            throw new BriefArchiveGenerationException(id, "AI 摘要归档生成失败", ex);
        }
    }

    private String truncate(String message) {
        if (message == null || message.length() <= MAX_ERROR_SUMMARY_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_SUMMARY_LENGTH);
    }
}
```

- [ ] **Step 5: Run the archive service tests**

Run:

```bash
./mvnw -Dspring.docker.compose.enabled=false -Dtest=BriefArchiveServiceTest test
```

Expected: pass.

- [ ] **Checkpoint**

Run:

```bash
git status --short --branch
```

Report changed files. Do not commit unless the user explicitly asks.

---

### Task 3: Mail Configuration And Sender Boundary

**Files:**
- Create: `src/main/java/cn/name/celestrong/signalbrief/config/BriefMailProperties.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/mail/BriefMailSender.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/mail/JavaMailBriefMailSender.java`
- Test: `src/test/java/cn/name/celestrong/signalbrief/config/BriefMailPropertiesTest.java`

- [ ] **Step 1: Write properties tests**

Cover defaults, enabled validation, trimming, and blank filtering:

```java
@Test
void defaultsToDisabledWithNoRecipients() {
    BriefMailProperties properties = bind("""
            signal-brief:
              mail:
                enabled: false
            """);

    assertFalse(properties.enabled());
    assertEquals(List.of(), properties.recipients());
    assertEquals("SignalBrief 技术半月报", properties.subjectPrefix());
}

@Test
void requiresFromWhenEnabled() {
    BindException exception = assertThrows(BindException.class, () -> bind("""
            signal-brief:
              mail:
                enabled: true
                recipients: a@example.com
            """));

    assertTrue(exception.getMessage().contains("signal-brief.mail.from"));
}

@Test
void trimsRecipientsAndDropsBlankValues() {
    BriefMailProperties properties = bind("""
            signal-brief:
              mail:
                enabled: true
                from: noreply@example.com
                recipients:
                  - " a@example.com "
                  - ""
                  - "b@example.com"
            """);

    assertEquals(List.of("a@example.com", "b@example.com"), properties.recipients());
}
```

Use the same binder helper pattern as existing `AiSummaryPropertiesTest`.

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
./mvnw -Dspring.docker.compose.enabled=false -Dtest=BriefMailPropertiesTest test
```

Expected: fail to compile because `BriefMailProperties` does not exist.

- [ ] **Step 3: Implement `BriefMailProperties`**

```java
@ConfigurationProperties(prefix = "signal-brief.mail")
public record BriefMailProperties(
        boolean enabled,
        String from,
        List<String> recipients,
        String subjectPrefix
) {

    public BriefMailProperties {
        recipients = normalizeRecipients(recipients);
        subjectPrefix = StringUtils.defaultIfBlank(subjectPrefix, "SignalBrief 技术半月报");

        if (enabled && StringUtils.isBlank(from)) {
            throw new IllegalArgumentException("signal-brief.mail.from must be configured when mail is enabled");
        }
        if (enabled && recipients.isEmpty()) {
            throw new IllegalArgumentException("signal-brief.mail.recipients must be configured when mail is enabled");
        }
    }

    private static List<String> normalizeRecipients(List<String> recipients) {
        if (recipients == null) {
            return List.of();
        }
        return recipients.stream()
                .map(StringUtils::trimToEmpty)
                .filter(StringUtils::isNotBlank)
                .toList();
    }
}
```

- [ ] **Step 4: Add sender boundary**

Create interface:

```java
public interface BriefMailSender {

    void send(String from, String recipient, String subject, String text);
}
```

Create production implementation:

```java
@Service
public class JavaMailBriefMailSender implements BriefMailSender {

    private final JavaMailSender javaMailSender;

    public JavaMailBriefMailSender(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }

    @Override
    public void send(String from, String recipient, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(text);
        javaMailSender.send(message);
    }
}
```

- [ ] **Step 5: Run properties tests**

Run:

```bash
./mvnw -Dspring.docker.compose.enabled=false -Dtest=BriefMailPropertiesTest test
```

Expected: pass.

- [ ] **Checkpoint**

Run:

```bash
git status --short --branch
```

Report changed files. Do not commit unless the user explicitly asks.

---

### Task 4: Mail Delivery Mapper And Service

**Files:**
- Create: `src/main/java/cn/name/celestrong/signalbrief/mail/BriefMailDeliveryStatus.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/mail/BriefMailDelivery.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/mail/BriefMailDeliveryResult.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/mail/BriefMailDeliveryMapper.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/mail/BriefMailDeliveryService.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/mail/BriefMailUnavailableException.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/brief/BriefGenerationNotFoundException.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/brief/BriefGenerationNotReadyException.java`
- Test: `src/test/java/cn/name/celestrong/signalbrief/mail/BriefMailDeliveryMapperIT.java`
- Test: `src/test/java/cn/name/celestrong/signalbrief/mail/BriefMailDeliveryServiceTest.java`

- [ ] **Step 1: Write mapper IT**

Test insert pending, mark sent, mark failed, and query by ID:

```java
@Test
void recordsSentAndFailedDeliveries() {
    Long briefId = briefGenerationMapper.insertGenerating(start, end, "# draft\n");
    briefGenerationMapper.markSuccess(briefId, "## summary\n", completedAt);

    Long sentId = mapper.insertPending(briefId, "a@example.com", "SignalBrief 技术半月报 2026-07-01 至 2026-07-16");
    Long failedId = mapper.insertPending(briefId, "b@example.com", "SignalBrief 技术半月报 2026-07-01 至 2026-07-16");

    mapper.markSent(sentId, Instant.parse("2026-07-16T02:00:00Z"));
    mapper.markFailed(failedId, "smtp unavailable");

    List<BriefMailDelivery> deliveries = mapper.findByBriefGenerationId(briefId);

    assertEquals(List.of(sentId, failedId), deliveries.stream().map(BriefMailDelivery::id).toList());
    assertEquals(BriefMailDeliveryStatus.SENT, deliveries.get(0).status());
    assertEquals(BriefMailDeliveryStatus.FAILED, deliveries.get(1).status());
}
```

- [ ] **Step 2: Add delivery model and mapper**

Create:

```java
public enum BriefMailDeliveryStatus {
    PENDING,
    SENT,
    FAILED
}
```

Create:

```java
public record BriefMailDelivery(
        Long id,
        Long briefGenerationId,
        String recipient,
        BriefMailDeliveryStatus status,
        String subject,
        String errorSummary,
        Instant createdAt,
        Instant updatedAt,
        Instant sentAt
) {
}
```

Create mapper methods with explicit annotation SQL:

```java
@Mapper
public interface BriefMailDeliveryMapper {

    @Select("""
            INSERT INTO brief_mail_delivery (
                brief_generation_id,
                recipient,
                status,
                subject
            ) VALUES (
                #{briefGenerationId},
                #{recipient},
                'PENDING',
                #{subject}
            )
            RETURNING id
            """)
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    Long insertPending(
            @Param("briefGenerationId") Long briefGenerationId,
            @Param("recipient") String recipient,
            @Param("subject") String subject
    );

    @Update("""
            UPDATE brief_mail_delivery
            SET status = 'SENT',
                sent_at = #{sentAt},
                error_summary = NULL,
                updated_at = now()
            WHERE id = #{id}
            """)
    int markSent(@Param("id") Long id, @Param("sentAt") Instant sentAt);

    @Update("""
            UPDATE brief_mail_delivery
            SET status = 'FAILED',
                error_summary = #{errorSummary},
                sent_at = NULL,
                updated_at = now()
            WHERE id = #{id}
            """)
    int markFailed(@Param("id") Long id, @Param("errorSummary") String errorSummary);

    @Select("""
            SELECT
                id,
                brief_generation_id AS "briefGenerationId",
                recipient,
                status,
                subject,
                error_summary AS "errorSummary",
                created_at AS "createdAt",
                updated_at AS "updatedAt",
                sent_at AS "sentAt"
            FROM brief_mail_delivery
            WHERE id = #{id}
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "briefGenerationId", javaType = Long.class),
            @Arg(column = "recipient", javaType = String.class),
            @Arg(column = "status", javaType = BriefMailDeliveryStatus.class),
            @Arg(column = "subject", javaType = String.class),
            @Arg(column = "errorSummary", javaType = String.class),
            @Arg(column = "createdAt", javaType = Instant.class),
            @Arg(column = "updatedAt", javaType = Instant.class),
            @Arg(column = "sentAt", javaType = Instant.class)
    })
    Optional<BriefMailDelivery> findById(@Param("id") Long id);

    @Select("""
            SELECT
                id,
                brief_generation_id AS "briefGenerationId",
                recipient,
                status,
                subject,
                error_summary AS "errorSummary",
                created_at AS "createdAt",
                updated_at AS "updatedAt",
                sent_at AS "sentAt"
            FROM brief_mail_delivery
            WHERE brief_generation_id = #{briefGenerationId}
            ORDER BY id ASC
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "briefGenerationId", javaType = Long.class),
            @Arg(column = "recipient", javaType = String.class),
            @Arg(column = "status", javaType = BriefMailDeliveryStatus.class),
            @Arg(column = "subject", javaType = String.class),
            @Arg(column = "errorSummary", javaType = String.class),
            @Arg(column = "createdAt", javaType = Instant.class),
            @Arg(column = "updatedAt", javaType = Instant.class),
            @Arg(column = "sentAt", javaType = Instant.class)
    })
    List<BriefMailDelivery> findByBriefGenerationId(@Param("briefGenerationId") Long briefGenerationId);
}
```

- [ ] **Step 3: Write delivery service tests**

Use fake mapper and fake sender:

```java
@Test
void sendsArchivedSummaryToEachConfiguredRecipient() {
    RecordingBriefGenerationMapper generationMapper = RecordingBriefGenerationMapper.successArchive("## summary\n");
    RecordingBriefMailDeliveryMapper deliveryMapper = new RecordingBriefMailDeliveryMapper();
    RecordingBriefMailSender sender = new RecordingBriefMailSender();
    BriefMailProperties properties = new BriefMailProperties(
            true,
            "noreply@example.com",
            List.of("a@example.com", "b@example.com"),
            "SignalBrief 技术半月报"
    );
    BriefMailDeliveryService service = new BriefMailDeliveryService(
            generationMapper,
            deliveryMapper,
            senderProvider(sender),
            properties,
            Clock.fixed(Instant.parse("2026-07-16T02:00:00Z"), ZoneOffset.UTC)
    );

    BriefMailDeliveryResult result = service.deliver(12L);

    assertEquals(2, sender.sentMessages.size());
    assertEquals(List.of("a@example.com", "b@example.com"), sender.recipients());
    assertEquals(2, result.deliveries().size());
    assertTrue(result.deliveries().stream().allMatch(delivery -> delivery.status() == BriefMailDeliveryStatus.SENT));
}

@Test
void recordsFailedRecipientWithoutBlockingOtherRecipients() {
    sender.failRecipients.add("b@example.com");

    BriefMailDeliveryResult result = service.deliver(12L);

    assertEquals(List.of(BriefMailDeliveryStatus.SENT, BriefMailDeliveryStatus.FAILED),
            result.deliveries().stream().map(BriefMailDelivery::status).toList());
}

@Test
void rejectsNonSuccessArchive() {
    generationMapper.archive = failedArchive();

    assertThrows(BriefGenerationNotReadyException.class, () -> service.deliver(12L));
    assertEquals(0, sender.sentMessages.size());
}

@Test
void rejectsDeliveryWhenMailIsDisabledWithoutRequiringSenderBean() {
    BriefMailProperties properties = new BriefMailProperties(
            false,
            null,
            List.of(),
            "SignalBrief 技术半月报"
    );
    BriefMailDeliveryService service = new BriefMailDeliveryService(
            RecordingBriefGenerationMapper.successArchive("## summary\n"),
            new RecordingBriefMailDeliveryMapper(),
            unusedSenderProvider(),
            properties,
            Clock.systemUTC()
    );

    assertThrows(BriefMailUnavailableException.class, () -> service.deliver(12L));
}

private static ObjectProvider<BriefMailSender> senderProvider(BriefMailSender sender) {
    return new ObjectProvider<>() {
        @Override
        public BriefMailSender getObject() {
            return sender;
        }
    };
}

private static ObjectProvider<BriefMailSender> unusedSenderProvider() {
    return new ObjectProvider<>() {
        @Override
        public BriefMailSender getObject() {
            throw new AssertionError("邮件关闭时不应获取 BriefMailSender");
        }
    };
}
```

- [ ] **Step 4: Implement exceptions**

```java
public class BriefMailUnavailableException extends RuntimeException {
    public BriefMailUnavailableException(String message) {
        super(message);
    }
}
```

```java
public class BriefGenerationNotFoundException extends RuntimeException {
    public BriefGenerationNotFoundException(Long id) {
        super("简报归档记录不存在: " + id);
    }
}
```

```java
public class BriefGenerationNotReadyException extends RuntimeException {
    public BriefGenerationNotReadyException(Long id, BriefGenerationStatus status) {
        super("简报归档记录不可发送: " + id + ", status=" + status);
    }
}
```

- [ ] **Step 5: Implement delivery service**

```java
@Service
public class BriefMailDeliveryService {

    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final DateTimeFormatter SUBJECT_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(UTC);
    private static final int MAX_ERROR_SUMMARY_LENGTH = 1_000;

    private final BriefGenerationMapper briefGenerationMapper;
    private final BriefMailDeliveryMapper deliveryMapper;
    private final ObjectProvider<BriefMailSender> mailSenderProvider;
    private final BriefMailProperties properties;
    private final Clock clock;

    @Autowired
    public BriefMailDeliveryService(
            BriefGenerationMapper briefGenerationMapper,
            BriefMailDeliveryMapper deliveryMapper,
            ObjectProvider<BriefMailSender> mailSenderProvider,
            BriefMailProperties properties
    ) {
        this(briefGenerationMapper, deliveryMapper, mailSenderProvider, properties, Clock.systemUTC());
    }

    BriefMailDeliveryService(
            BriefGenerationMapper briefGenerationMapper,
            BriefMailDeliveryMapper deliveryMapper,
            ObjectProvider<BriefMailSender> mailSenderProvider,
            BriefMailProperties properties,
            Clock clock
    ) {
        this.briefGenerationMapper = briefGenerationMapper;
        this.deliveryMapper = deliveryMapper;
        this.mailSenderProvider = mailSenderProvider;
        this.properties = properties;
        this.clock = clock;
    }

    public BriefMailDeliveryResult deliver(Long briefGenerationId) {
        requireAvailable();
        BriefMailSender mailSender = mailSenderProvider.getIfAvailable(() -> {
            throw new BriefMailUnavailableException("邮件发送器未配置");
        });
        BriefGeneration archive = briefGenerationMapper.findById(briefGenerationId)
                .orElseThrow(() -> new BriefGenerationNotFoundException(briefGenerationId));
        if (archive.status() != BriefGenerationStatus.SUCCESS) {
            throw new BriefGenerationNotReadyException(briefGenerationId, archive.status());
        }

        String subject = subjectFor(archive);
        List<BriefMailDelivery> deliveries = new ArrayList<>();
        for (String recipient : properties.recipients()) {
            Long deliveryId = deliveryMapper.insertPending(archive.id(), recipient, subject);
            try {
                mailSender.send(properties.from(), recipient, subject, archive.summaryMarkdown());
                deliveryMapper.markSent(deliveryId, clock.instant());
            } catch (RuntimeException ex) {
                deliveryMapper.markFailed(deliveryId, truncate(ex.getMessage()));
            }
            deliveries.add(deliveryMapper.findById(deliveryId).orElseThrow());
        }
        return new BriefMailDeliveryResult(archive.id(), deliveries);
    }

    private void requireAvailable() {
        if (!properties.enabled()) {
            throw new BriefMailUnavailableException("邮件发送能力未启用");
        }
    }

    private String subjectFor(BriefGeneration archive) {
        return "%s %s 至 %s".formatted(
                properties.subjectPrefix(),
                SUBJECT_DATE_FORMATTER.format(archive.startInclusive()),
                SUBJECT_DATE_FORMATTER.format(archive.endExclusive())
        );
    }

    private String truncate(String message) {
        if (message == null || message.length() <= MAX_ERROR_SUMMARY_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_SUMMARY_LENGTH);
    }
}
```

- [ ] **Step 6: Run mail tests**

Run:

```bash
./mvnw -Dspring.docker.compose.enabled=false -Dtest=BriefMailDeliveryServiceTest,BriefMailPropertiesTest test
```

Expected: pass.

Run mapper IT when a local database is available:

```bash
./mvnw -Dspring.docker.compose.enabled=false -Dtest=BriefMailDeliveryMapperIT test
```

Expected: pass, or leave to CI if local integration DB is intentionally not running.

- [ ] **Checkpoint**

Run:

```bash
git status --short --branch
```

Report changed files. Do not commit unless the user explicitly asks.

---

### Task 5: Internal API Endpoints And Error Mapping

**Files:**
- Modify: `src/main/java/cn/name/celestrong/signalbrief/internal/ManualTriggerController.java`
- Modify: `src/main/java/cn/name/celestrong/signalbrief/internal/ManualTriggerExceptionHandler.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/internal/BriefArchiveErrorResponse.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/internal/BriefMailDeliveryResponse.java`
- Modify: `src/test/java/cn/name/celestrong/signalbrief/internal/ManualTriggerControllerTest.java`
- Modify: `src/test/java/cn/name/celestrong/signalbrief/internal/ManualTriggerControllerConditionTest.java`

- [ ] **Step 1: Add controller tests first**

Add tests for:

```java
@Test
void archivesAiSummaryBriefForRequestedWindow() throws Exception {
    mockMvc.perform(post("/internal/briefs/ai-summary/archives")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "startInclusive": "2026-07-01T00:00:00Z",
                              "endExclusive": "2026-07-16T00:00:00Z"
                            }
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(100))
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.draftMarkdown").value("# SignalBrief 技术半月报\n"))
            .andExpect(jsonPath("$.summaryMarkdown").value("## AI 摘要\n"));
}

@Test
void mapsArchivedAiProviderFailureToBadGatewayWithArchiveId() throws Exception {
    briefArchiveService.failure = new BriefArchiveGenerationException(
            100L,
            "AI 摘要归档生成失败",
            new AiSummaryException("provider down")
    );

    mockMvc.perform(post("/internal/briefs/ai-summary/archives")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validBriefWindowJson()))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.message").value("AI 摘要生成失败"))
            .andExpect(jsonPath("$.briefGenerationId").value(100));
}

@Test
void sendsArchivedBriefToConfiguredRecipients() throws Exception {
    mockMvc.perform(post("/internal/briefs/100/mail-deliveries"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.briefGenerationId").value(100))
            .andExpect(jsonPath("$.deliveries[0].recipient").value("a@example.com"))
            .andExpect(jsonPath("$.deliveries[0].status").value("SENT"));
}
```

Also add tests for archive `503`, delivery `404`, `409`, and mail `503`.

- [ ] **Step 2: Run controller tests and verify failure**

Run:

```bash
./mvnw -Dspring.docker.compose.enabled=false -Dtest=ManualTriggerControllerTest,ManualTriggerControllerConditionTest test
```

Expected: fail because controller dependencies and endpoints are missing.

- [ ] **Step 3: Add response records**

```java
@Schema(description = "AI 简报归档失败响应")
public record BriefArchiveErrorResponse(
        @Schema(description = "错误说明", example = "AI 摘要生成失败")
        String message,
        @Schema(description = "已创建的简报归档 ID", example = "12")
        Long briefGenerationId
) {
}
```

```java
@Schema(description = "简报邮件发送响应")
public record BriefMailDeliveryResponse(
        Long briefGenerationId,
        List<BriefMailDelivery> deliveries
) {
}
```

- [ ] **Step 4: Wire new services into controller**

Add constructor dependencies:

```java
private final BriefArchiveService briefArchiveService;
private final BriefMailDeliveryService briefMailDeliveryService;
```

Add endpoints:

```java
@PostMapping("/briefs/ai-summary/archives")
public BriefGeneration archiveAiSummaryBrief(@RequestBody MarkdownBriefRequest request) {
    validateBriefWindowRequest(request);
    return briefArchiveService.archiveAiSummary(request.startInclusive(), request.endExclusive());
}

@PostMapping("/briefs/{id}/mail-deliveries")
public BriefMailDeliveryResponse deliverBriefMail(@PathVariable Long id) {
    BriefMailDeliveryResult result = briefMailDeliveryService.deliver(id);
    return new BriefMailDeliveryResponse(result.briefGenerationId(), result.deliveries());
}
```

Update `@Tag` description to mention archived brief mail delivery.

- [ ] **Step 5: Add exception mappings**

In `ManualTriggerExceptionHandler`:

```java
@ExceptionHandler(BriefArchiveGenerationException.class)
public ResponseEntity<BriefArchiveErrorResponse> handleBriefArchiveGeneration(BriefArchiveGenerationException ex) {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(new BriefArchiveErrorResponse("AI 摘要生成失败", ex.briefGenerationId()));
}

@ExceptionHandler(BriefGenerationNotFoundException.class)
public ResponseEntity<InternalApiErrorResponse> handleBriefGenerationNotFound(BriefGenerationNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new InternalApiErrorResponse(ex.getMessage()));
}

@ExceptionHandler(BriefGenerationNotReadyException.class)
public ResponseEntity<InternalApiErrorResponse> handleBriefGenerationNotReady(BriefGenerationNotReadyException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(new InternalApiErrorResponse(ex.getMessage()));
}

@ExceptionHandler(BriefMailUnavailableException.class)
public ResponseEntity<InternalApiErrorResponse> handleBriefMailUnavailable(BriefMailUnavailableException ex) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new InternalApiErrorResponse(ex.getMessage()));
}
```

- [ ] **Step 6: Run internal API tests**

Run:

```bash
./mvnw -Dspring.docker.compose.enabled=false -Dtest=ManualTriggerControllerTest,ManualTriggerControllerConditionTest test
```

Expected: pass.

- [ ] **Checkpoint**

Run:

```bash
git status --short --branch
```

Report changed files. Do not commit unless the user explicitly asks.

---

### Task 6: Configuration, Documentation, And Final Verification

**Files:**
- Modify: `src/main/resources/application.yaml`
- Modify: `.env.example`
- Modify: `README.md`
- Modify: `docs/personal-tech-newsletter-system.md`
- Modify or create: `docs/records/brief-archive-mail-delivery.md`
- Modify: `docs/records/ai-summary-generation.md`
- Modify: `docs/records/manual-trigger-api.md`

- [ ] **Step 1: Add application defaults**

Add:

```yaml
signal-brief:
  mail:
    enabled: ${SIGNAL_BRIEF_MAIL_ENABLED:false}
    from: ${SIGNAL_BRIEF_MAIL_FROM:}
    recipients: ${SIGNAL_BRIEF_MAIL_RECIPIENTS:}
    subject-prefix: ${SIGNAL_BRIEF_MAIL_SUBJECT_PREFIX:SignalBrief 技术半月报}
```

Keep SMTP infrastructure under Spring Boot standard `spring.mail.*`.

- [ ] **Step 2: Update `.env.example`**

Add safe examples:

```dotenv
# Mail delivery is disabled by default.
SIGNAL_BRIEF_MAIL_ENABLED=false
SIGNAL_BRIEF_MAIL_FROM=noreply@example.com
SIGNAL_BRIEF_MAIL_RECIPIENTS=reader@example.com
SIGNAL_BRIEF_MAIL_SUBJECT_PREFIX=SignalBrief 技术半月报

# Standard Spring Mail settings. Do not commit real credentials.
SPRING_MAIL_HOST=smtp.example.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=
SPRING_MAIL_PASSWORD=
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
```

- [ ] **Step 3: Update README API examples**

Document:

```bash
curl -X POST http://localhost:8080/internal/briefs/ai-summary/archives \
  -H 'Content-Type: application/json' \
  -d '{
    "startInclusive": "2026-07-01T00:00:00Z",
    "endExclusive": "2026-07-16T00:00:00Z"
  }'
```

Document:

```bash
curl -X POST http://localhost:8080/internal/briefs/12/mail-deliveries
```

Explain that mail requires `SIGNAL_BRIEF_MAIL_ENABLED=true`, configured recipients, and valid `spring.mail.*`.

- [ ] **Step 4: Update records**

Create `docs/records/brief-archive-mail-delivery.md` with:

- Scope: archive and manual delivery only.
- Tables: `brief_generation`, `brief_mail_delivery`.
- Status rules: `GENERATING/SUCCESS/FAILED`, `PENDING/SENT/FAILED`.
- API paths.
- Failure behavior: AI disabled no archive, Provider failure creates failed archive, delivery partial failures return `200`.
- SQL migration rule: tables and important columns use `COMMENT ON`.

Update existing AI and manual-trigger records to link this new record.

- [ ] **Step 5: Run focused tests**

Run:

```bash
./mvnw -Dspring.docker.compose.enabled=false -Dtest=BriefArchiveServiceTest,BriefMailPropertiesTest,BriefMailDeliveryServiceTest,ManualTriggerControllerTest,ManualTriggerControllerConditionTest test
```

Expected: all selected tests pass.

- [ ] **Step 6: Run full local base tests**

Run:

```bash
./mvnw -Dspring.docker.compose.enabled=false test
```

Expected: all unit and slice tests pass.

- [ ] **Step 7: Run whitespace check**

Run:

```bash
git diff --check
```

Expected: no output.

- [ ] **Step 8: Final status**

Run:

```bash
git status --short --branch
```

Report branch and changed files. Do not commit unless the user explicitly asks.
