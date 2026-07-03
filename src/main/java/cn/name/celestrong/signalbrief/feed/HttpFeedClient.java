package cn.name.celestrong.signalbrief.feed;

import cn.name.celestrong.signalbrief.config.FeedProperties;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class HttpFeedClient implements FeedClient {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public InputStream fetch(FeedProperties.FeedSource source) {
        try {
            HttpRequest request = HttpRequest.newBuilder(source.url())
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new FeedFetchException("Feed source returned HTTP " + response.statusCode() + ": " + source.name(), null);
            }
            return new ByteArrayInputStream(response.body());
        } catch (FeedFetchException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new FeedFetchException("Failed to fetch feed source: " + source.name(), ex);
        }
    }
}
