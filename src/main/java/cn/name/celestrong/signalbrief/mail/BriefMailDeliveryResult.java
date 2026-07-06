package cn.name.celestrong.signalbrief.mail;

import java.util.List;

public record BriefMailDeliveryResult(
        Long briefGenerationId,
        List<BriefMailDelivery> deliveries
) {
    public BriefMailDeliveryResult {
        deliveries = List.copyOf(deliveries);
    }
}
