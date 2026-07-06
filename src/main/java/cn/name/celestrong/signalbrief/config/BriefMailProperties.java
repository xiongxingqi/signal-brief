package cn.name.celestrong.signalbrief.config;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 简报邮件发送配置。
 *
 * <p>邮件能力关闭时允许发件人和收件人为空；开启后必须显式配置，避免误发或静默丢信。</p>
 */
@ConfigurationProperties(prefix = "signal-brief.mail")
public record BriefMailProperties(
        boolean enabled,
        String from,
        List<String> recipients,
        String subjectPrefix
) {
    private static final String DEFAULT_SUBJECT_PREFIX = "SignalBrief 技术半月报";

    public BriefMailProperties {
        recipients = normalizeRecipients(recipients);
        subjectPrefix = StringUtils.defaultIfBlank(subjectPrefix, DEFAULT_SUBJECT_PREFIX);

        if (enabled && StringUtils.isBlank(from)) {
            throw new IllegalArgumentException("signal-brief.mail.from must be configured when mail is enabled");
        }
        if (enabled && recipients.isEmpty()) {
            throw new IllegalArgumentException("signal-brief.mail.recipients must be configured when mail is enabled");
        }
    }

    private static List<String> normalizeRecipients(List<String> recipients) {
        if (recipients == null) {
            return List.of();
        }
        return recipients.stream()
                .map(StringUtils::trimToEmpty)
                .filter(StringUtils::isNotBlank)
                .toList();
    }
}
