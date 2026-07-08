package cn.name.celestrong.signalbrief.mail;

import java.util.List;

/**
 * 一次简报邮件发送请求的聚合结果。
 */
public record BriefMailDeliveryResult(
        Long briefGenerationId,
        List<BriefMailDelivery> deliveries
) {
    public BriefMailDeliveryResult {
        deliveries = List.copyOf(deliveries);
    }
}
