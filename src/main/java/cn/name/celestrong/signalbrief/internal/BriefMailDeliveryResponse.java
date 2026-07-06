package cn.name.celestrong.signalbrief.internal;

import cn.name.celestrong.signalbrief.mail.BriefMailDelivery;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "简报邮件发送响应")
public record BriefMailDeliveryResponse(
        @Schema(description = "简报归档记录 ID", example = "100")
        Long briefGenerationId,

        @Schema(description = "邮件发送记录列表")
        List<BriefMailDelivery> deliveries
) {
    public BriefMailDeliveryResponse {
        deliveries = List.copyOf(deliveries);
    }
}
