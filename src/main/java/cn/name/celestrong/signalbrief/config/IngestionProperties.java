package cn.name.celestrong.signalbrief.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RSS 入库定时任务配置。
 *
 * <p>任务默认关闭，避免本地启动、测试或 CI 意外访问外部 RSS 源。</p>
 */
@ConfigurationProperties(prefix = "signal-brief.ingestion")
public record IngestionProperties(
        boolean enabled,
        String cron
) {
    /**
     * 半月报默认节奏：每月 1 日和 16 日 06:00 执行。
     */
    private static final String DEFAULT_CRON = "0 0 6 1,16 * *";

    public IngestionProperties {
        cron = cron == null || cron.isBlank() ? DEFAULT_CRON : cron;
    }
}
