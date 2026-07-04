package cn.name.celestrong.signalbrief.feed;

import cn.name.celestrong.signalbrief.config.FeedProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * 基于 Spring RestClient 的 feed 获取实现。
 *
 * <p>HTTP 细节限制在该类内，对上层仍暴露 {@code InputStream}，避免解析层感知具体客户端。</p>
 */
@Component
public class HttpFeedClient implements FeedClient {

    private final RestClient restClient;

    public HttpFeedClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .defaultHeader(HttpHeaders.USER_AGENT, "signal-brief")
                .build();
    }

    @Override
    public InputStream fetch(FeedProperties.FeedSource source) {
        try {
            byte[] feedBytes = restClient.get()
                    .uri(source.url())
                    .accept(MediaType.APPLICATION_XML, MediaType.TEXT_XML, MediaType.APPLICATION_ATOM_XML, MediaType.ALL)
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new FeedFetchException(
                                    "Feed source returned HTTP " + response.getStatusCode().value() + ": " + source.name(),
                                    response.createException()
                            );
                        }
                        return StreamUtils.copyToByteArray(response.getBody());
                    });
            // exchange 回调结束后响应体会关闭，这里转为可由解析器继续读取的内存流。
            return new ByteArrayInputStream(feedBytes);
        } catch (FeedFetchException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new FeedFetchException("Failed to fetch feed source: " + source.name(), ex);
        }
    }
}
