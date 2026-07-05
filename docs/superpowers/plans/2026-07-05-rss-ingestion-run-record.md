# RSS Ingestion Run Record Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add persistent RSS ingestion run records, source-level failure details, and internal query APIs.

**Architecture:** Keep RSS ingestion as a focused feature instead of introducing a generic task system. `FeedIngestionService` remains the orchestration entry point, while a new `IngestionRunRecorder` owns run persistence through a MyBatis mapper. Internal APIs expose recent run lists and run details under the existing `/internal` boundary.

**Tech Stack:** Java 25, Spring Boot 4.0.7, Maven, PostgreSQL, Flyway, MyBatis, JUnit 5, Spring MVC test.

---

## File Map

- Create `src/main/resources/db/migration/V2__create_rss_ingestion_run_tables.sql`: create run and source-run tables.
- Create `src/main/java/cn/name/celestrong/signalbrief/ingestion/IngestionTriggerType.java`: `MANUAL` / `SCHEDULED`.
- Create `src/main/java/cn/name/celestrong/signalbrief/ingestion/IngestionRunStatus.java`: batch run status.
- Create `src/main/java/cn/name/celestrong/signalbrief/ingestion/IngestionSourceRunStatus.java`: source run status.
- Create `src/main/java/cn/name/celestrong/signalbrief/ingestion/NewRssIngestionSourceRun.java`: insert command for source records.
- Create `src/main/java/cn/name/celestrong/signalbrief/ingestion/RssIngestionRun.java`: batch run read model.
- Create `src/main/java/cn/name/celestrong/signalbrief/ingestion/RssIngestionSourceRun.java`: source run read model.
- Create `src/main/java/cn/name/celestrong/signalbrief/ingestion/RssIngestionRunDetail.java`: run detail read model.
- Create `src/main/java/cn/name/celestrong/signalbrief/ingestion/RssIngestionRunMapper.java`: MyBatis persistence and queries.
- Create `src/main/java/cn/name/celestrong/signalbrief/ingestion/IngestionRunRecorder.java`: persistence facade used by ingestion orchestration.
- Create `src/main/java/cn/name/celestrong/signalbrief/ingestion/RssIngestionRunQueryService.java`: internal query service with limit validation.
- Modify `src/main/java/cn/name/celestrong/signalbrief/ingestion/FeedIngestionResult.java`: add `runId`.
- Modify `src/main/java/cn/name/celestrong/signalbrief/ingestion/FeedIngestionService.java`: record run and source outcomes.
- Modify `src/main/java/cn/name/celestrong/signalbrief/ingestion/FeedIngestionScheduler.java`: pass `SCHEDULED`.
- Modify `src/main/java/cn/name/celestrong/signalbrief/internal/ManualTriggerController.java`: pass `MANUAL` and add query endpoints.
- Modify tests under `src/test/java/cn/name/celestrong/signalbrief/ingestion` and `src/test/java/cn/name/celestrong/signalbrief/internal`.
- Modify `README.md`, `docs/records/rss-ingestion.md`, `docs/records/rss-source-reliability.md`, and `docs/personal-tech-newsletter-system.md`.

---

### Task 1: Database Model and Mapper

**Files:**
- Create: `src/main/resources/db/migration/V2__create_rss_ingestion_run_tables.sql`
- Create: `src/main/java/cn/name/celestrong/signalbrief/ingestion/IngestionTriggerType.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/ingestion/IngestionRunStatus.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/ingestion/IngestionSourceRunStatus.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/ingestion/NewRssIngestionSourceRun.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/ingestion/RssIngestionRun.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/ingestion/RssIngestionSourceRun.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/ingestion/RssIngestionRunDetail.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/ingestion/RssIngestionRunMapper.java`
- Create: `src/test/java/cn/name/celestrong/signalbrief/ingestion/RssIngestionRunMapperIT.java`

- [ ] **Step 1: Write the failing Mapper IT**

Create `src/test/java/cn/name/celestrong/signalbrief/ingestion/RssIngestionRunMapperIT.java`:

```java
package cn.name.celestrong.signalbrief.ingestion;

import cn.name.celestrong.signalbrief.article.ArticleCategory;
import cn.name.celestrong.signalbrief.feed.FeedFetchFailureType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Sql(
        statements = "TRUNCATE TABLE rss_ingestion_source_run, rss_ingestion_run RESTART IDENTITY",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class RssIngestionRunMapperIT {

    @Autowired
    private RssIngestionRunMapper mapper;

    @Test
    void insertsRunAndSourceRecordsThenFindsDetail() {
        Instant startedAt = Instant.parse("2026-07-05T00:00:00Z");
        Long runId = mapper.insertRun(
                IngestionTriggerType.MANUAL,
                IngestionRunStatus.RUNNING,
                startedAt,
                2
        );

        assertNotNull(runId);

        mapper.insertSourceRun(new NewRssIngestionSourceRun(
                runId,
                "Spring Blog",
                "https://spring.io/blog.atom",
                ArticleCategory.FRAMEWORK,
                IngestionSourceRunStatus.SUCCESS,
                null,
                null,
                null,
                null,
                null,
                5,
                3,
                2,
                startedAt,
                startedAt.plusSeconds(2),
                2_000L
        ));
        mapper.insertSourceRun(new NewRssIngestionSourceRun(
                runId,
                "Failing",
                "https://example.com/failing.xml",
                ArticleCategory.JAVA,
                IngestionSourceRunStatus.FAILED,
                FeedFetchFailureType.HTTP_STATUS,
                503,
                2,
                2,
                "Feed fetch failed with HTTP status 503",
                0,
                0,
                0,
                startedAt.plusSeconds(3),
                startedAt.plusSeconds(5),
                2_000L
        ));

        mapper.finishRun(
                runId,
                IngestionRunStatus.PARTIAL_SUCCESS,
                startedAt.plusSeconds(6),
                6_000L,
                5,
                3,
                2,
                1
        );

        RssIngestionRun run = mapper.findRunById(runId).orElseThrow();
        List<RssIngestionSourceRun> sources = mapper.findSourcesByRunId(runId);

        assertEquals(IngestionTriggerType.MANUAL, run.triggerType());
        assertEquals(IngestionRunStatus.PARTIAL_SUCCESS, run.status());
        assertEquals(2, run.sourceCount());
        assertEquals(1, run.failedSourceCount());
        assertEquals(List.of("Spring Blog", "Failing"), sources.stream().map(RssIngestionSourceRun::sourceName).toList());
    }

    @Test
    void findsRecentRunsByStartedAtAndIdDescending() {
        Long older = mapper.insertRun(
                IngestionTriggerType.MANUAL,
                IngestionRunStatus.SUCCESS,
                Instant.parse("2026-07-04T00:00:00Z"),
                1
        );
        Long newer = mapper.insertRun(
                IngestionTriggerType.SCHEDULED,
                IngestionRunStatus.SUCCESS,
                Instant.parse("2026-07-05T00:00:00Z"),
                1
        );

        List<RssIngestionRun> runs = mapper.findRecentRuns(10);

        assertEquals(List.of(newer, older), runs.stream().map(RssIngestionRun::id).toList());
    }
}
```

- [ ] **Step 2: Run the Mapper IT and verify it fails before implementation**

Run in an environment with PostgreSQL configured for the test profile:

```bash
SPRING_PROFILES_ACTIVE=test ./mvnw -Dspring.docker.compose.enabled=false -DskipTests -Dit.test=RssIngestionRunMapperIT verify
```

Expected: compilation fails because `RssIngestionRunMapper` and the run model types do not exist.

- [ ] **Step 3: Add the Flyway migration**

Create `src/main/resources/db/migration/V2__create_rss_ingestion_run_tables.sql`:

```sql
CREATE TABLE rss_ingestion_run (
    id BIGSERIAL PRIMARY KEY,
    trigger_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    duration_millis BIGINT,
    source_count INTEGER NOT NULL DEFAULT 0,
    fetched_count INTEGER NOT NULL DEFAULT 0,
    inserted_count INTEGER NOT NULL DEFAULT 0,
    skipped_count INTEGER NOT NULL DEFAULT 0,
    failed_source_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_rss_ingestion_run_trigger_type
        CHECK (trigger_type IN ('MANUAL', 'SCHEDULED')),
    CONSTRAINT ck_rss_ingestion_run_status
        CHECK (status IN ('RUNNING', 'SUCCESS', 'PARTIAL_SUCCESS', 'FAILED'))
);

CREATE INDEX idx_rss_ingestion_run_started_at
    ON rss_ingestion_run (started_at DESC, id DESC);

CREATE TABLE rss_ingestion_source_run (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES rss_ingestion_run (id) ON DELETE CASCADE,
    source_name VARCHAR(200) NOT NULL,
    source_url TEXT NOT NULL,
    category VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    failure_type VARCHAR(50),
    http_status INTEGER,
    attempt_count INTEGER,
    max_attempts INTEGER,
    error_message VARCHAR(1000),
    fetched_count INTEGER NOT NULL DEFAULT 0,
    inserted_count INTEGER NOT NULL DEFAULT 0,
    skipped_count INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ NOT NULL,
    duration_millis BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_rss_ingestion_source_run_status
        CHECK (status IN ('SUCCESS', 'FAILED'))
);

CREATE INDEX idx_rss_ingestion_source_run_run_id
    ON rss_ingestion_source_run (run_id, id);
```

- [ ] **Step 4: Add enum and record model types**

Create `IngestionTriggerType.java`:

```java
package cn.name.celestrong.signalbrief.ingestion;

public enum IngestionTriggerType {
    MANUAL,
    SCHEDULED
}
```

Create `IngestionRunStatus.java`:

```java
package cn.name.celestrong.signalbrief.ingestion;

public enum IngestionRunStatus {
    RUNNING,
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILED
}
```

Create `IngestionSourceRunStatus.java`:

```java
package cn.name.celestrong.signalbrief.ingestion;

public enum IngestionSourceRunStatus {
    SUCCESS,
    FAILED
}
```

Create `NewRssIngestionSourceRun.java`:

```java
package cn.name.celestrong.signalbrief.ingestion;

import cn.name.celestrong.signalbrief.article.ArticleCategory;
import cn.name.celestrong.signalbrief.feed.FeedFetchFailureType;

import java.time.Instant;

public record NewRssIngestionSourceRun(
        Long runId,
        String sourceName,
        String sourceUrl,
        ArticleCategory category,
        IngestionSourceRunStatus status,
        FeedFetchFailureType failureType,
        Integer httpStatus,
        Integer attemptCount,
        Integer maxAttempts,
        String errorMessage,
        int fetchedCount,
        int insertedCount,
        int skippedCount,
        Instant startedAt,
        Instant finishedAt,
        long durationMillis
) {
}
```

Create `RssIngestionRun.java`:

```java
package cn.name.celestrong.signalbrief.ingestion;

import java.time.Instant;

public record RssIngestionRun(
        Long id,
        IngestionTriggerType triggerType,
        IngestionRunStatus status,
        Instant startedAt,
        Instant finishedAt,
        Long durationMillis,
        int sourceCount,
        int fetchedCount,
        int insertedCount,
        int skippedCount,
        int failedSourceCount,
        Instant createdAt,
        Instant updatedAt
) {
}
```

Create `RssIngestionSourceRun.java`:

```java
package cn.name.celestrong.signalbrief.ingestion;

import cn.name.celestrong.signalbrief.article.ArticleCategory;
import cn.name.celestrong.signalbrief.feed.FeedFetchFailureType;

import java.time.Instant;

public record RssIngestionSourceRun(
        Long id,
        Long runId,
        String sourceName,
        String sourceUrl,
        ArticleCategory category,
        IngestionSourceRunStatus status,
        FeedFetchFailureType failureType,
        Integer httpStatus,
        Integer attemptCount,
        Integer maxAttempts,
        String errorMessage,
        int fetchedCount,
        int insertedCount,
        int skippedCount,
        Instant startedAt,
        Instant finishedAt,
        long durationMillis,
        Instant createdAt
) {
}
```

Create `RssIngestionRunDetail.java`:

```java
package cn.name.celestrong.signalbrief.ingestion;

import java.util.List;

public record RssIngestionRunDetail(
        RssIngestionRun run,
        List<RssIngestionSourceRun> sources
) {
}
```

- [ ] **Step 5: Add `RssIngestionRunMapper`**

Create `src/main/java/cn/name/celestrong/signalbrief/ingestion/RssIngestionRunMapper.java` with these methods:

```java
package cn.name.celestrong.signalbrief.ingestion;

import cn.name.celestrong.signalbrief.article.ArticleCategory;
import cn.name.celestrong.signalbrief.feed.FeedFetchFailureType;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Mapper
public interface RssIngestionRunMapper {

    @Select("""
            INSERT INTO rss_ingestion_run (
                trigger_type,
                status,
                started_at,
                source_count
            ) VALUES (
                #{triggerType},
                #{status},
                #{startedAt},
                #{sourceCount}
            )
            RETURNING id
            """)
    Long insertRun(
            @Param("triggerType") IngestionTriggerType triggerType,
            @Param("status") IngestionRunStatus status,
            @Param("startedAt") Instant startedAt,
            @Param("sourceCount") int sourceCount
    );

    @Insert("""
            INSERT INTO rss_ingestion_source_run (
                run_id,
                source_name,
                source_url,
                category,
                status,
                failure_type,
                http_status,
                attempt_count,
                max_attempts,
                error_message,
                fetched_count,
                inserted_count,
                skipped_count,
                started_at,
                finished_at,
                duration_millis
            ) VALUES (
                #{runId},
                #{sourceName},
                #{sourceUrl},
                #{category},
                #{status},
                #{failureType},
                #{httpStatus},
                #{attemptCount},
                #{maxAttempts},
                #{errorMessage},
                #{fetchedCount},
                #{insertedCount},
                #{skippedCount},
                #{startedAt},
                #{finishedAt},
                #{durationMillis}
            )
            """)
    int insertSourceRun(NewRssIngestionSourceRun sourceRun);

    @Update("""
            UPDATE rss_ingestion_run
            SET status = #{status},
                finished_at = #{finishedAt},
                duration_millis = #{durationMillis},
                fetched_count = #{fetchedCount},
                inserted_count = #{insertedCount},
                skipped_count = #{skippedCount},
                failed_source_count = #{failedSourceCount},
                updated_at = now()
            WHERE id = #{runId}
            """)
    int finishRun(
            @Param("runId") Long runId,
            @Param("status") IngestionRunStatus status,
            @Param("finishedAt") Instant finishedAt,
            @Param("durationMillis") long durationMillis,
            @Param("fetchedCount") int fetchedCount,
            @Param("insertedCount") int insertedCount,
            @Param("skippedCount") int skippedCount,
            @Param("failedSourceCount") int failedSourceCount
    );

    @Select("""
            SELECT
                id,
                trigger_type AS "triggerType",
                status,
                started_at AS "startedAt",
                finished_at AS "finishedAt",
                duration_millis AS "durationMillis",
                source_count AS "sourceCount",
                fetched_count AS "fetchedCount",
                inserted_count AS "insertedCount",
                skipped_count AS "skippedCount",
                failed_source_count AS "failedSourceCount",
                created_at AS "createdAt",
                updated_at AS "updatedAt"
            FROM rss_ingestion_run
            WHERE id = #{runId}
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "triggerType", javaType = IngestionTriggerType.class),
            @Arg(column = "status", javaType = IngestionRunStatus.class),
            @Arg(column = "startedAt", javaType = Instant.class),
            @Arg(column = "finishedAt", javaType = Instant.class),
            @Arg(column = "durationMillis", javaType = Long.class),
            @Arg(column = "sourceCount", javaType = Integer.class),
            @Arg(column = "fetchedCount", javaType = Integer.class),
            @Arg(column = "insertedCount", javaType = Integer.class),
            @Arg(column = "skippedCount", javaType = Integer.class),
            @Arg(column = "failedSourceCount", javaType = Integer.class),
            @Arg(column = "createdAt", javaType = Instant.class),
            @Arg(column = "updatedAt", javaType = Instant.class)
    })
    Optional<RssIngestionRun> findRunById(@Param("runId") Long runId);

    @Select("""
            SELECT
                id,
                trigger_type AS "triggerType",
                status,
                started_at AS "startedAt",
                finished_at AS "finishedAt",
                duration_millis AS "durationMillis",
                source_count AS "sourceCount",
                fetched_count AS "fetchedCount",
                inserted_count AS "insertedCount",
                skipped_count AS "skippedCount",
                failed_source_count AS "failedSourceCount",
                created_at AS "createdAt",
                updated_at AS "updatedAt"
            FROM rss_ingestion_run
            ORDER BY started_at DESC, id DESC
            LIMIT #{limit}
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "triggerType", javaType = IngestionTriggerType.class),
            @Arg(column = "status", javaType = IngestionRunStatus.class),
            @Arg(column = "startedAt", javaType = Instant.class),
            @Arg(column = "finishedAt", javaType = Instant.class),
            @Arg(column = "durationMillis", javaType = Long.class),
            @Arg(column = "sourceCount", javaType = Integer.class),
            @Arg(column = "fetchedCount", javaType = Integer.class),
            @Arg(column = "insertedCount", javaType = Integer.class),
            @Arg(column = "skippedCount", javaType = Integer.class),
            @Arg(column = "failedSourceCount", javaType = Integer.class),
            @Arg(column = "createdAt", javaType = Instant.class),
            @Arg(column = "updatedAt", javaType = Instant.class)
    })
    List<RssIngestionRun> findRecentRuns(@Param("limit") int limit);

    @Select("""
            SELECT
                id,
                run_id AS "runId",
                source_name AS "sourceName",
                source_url AS "sourceUrl",
                category,
                status,
                failure_type AS "failureType",
                http_status AS "httpStatus",
                attempt_count AS "attemptCount",
                max_attempts AS "maxAttempts",
                error_message AS "errorMessage",
                fetched_count AS "fetchedCount",
                inserted_count AS "insertedCount",
                skipped_count AS "skippedCount",
                started_at AS "startedAt",
                finished_at AS "finishedAt",
                duration_millis AS "durationMillis",
                created_at AS "createdAt"
            FROM rss_ingestion_source_run
            WHERE run_id = #{runId}
            ORDER BY id ASC
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "runId", javaType = Long.class),
            @Arg(column = "sourceName", javaType = String.class),
            @Arg(column = "sourceUrl", javaType = String.class),
            @Arg(column = "category", javaType = ArticleCategory.class),
            @Arg(column = "status", javaType = IngestionSourceRunStatus.class),
            @Arg(column = "failureType", javaType = FeedFetchFailureType.class),
            @Arg(column = "httpStatus", javaType = Integer.class),
            @Arg(column = "attemptCount", javaType = Integer.class),
            @Arg(column = "maxAttempts", javaType = Integer.class),
            @Arg(column = "errorMessage", javaType = String.class),
            @Arg(column = "fetchedCount", javaType = Integer.class),
            @Arg(column = "insertedCount", javaType = Integer.class),
            @Arg(column = "skippedCount", javaType = Integer.class),
            @Arg(column = "startedAt", javaType = Instant.class),
            @Arg(column = "finishedAt", javaType = Instant.class),
            @Arg(column = "durationMillis", javaType = Long.class),
            @Arg(column = "createdAt", javaType = Instant.class)
    })
    List<RssIngestionSourceRun> findSourcesByRunId(@Param("runId") Long runId);
}
```

- [ ] **Step 6: Run focused compilation**

Run:

```bash
./mvnw -Dspring.docker.compose.enabled=false -DskipTests compile
```

Expected: main sources compile. If MyBatis annotations reference a missing column alias or record constructor mismatch, fix the mapper before continuing.

---

### Task 2: Ingestion Run Recorder

**Files:**
- Create: `src/main/java/cn/name/celestrong/signalbrief/ingestion/IngestionRunRecorder.java`
- Create: `src/test/java/cn/name/celestrong/signalbrief/ingestion/IngestionRunRecorderTest.java`

- [ ] **Step 1: Write recorder unit tests with a fake mapper**

Create `IngestionRunRecorderTest.java` covering:

```java
@Test
void finishesSuccessfulRun() {
    FakeRssIngestionRunMapper mapper = new FakeRssIngestionRunMapper();
    IngestionRunRecorder recorder = new IngestionRunRecorder(mapper, fixedClock());

    IngestionRunRecorder.RunContext context = recorder.startRun(IngestionTriggerType.MANUAL, 2);
    FeedIngestionResult result = new FeedIngestionResult(context.runId(), 2, 5, 3, 2, 0);

    recorder.finishRun(context, result);

    assertEquals(IngestionRunStatus.SUCCESS, mapper.finishedStatus);
    assertEquals(5, mapper.fetchedCount);
    assertEquals(3, mapper.insertedCount);
    assertEquals(2, mapper.skippedCount);
    assertEquals(0, mapper.failedSourceCount);
}

@Test
void marksPartialSuccessWhenAnySourceFails() {
    FakeRssIngestionRunMapper mapper = new FakeRssIngestionRunMapper();
    IngestionRunRecorder recorder = new IngestionRunRecorder(mapper, fixedClock());

    IngestionRunRecorder.RunContext context = recorder.startRun(IngestionTriggerType.MANUAL, 2);
    FeedIngestionResult result = new FeedIngestionResult(context.runId(), 2, 5, 3, 2, 1);

    recorder.finishRun(context, result);

    assertEquals(IngestionRunStatus.PARTIAL_SUCCESS, mapper.finishedStatus);
}

@Test
void truncatesLongErrorMessage() {
    FakeRssIngestionRunMapper mapper = new FakeRssIngestionRunMapper();
    IngestionRunRecorder recorder = new IngestionRunRecorder(mapper, fixedClock());
    IngestionRunRecorder.RunContext run = recorder.startRun(IngestionTriggerType.MANUAL, 1);
    IngestionRunRecorder.SourceRunContext source = recorder.startSource(run, sampleSource());

    recorder.recordSourceFailure(source, new IllegalStateException("x".repeat(2_000)));

    assertEquals(1_000, mapper.lastSourceRun.errorMessage().length());
}
```

The fake mapper should store the last inserted source run and the last finished run values. Put the fake mapper as a private static class in the test.

- [ ] **Step 2: Run the recorder tests and verify failure**

Run:

```bash
./mvnw -Dspring.docker.compose.enabled=false -Dtest=IngestionRunRecorderTest test
```

Expected: compilation fails because `IngestionRunRecorder` does not exist.

- [ ] **Step 3: Implement `IngestionRunRecorder`**

Create `src/main/java/cn/name/celestrong/signalbrief/ingestion/IngestionRunRecorder.java`:

```java
package cn.name.celestrong.signalbrief.ingestion;

import cn.name.celestrong.signalbrief.config.FeedProperties;
import cn.name.celestrong.signalbrief.feed.FeedFetchException;
import cn.name.celestrong.signalbrief.feed.FeedFetchFailureType;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class IngestionRunRecorder {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1_000;

    private final RssIngestionRunMapper mapper;
    private final Clock clock;

    public IngestionRunRecorder(RssIngestionRunMapper mapper) {
        this(mapper, Clock.systemUTC());
    }

    IngestionRunRecorder(RssIngestionRunMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    public RunContext startRun(IngestionTriggerType triggerType, int sourceCount) {
        Instant startedAt = clock.instant();
        Long runId = mapper.insertRun(triggerType, IngestionRunStatus.RUNNING, startedAt, sourceCount);
        return new RunContext(runId, startedAt);
    }

    public SourceRunContext startSource(RunContext runContext, FeedProperties.FeedSource source) {
        return new SourceRunContext(runContext.runId(), source, clock.instant());
    }

    public void recordSourceSuccess(SourceRunContext context, FeedIngestionResult result) {
        Instant finishedAt = clock.instant();
        mapper.insertSourceRun(new NewRssIngestionSourceRun(
                context.runId(),
                context.source().name(),
                context.source().url().toString(),
                context.source().category(),
                IngestionSourceRunStatus.SUCCESS,
                null,
                null,
                null,
                null,
                null,
                result.fetchedCount(),
                result.insertedCount(),
                result.skippedCount(),
                context.startedAt(),
                finishedAt,
                durationMillis(context.startedAt(), finishedAt)
        ));
    }

    public void recordSourceFailure(SourceRunContext context, Exception exception) {
        Instant finishedAt = clock.instant();
        FeedFetchException fetchException = exception instanceof FeedFetchException typed ? typed : null;
        mapper.insertSourceRun(new NewRssIngestionSourceRun(
                context.runId(),
                context.source().name(),
                context.source().url().toString(),
                context.source().category(),
                IngestionSourceRunStatus.FAILED,
                fetchException == null ? FeedFetchFailureType.UNEXPECTED : fetchException.failureType(),
                fetchException == null ? null : fetchException.httpStatus(),
                fetchException == null ? null : fetchException.attemptCount(),
                fetchException == null ? null : fetchException.maxAttempts(),
                truncateErrorMessage(exception.getMessage()),
                0,
                0,
                0,
                context.startedAt(),
                finishedAt,
                durationMillis(context.startedAt(), finishedAt)
        ));
    }

    public void finishRun(RunContext context, FeedIngestionResult result) {
        Instant finishedAt = clock.instant();
        mapper.finishRun(
                context.runId(),
                statusFor(result),
                finishedAt,
                durationMillis(context.startedAt(), finishedAt),
                result.fetchedCount(),
                result.insertedCount(),
                result.skippedCount(),
                result.failedSourceCount()
        );
    }

    public void failRun(RunContext context, FeedIngestionResult result) {
        Instant finishedAt = clock.instant();
        mapper.finishRun(
                context.runId(),
                IngestionRunStatus.FAILED,
                finishedAt,
                durationMillis(context.startedAt(), finishedAt),
                result.fetchedCount(),
                result.insertedCount(),
                result.skippedCount(),
                result.failedSourceCount()
        );
    }

    private IngestionRunStatus statusFor(FeedIngestionResult result) {
        if (result.failedSourceCount() == 0) {
            return IngestionRunStatus.SUCCESS;
        }
        if (result.failedSourceCount() >= result.sourceCount()) {
            return IngestionRunStatus.FAILED;
        }
        return IngestionRunStatus.PARTIAL_SUCCESS;
    }

    private String truncateErrorMessage(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_MESSAGE_LENGTH
                ? message
                : message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private long durationMillis(Instant startedAt, Instant finishedAt) {
        return Duration.between(startedAt, finishedAt).toMillis();
    }

    public record RunContext(Long runId, Instant startedAt) {
    }

    public record SourceRunContext(Long runId, FeedProperties.FeedSource source, Instant startedAt) {
    }
}
```

- [ ] **Step 4: Run recorder tests**

Run:

```bash
./mvnw -Dspring.docker.compose.enabled=false -Dtest=IngestionRunRecorderTest test
```

Expected: recorder tests pass.

---

### Task 3: Wire Run Recording into RSS Ingestion

**Files:**
- Modify: `src/main/java/cn/name/celestrong/signalbrief/ingestion/FeedIngestionResult.java`
- Modify: `src/main/java/cn/name/celestrong/signalbrief/ingestion/FeedIngestionService.java`
- Modify: `src/main/java/cn/name/celestrong/signalbrief/ingestion/FeedIngestionScheduler.java`
- Modify: `src/test/java/cn/name/celestrong/signalbrief/ingestion/FeedIngestionServiceTest.java`
- Modify: `src/test/java/cn/name/celestrong/signalbrief/ingestion/FeedIngestionSchedulerTest.java`

- [ ] **Step 1: Update tests for `runId` and trigger type**

In `FeedIngestionServiceTest`, construct the service with a fake recorder and assert:

```java
FeedIngestionResult result = service.ingestEnabledFeeds(IngestionTriggerType.MANUAL);

assertEquals(100L, result.runId());
assertEquals(IngestionTriggerType.MANUAL, recorder.triggerType);
assertEquals(List.of(IngestionSourceRunStatus.SUCCESS), recorder.sourceStatuses);
assertEquals(IngestionRunStatus.SUCCESS, recorder.finishedStatus);
```

For the partial failure test, assert:

```java
assertEquals(List.of(IngestionSourceRunStatus.FAILED, IngestionSourceRunStatus.SUCCESS), recorder.sourceStatuses);
assertEquals(IngestionRunStatus.PARTIAL_SUCCESS, recorder.finishedStatus);
```

In `FeedIngestionSchedulerTest`, change the fake service to override `ingestEnabledFeeds(IngestionTriggerType triggerType)` and assert `SCHEDULED`.

- [ ] **Step 2: Run affected tests and verify failure**

Run:

```bash
./mvnw -Dspring.docker.compose.enabled=false -Dtest=FeedIngestionServiceTest,FeedIngestionSchedulerTest test
```

Expected: compilation fails because the new `runId` result constructor and trigger-type method are not implemented.

- [ ] **Step 3: Update `FeedIngestionResult`**

Replace `FeedIngestionResult` with:

```java
package cn.name.celestrong.signalbrief.ingestion;

public record FeedIngestionResult(
        Long runId,
        int sourceCount,
        int fetchedCount,
        int insertedCount,
        int skippedCount,
        int failedSourceCount
) {
    public FeedIngestionResult plus(FeedIngestionResult other) {
        return new FeedIngestionResult(
                mergeRunId(other),
                sourceCount + other.sourceCount,
                fetchedCount + other.fetchedCount,
                insertedCount + other.insertedCount,
                skippedCount + other.skippedCount,
                failedSourceCount + other.failedSourceCount
        );
    }

    public FeedIngestionResult withRunId(Long runId) {
        return new FeedIngestionResult(runId, sourceCount, fetchedCount, insertedCount, skippedCount, failedSourceCount);
    }

    private Long mergeRunId(FeedIngestionResult other) {
        if (runId == null) {
            return other.runId;
        }
        if (other.runId == null || runId.equals(other.runId)) {
            return runId;
        }
        throw new IllegalArgumentException("Cannot merge ingestion results from different runs");
    }

    public static FeedIngestionResult empty() {
        return empty(null);
    }

    public static FeedIngestionResult empty(Long runId) {
        return new FeedIngestionResult(runId, 0, 0, 0, 0, 0);
    }
}
```

Update all direct constructor calls in tests and production code to include `runId` as the first argument. Source-level temporary results should use `null`; batch results should start with the real run id.

- [ ] **Step 4: Update `FeedIngestionService`**

Add `IngestionRunRecorder` to the constructor. Implement:

```java
public FeedIngestionResult ingestEnabledFeeds() {
    return ingestEnabledFeeds(IngestionTriggerType.MANUAL);
}

public FeedIngestionResult ingestEnabledFeeds(IngestionTriggerType triggerType) {
    List<FeedProperties.FeedSource> enabledFeeds = feedProperties.enabledFeeds();
    IngestionRunRecorder.RunContext runContext = runRecorder.startRun(triggerType, enabledFeeds.size());
    FeedIngestionResult result = FeedIngestionResult.empty(runContext.runId());
    try {
        for (FeedProperties.FeedSource source : enabledFeeds) {
            result = result.plus(ingestSource(runContext, source));
        }
        runRecorder.finishRun(runContext, result);
        log.info(
                "Feed ingestion completed: runId={}, sources={}, fetched={}, inserted={}, skipped={}, failedSources={}",
                result.runId(),
                result.sourceCount(),
                result.fetchedCount(),
                result.insertedCount(),
                result.skippedCount(),
                result.failedSourceCount()
        );
        return result;
    } catch (RuntimeException ex) {
        runRecorder.failRun(runContext, result);
        throw ex;
    }
}
```

Change `ingestSource` to start a source context, record success after article insertion, and record failure in both catch blocks:

```java
private FeedIngestionResult ingestSource(
        IngestionRunRecorder.RunContext runContext,
        FeedProperties.FeedSource source
) {
    IngestionRunRecorder.SourceRunContext sourceContext = runRecorder.startSource(runContext, source);
    try (InputStream inputStream = feedClient.fetch(source)) {
        List<FetchedArticle> articles = feedParser.parse(source, inputStream);
        int insertedCount = 0;
        int skippedCount = 0;
        for (FetchedArticle article : articles) {
            ArticleIngestionService.Result result = articleIngestionService.ingest(article);
            insertedCount += result.insertedCount();
            skippedCount += result.skippedCount();
        }
        FeedIngestionResult result = new FeedIngestionResult(null, 1, articles.size(), insertedCount, skippedCount, 0);
        runRecorder.recordSourceSuccess(sourceContext, result);
        return result;
    } catch (FeedFetchException ex) {
        runRecorder.recordSourceFailure(sourceContext, ex);
        log.warn(
                "Failed to fetch feed source name={}, url={}, failureType={}, httpStatus={}, attempts={}/{}",
                source.name(),
                source.url(),
                ex.failureType(),
                ex.httpStatus(),
                ex.attemptCount(),
                ex.maxAttempts(),
                ex
        );
        return new FeedIngestionResult(null, 1, 0, 0, 0, 1);
    } catch (Exception ex) {
        runRecorder.recordSourceFailure(sourceContext, ex);
        log.warn("Failed to ingest feed source name={}, url={}", source.name(), source.url(), ex);
        return new FeedIngestionResult(null, 1, 0, 0, 0, 1);
    }
}
```

- [ ] **Step 5: Update scheduler**

Change `FeedIngestionScheduler.ingestEnabledFeeds()` to call:

```java
FeedIngestionResult result = feedIngestionService.ingestEnabledFeeds(IngestionTriggerType.SCHEDULED);
```

Include `runId` in the scheduler log message.

- [ ] **Step 6: Run affected tests**

Run:

```bash
./mvnw -Dspring.docker.compose.enabled=false -Dtest=FeedIngestionServiceTest,FeedIngestionSchedulerTest test
```

Expected: both test classes pass.

---

### Task 4: Internal Query API

**Files:**
- Create: `src/main/java/cn/name/celestrong/signalbrief/ingestion/RssIngestionRunQueryService.java`
- Modify: `src/main/java/cn/name/celestrong/signalbrief/internal/ManualTriggerController.java`
- Modify: `src/test/java/cn/name/celestrong/signalbrief/internal/ManualTriggerControllerTest.java`

- [ ] **Step 1: Update controller tests**

In `ManualTriggerControllerTest`, update the fake ingestion result:

```java
return new FeedIngestionResult(12L, 2, 8, 3, 5, 1);
```

Assert the trigger response includes `runId`:

```java
.andExpect(jsonPath("$.runId").value(12))
```

Add tests:

```java
@Test
void listsRssIngestionRuns() throws Exception {
    mockMvc.perform(get("/internal/ingestions/rss/runs").param("limit", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(12))
            .andExpect(jsonPath("$[0].status").value("PARTIAL_SUCCESS"));

    assertEquals(5, runQueryService.limit);
}

@Test
void getsRssIngestionRunDetail() throws Exception {
    mockMvc.perform(get("/internal/ingestions/rss/runs/12"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.run.id").value(12))
            .andExpect(jsonPath("$.sources[0].sourceName").value("Spring Blog"));
}
```

Add `MockMvcRequestBuilders.get` static import and a fake `RssIngestionRunQueryService` bean in the test configuration.

- [ ] **Step 2: Run controller tests and verify failure**

Run:

```bash
./mvnw -Dspring.docker.compose.enabled=false -Dtest=ManualTriggerControllerTest test
```

Expected: compilation fails because `RssIngestionRunQueryService` and the controller endpoints do not exist.

- [ ] **Step 3: Implement `RssIngestionRunQueryService`**

Create:

```java
package cn.name.celestrong.signalbrief.ingestion;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RssIngestionRunQueryService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final RssIngestionRunMapper mapper;

    public RssIngestionRunQueryService(RssIngestionRunMapper mapper) {
        this.mapper = mapper;
    }

    public List<RssIngestionRun> findRecentRuns(Integer limit) {
        return mapper.findRecentRuns(resolveLimit(limit));
    }

    public RssIngestionRunDetail findRunDetail(Long runId) {
        RssIngestionRun run = mapper.findRunById(runId)
                .orElseThrow(() -> new IllegalArgumentException("RSS 入库运行记录不存在"));
        return new RssIngestionRunDetail(run, mapper.findSourcesByRunId(runId));
    }

    private int resolveLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
```

- [ ] **Step 4: Update `ManualTriggerController`**

Inject `RssIngestionRunQueryService`. Add:

```java
@GetMapping("/ingestions/rss/runs")
public List<RssIngestionRun> listRssIngestionRuns(@RequestParam(required = false) Integer limit) {
    return runQueryService.findRecentRuns(limit);
}

@GetMapping("/ingestions/rss/runs/{id}")
public RssIngestionRunDetail getRssIngestionRun(@PathVariable Long id) {
    return runQueryService.findRunDetail(id);
}
```

Change the trigger method to:

```java
return feedIngestionService.ingestEnabledFeeds(IngestionTriggerType.MANUAL);
```

Add OpenAPI `@Operation` and `@ApiResponse` annotations following the existing controller style.

- [ ] **Step 5: Run controller tests**

Run:

```bash
./mvnw -Dspring.docker.compose.enabled=false -Dtest=ManualTriggerControllerTest test
```

Expected: controller tests pass.

---

### Task 5: Documentation and Verification

**Files:**
- Modify: `README.md`
- Modify: `docs/records/rss-ingestion.md`
- Modify: `docs/records/rss-source-reliability.md`
- Modify: `docs/personal-tech-newsletter-system.md`

- [ ] **Step 1: Update README internal API section**

Add examples:

```bash
curl http://localhost:8080/internal/ingestions/rss/runs
curl http://localhost:8080/internal/ingestions/rss/runs/12
```

Mention that `POST /internal/ingestions/rss` returns `runId`.

- [ ] **Step 2: Update records**

In `docs/records/rss-ingestion.md`, add that RSS ingestion now persists run and source-level records.

In `docs/records/rss-source-reliability.md`, replace “任务运行记录属于后续设计” with a reference to the new run record capability.

In `docs/personal-tech-newsletter-system.md`, move task run records from future work into current capabilities and keep alerting as future work.

- [ ] **Step 3: Run local unit and slice tests**

Run:

```bash
./mvnw -Dspring.docker.compose.enabled=false test
```

Expected: all local unit and slice tests pass.

- [ ] **Step 4: Run whitespace check**

Run:

```bash
git diff --check
```

Expected: no output.

- [ ] **Step 5: Run database integration tests in CI or a configured PostgreSQL environment**

Run:

```bash
SPRING_PROFILES_ACTIVE=test ./mvnw -Dspring.docker.compose.enabled=false -DskipTests -Dit.test=RssIngestionRunMapperIT verify
```

Expected: `RssIngestionRunMapperIT` passes when datasource environment variables point to a PostgreSQL database.

- [ ] **Step 6: Review changed files**

Run:

```bash
git status --short
git diff --stat
```

Expected: changes are limited to RSS ingestion run records, internal API query endpoints, tests, migration, and docs.

- [ ] **Step 7: Stop before commit**

Do not commit automatically. This repository requires explicit user approval before `git add` and `git commit`. When the user says “提交”, run the matching verification again and commit with a Conventional Commit message such as:

```text
feat(ingestion): 记录 RSS 入库运行明细
```
