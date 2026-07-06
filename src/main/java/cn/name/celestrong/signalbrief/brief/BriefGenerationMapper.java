package cn.name.celestrong.signalbrief.brief;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.Optional;

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
