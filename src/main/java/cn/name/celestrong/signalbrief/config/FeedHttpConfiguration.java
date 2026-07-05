package cn.name.celestrong.signalbrief.config;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * RSS feed HTTP 客户端装配。
 *
 * <p>该 bean 独立于其他 RestClient 用法，避免外部 feed 的超时和请求头设置影响其他调用。</p>
 */
@Configuration(proxyBeanMethods = false)
public class FeedHttpConfiguration {

    @Bean
    HttpComponentsClientHttpRequestFactory feedClientHttpRequestFactory(FeedHttpProperties properties) {
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.of(properties.connectTimeout()))
                .build();
        RequestConfig requestConfig = RequestConfig.custom()
                .setResponseTimeout(Timeout.of(properties.readTimeout()))
                .build();
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(connectionConfig)
                        .build())
                .setDefaultRequestConfig(requestConfig)
                .disableAutomaticRetries()
                .build();
        return new HttpComponentsClientHttpRequestFactory(httpClient);
    }

    @Bean
    RestClient feedRestClient(
            RestClient.Builder restClientBuilder,
            HttpComponentsClientHttpRequestFactory feedClientHttpRequestFactory,
            FeedHttpProperties properties
    ) {
        return restClientBuilder
                .requestFactory(feedClientHttpRequestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .build();
    }
}
