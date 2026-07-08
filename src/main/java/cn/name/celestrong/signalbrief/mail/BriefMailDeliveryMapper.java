package cn.name.celestrong.signalbrief.mail;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 简报邮件投递记录 Mapper。
 *
 * <p>每个收件人一条投递记录，状态更新只从 {@code PENDING} 转出，便于重试和审计。</p>
 */
@Mapper
public interface BriefMailDeliveryMapper {

    /**
     * 创建待发送记录，并返回数据库生成的主键。
     */
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

    /**
     * 将待发送记录标记为已发送；返回 1 表示状态转换完成。
     */
    @Update("""
            UPDATE brief_mail_delivery
            SET status = 'SENT',
                error_summary = NULL,
                updated_at = now(),
                sent_at = #{sentAt}
            WHERE id = #{id}
              AND status = 'PENDING'
            """)
    int markSent(
            @Param("id") Long id,
            @Param("sentAt") Instant sentAt
    );

    /**
     * 将待发送记录标记为失败；返回 1 表示状态转换完成。
     */
    @Update("""
            UPDATE brief_mail_delivery
            SET status = 'FAILED',
                error_summary = #{errorSummary},
                updated_at = now(),
                sent_at = NULL
            WHERE id = #{id}
              AND status = 'PENDING'
            """)
    int markFailed(
            @Param("id") Long id,
            @Param("errorSummary") String errorSummary
    );

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

    /**
     * 查询某个简报归档下的全部投递记录。
     */
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
