package cn.name.celestrong.signalbrief.config;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * AI 摘要生成配置。
 */
@ConfigurationProperties(prefix = "signal-brief.ai-summary")
public record AiSummaryProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String model,
        Duration connectTimeout,
        Duration readTimeout,
        Double temperature,
        Integer maxOutputTokens
) {
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(30);
    private static final double DEFAULT_TEMPERATURE = 0.2;
    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 2000;

    public AiSummaryProperties {
        baseUrl = StringUtils.stripToEmpty(baseUrl);
        apiKey = StringUtils.stripToEmpty(apiKey);
        model = StringUtils.stripToEmpty(model);
        connectTimeout = connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout;
        readTimeout = readTimeout == null ? DEFAULT_READ_TIMEOUT : readTimeout;
        temperature = temperature == null ? DEFAULT_TEMPERATURE : temperature;
        maxOutputTokens = maxOutputTokens == null ? DEFAULT_MAX_OUTPUT_TOKENS : maxOutputTokens;

        rejectNegative(connectTimeout, "AI 摘要 connect-timeout 不能为负数");
        rejectNegative(readTimeout, "AI 摘要 read-timeout 不能为负数");
        if (Double.isNaN(temperature) || temperature < 0 || temperature > 2) {
            throw new IllegalArgumentException("AI 摘要 temperature 必须在 0 到 2 之间");
        }
        if (maxOutputTokens < 1) {
            throw new IllegalArgumentException("AI 摘要 max-output-tokens 必须大于等于 1");
        }
        if (enabled) {
            requireNonBlank(baseUrl, "AI 摘要 base-url 不能为空");
            requireNonBlank(apiKey, "AI 摘要 api-key 不能为空");
            requireNonBlank(model, "AI 摘要 model 不能为空");
        }
    }

    private static void rejectNegative(Duration duration, String message) {
        if (duration.isNegative()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireNonBlank(String value, String message) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
    }
}
