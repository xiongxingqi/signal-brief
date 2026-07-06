package cn.name.celestrong.signalbrief.config;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * AI Provider 专用 HTTP 客户端装配，避免和其他 RestClient 用法混用。
 */
@Configuration(proxyBeanMethods = false)
public class AiSummaryConfiguration {

    @Bean
    HttpComponentsClientHttpRequestFactory aiSummaryClientHttpRequestFactory(AiSummaryProperties properties) {
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
    RestClient aiSummaryRestClient(
            RestClient.Builder restClientBuilder,
            @Qualifier("aiSummaryClientHttpRequestFactory")
            HttpComponentsClientHttpRequestFactory aiSummaryClientHttpRequestFactory,
            AiSummaryProperties properties
    ) {
        RestClient.Builder builder = restClientBuilder.clone()
                .requestFactory(aiSummaryClientHttpRequestFactory);
        if (StringUtils.isNotBlank(properties.baseUrl())) {
            builder.baseUrl(StringUtils.stripEnd(properties.baseUrl(), "/"));
        }
        if (StringUtils.isNotBlank(properties.apiKey())) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey());
        }
        return builder.build();
    }
}
