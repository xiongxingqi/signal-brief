package cn.name.celestrong.signalbrief.feed;

import cn.name.celestrong.signalbrief.article.ArticleCategory;
import cn.name.celestrong.signalbrief.config.FeedProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpFeedClientTest {

    @Test
    void fetchesFeedInputStreamWithRestClient() throws IOException {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpFeedClient client = new HttpFeedClient(builder);
        FeedProperties.FeedSource source = source("https://example.com/feed.xml");
        byte[] feedBytes = "<rss/>".getBytes(StandardCharsets.UTF_8);

        server.expect(requestTo(source.url()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(feedBytes, MediaType.APPLICATION_XML));

        try (InputStream inputStream = client.fetch(source)) {
            assertArrayEquals(feedBytes, inputStream.readAllBytes());
        }
        server.verify();
    }

    @Test
    void wrapsNonSuccessfulResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpFeedClient client = new HttpFeedClient(builder);
        FeedProperties.FeedSource source = source("https://example.com/feed.xml");

        server.expect(requestTo(source.url()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        FeedFetchException exception = assertThrows(FeedFetchException.class, () -> client.fetch(source));

        assertTrue(exception.getMessage().contains("HTTP 502"));
        server.verify();
    }

    private FeedProperties.FeedSource source(String url) {
        return new FeedProperties.FeedSource(
                "Example",
                URI.create(url),
                ArticleCategory.JAVA,
                true
        );
    }
}
