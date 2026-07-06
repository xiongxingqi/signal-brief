package cn.name.celestrong.signalbrief.mail;

import java.time.Instant;

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
