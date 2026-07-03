package cn.name.celestrong.signalbrief.feed;

import cn.name.celestrong.signalbrief.config.FeedProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

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
            return new ByteArrayInputStream(feedBytes);
        } catch (FeedFetchException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new FeedFetchException("Failed to fetch feed source: " + source.name(), ex);
        }
    }
}
