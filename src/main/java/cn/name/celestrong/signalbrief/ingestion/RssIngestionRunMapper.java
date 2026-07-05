package cn.name.celestrong.signalbrief.ingestion;

import cn.name.celestrong.signalbrief.article.ArticleCategory;
import cn.name.celestrong.signalbrief.feed.FeedFetchFailureType;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * RSS 入库运行记录 Mapper。
 */
@Mapper
public interface RssIngestionRunMapper {

    /**
     * 创建一次入库运行记录并返回数据库生成的主键。
     */
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
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
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
            @Arg(column = "sourceCount", javaType = int.class),
            @Arg(column = "fetchedCount", javaType = int.class),
            @Arg(column = "insertedCount", javaType = int.class),
            @Arg(column = "skippedCount", javaType = int.class),
            @Arg(column = "failedSourceCount", javaType = int.class),
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
            @Arg(column = "sourceCount", javaType = int.class),
            @Arg(column = "fetchedCount", javaType = int.class),
            @Arg(column = "insertedCount", javaType = int.class),
            @Arg(column = "skippedCount", javaType = int.class),
            @Arg(column = "failedSourceCount", javaType = int.class),
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
            @Arg(column = "fetchedCount", javaType = int.class),
            @Arg(column = "insertedCount", javaType = int.class),
            @Arg(column = "skippedCount", javaType = int.class),
            @Arg(column = "startedAt", javaType = Instant.class),
            @Arg(column = "finishedAt", javaType = Instant.class),
            @Arg(column = "durationMillis", javaType = long.class),
            @Arg(column = "createdAt", javaType = Instant.class)
    })
    List<RssIngestionSourceRun> findSourcesByRunId(@Param("runId") Long runId);
}
