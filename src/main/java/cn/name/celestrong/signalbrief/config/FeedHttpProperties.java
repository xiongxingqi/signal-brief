package cn.name.celestrong.signalbrief.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 外部 RSS / Atom 源 HTTP 访问配置。
 */
@ConfigurationProperties(prefix = "signal-brief.feed-http")
public record FeedHttpProperties(
        String userAgent,
        Duration connectTimeout,
        Duration readTimeout,
        Retry retry
) {
    private static final String DEFAULT_USER_AGENT = "signal-brief/0.0.1";
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(10);

    public FeedHttpProperties {
        userAgent = userAgent == null || userAgent.isBlank() ? DEFAULT_USER_AGENT : userAgent;
        connectTimeout = connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout;
        readTimeout = readTimeout == null ? DEFAULT_READ_TIMEOUT : readTimeout;
        retry = retry == null ? new Retry(null, null) : retry;

        rejectNegative(connectTimeout, "RSS feed HTTP connect-timeout 不能为负数");
        rejectNegative(readTimeout, "RSS feed HTTP read-timeout 不能为负数");
    }

    private static void rejectNegative(Duration duration, String message) {
        if (duration.isNegative()) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * RSS feed HTTP 重试配置。
     */
    public record Retry(
            Integer maxAttempts,
            Duration backoff
    ) {
        private static final int DEFAULT_MAX_ATTEMPTS = 2;
        private static final Duration DEFAULT_BACKOFF = Duration.ofSeconds(1);

        public Retry {
            maxAttempts = maxAttempts == null ? DEFAULT_MAX_ATTEMPTS : maxAttempts;
            backoff = backoff == null ? DEFAULT_BACKOFF : backoff;

            if (maxAttempts < 1) {
                throw new IllegalArgumentException("RSS feed HTTP retry.max-attempts 必须大于等于 1");
            }
            rejectNegative(backoff, "RSS feed HTTP retry.backoff 不能为负数");
        }
    }
}
