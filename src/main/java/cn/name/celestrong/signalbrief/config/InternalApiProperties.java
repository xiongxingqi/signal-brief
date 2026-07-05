package cn.name.celestrong.signalbrief.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 内部手动触发 API 配置。
 *
 * <p>默认关闭，避免本地或生产环境误暴露运维入口。</p>
 */
@ConfigurationProperties(prefix = "signal-brief.internal-api")
public record InternalApiProperties(boolean enabled) {
}
