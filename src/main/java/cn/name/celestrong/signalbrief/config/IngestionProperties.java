package cn.name.celestrong.signalbrief.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "signal-brief.ingestion")
public record IngestionProperties(
        boolean enabled,
        String cron
) {
    private static final String DEFAULT_CRON = "0 0 6 1,16 * *";

    public IngestionProperties {
        cron = cron == null || cron.isBlank() ? DEFAULT_CRON : cron;
    }
}
