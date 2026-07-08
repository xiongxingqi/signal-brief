package cn.name.celestrong.signalbrief.mail;

import java.time.Instant;

/**
 * 单个收件人的简报邮件投递记录。
 */
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
