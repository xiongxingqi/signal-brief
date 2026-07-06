package cn.name.celestrong.signalbrief.mail;

import cn.name.celestrong.signalbrief.brief.BriefGeneration;
import cn.name.celestrong.signalbrief.brief.BriefGenerationMapper;
import cn.name.celestrong.signalbrief.brief.BriefGenerationNotFoundException;
import cn.name.celestrong.signalbrief.brief.BriefGenerationNotReadyException;
import cn.name.celestrong.signalbrief.brief.BriefGenerationStatus;
import cn.name.celestrong.signalbrief.config.BriefMailProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class BriefMailDeliveryService {

    private static final String DEFAULT_ERROR_SUMMARY = "邮件发送失败";

    private final BriefMailProperties properties;
    private final ObjectProvider<BriefMailSender> senderProvider;
    private final BriefGenerationMapper briefGenerationMapper;
    private final BriefMailDeliveryMapper deliveryMapper;
    private final Clock clock;
    @Autowired
    public BriefMailDeliveryService(
            BriefMailProperties properties,
            ObjectProvider<BriefMailSender> senderProvider,
            BriefGenerationMapper briefGenerationMapper,
            BriefMailDeliveryMapper deliveryMapper
    ) {

        this(properties, senderProvider, briefGenerationMapper, deliveryMapper, Clock.systemUTC());
    }

    BriefMailDeliveryService(
            BriefMailProperties properties,
            ObjectProvider<BriefMailSender> senderProvider,
            BriefGenerationMapper briefGenerationMapper,
            BriefMailDeliveryMapper deliveryMapper,
            Clock clock
    ) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.senderProvider = Objects.requireNonNull(senderProvider, "senderProvider must not be null");
        this.briefGenerationMapper = Objects.requireNonNull(
                briefGenerationMapper,
                "briefGenerationMapper must not be null"
        );
        this.deliveryMapper = Objects.requireNonNull(deliveryMapper, "deliveryMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public BriefMailDeliveryResult deliver(Long briefGenerationId) {
        BriefGeneration archive = briefGenerationMapper.findById(briefGenerationId)
                .orElseThrow(() -> new BriefGenerationNotFoundException("简报归档记录不存在: " + briefGenerationId));
        if (archive.status() != BriefGenerationStatus.SUCCESS) {
            throw new BriefGenerationNotReadyException(
                    "简报归档记录不可发送: " + briefGenerationId + ", status=" + archive.status()
            );
        }

        BriefMailSender sender = requireAvailableSender();
        String subject = subject(archive);
        List<BriefMailDelivery> deliveries = new ArrayList<>();
        for (String recipient : properties.recipients()) {
            Long deliveryId = deliveryMapper.insertPending(briefGenerationId, recipient, subject);
            RuntimeException sendFailure = send(sender, recipient, subject, archive.summaryMarkdown());
            if (sendFailure == null) {
                markSent(deliveryId);
            } else {
                markFailed(deliveryId, sendFailure);
            }
            deliveries.add(findDelivery(deliveryId));
        }
        return new BriefMailDeliveryResult(briefGenerationId, deliveries);
    }

    private BriefMailSender requireAvailableSender() {
        if (!properties.enabled()) {
            throw new BriefMailUnavailableException("邮件发送能力未启用");
        }

        BriefMailSender sender = senderProvider.getIfAvailable();
        if (sender == null || !sender.isAvailable()) {
            throw new BriefMailUnavailableException("邮件发送器未配置");
        }
        return sender;
    }

    private RuntimeException send(
            BriefMailSender sender,
            String recipient,
            String subject,
            String summaryMarkdown
    ) {
        try {
            sender.send(properties.from(), recipient, subject, summaryMarkdown);
            return null;
        } catch (RuntimeException ex) {
            return ex;
        }
    }

    private void markSent(Long deliveryId) {
        int updated = deliveryMapper.markSent(deliveryId, clock.instant());
        if (updated != 1) {
            throw new IllegalStateException("邮件发送状态更新失败: " + deliveryId);
        }
    }

    private void markFailed(Long deliveryId, RuntimeException exception) {
        int updated = deliveryMapper.markFailed(deliveryId, errorSummary());
        if (updated != 1) {
            throw new IllegalStateException("邮件发送失败状态更新失败: " + deliveryId, exception);
        }
    }

    private BriefMailDelivery findDelivery(Long deliveryId) {
        return deliveryMapper.findById(deliveryId)
                .orElseThrow(() -> new IllegalStateException("邮件发送记录不存在: " + deliveryId));
    }

    private String subject(BriefGeneration archive) {
        LocalDate startDate = LocalDate.ofInstant(archive.startInclusive(), ZoneOffset.UTC);
        LocalDate endDate = LocalDate.ofInstant(archive.endExclusive(), ZoneOffset.UTC);
        return properties.subjectPrefix() + " " + startDate + " 至 " + endDate;
    }

    private String errorSummary() {
        return DEFAULT_ERROR_SUMMARY;
    }
}
