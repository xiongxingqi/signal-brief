package cn.name.celestrong.signalbrief.feed;

import cn.name.celestrong.signalbrief.article.ArticleCategory;
import cn.name.celestrong.signalbrief.config.FeedHttpProperties;
import cn.name.celestrong.signalbrief.config.FeedProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 使用 MockRestServiceServer 验证 RestClient 边界，避免测试访问真实外部 RSS 源。
 */
class HttpFeedClientTest {

    @Test
    void fetchesFeedInputStreamWithRestClient() throws IOException {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FeedHttpProperties properties = properties();
        HttpFeedClient client = client(builder, properties);
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
    void sendsConfiguredUserAgent() throws IOException {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FeedHttpProperties properties = properties();
        HttpFeedClient client = client(builder, properties);
        FeedProperties.FeedSource source = source("https://example.com/feed.xml");
        byte[] feedBytes = "<rss/>".getBytes(StandardCharsets.UTF_8);

        server.expect(requestTo(source.url()))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.USER_AGENT, "signal-brief-test"))
                .andRespond(withSuccess(feedBytes, MediaType.APPLICATION_XML));

        try (InputStream inputStream = client.fetch(source)) {
            assertArrayEquals(feedBytes, inputStream.readAllBytes());
        }
        server.verify();
    }

    @Test
    void retriesTemporaryServerFailure() throws IOException {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FeedHttpProperties properties = properties();
        HttpFeedClient client = client(builder, properties);
        FeedProperties.FeedSource source = source("https://example.com/feed.xml");
        byte[] feedBytes = "<rss/>".getBytes(StandardCharsets.UTF_8);

        server.expect(requestTo(source.url()))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        server.expect(requestTo(source.url()))
                .andRespond(withSuccess(feedBytes, MediaType.APPLICATION_XML));

        try (InputStream inputStream = client.fetch(source)) {
            assertArrayEquals(feedBytes, inputStream.readAllBytes());
        }
        server.verify();
    }

    @Test
    void retriesTooManyRequests() throws IOException {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FeedHttpProperties properties = properties();
        HttpFeedClient client = client(builder, properties);
        FeedProperties.FeedSource source = source("https://example.com/feed.xml");
        byte[] feedBytes = "<rss/>".getBytes(StandardCharsets.UTF_8);

        server.expect(requestTo(source.url()))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(source.url()))
                .andRespond(withSuccess(feedBytes, MediaType.APPLICATION_XML));

        try (InputStream inputStream = client.fetch(source)) {
            assertArrayEquals(feedBytes, inputStream.readAllBytes());
        }
        server.verify();
    }

    @Test
    void retriesTemporaryIoFailure() throws IOException {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FeedHttpProperties properties = properties();
        HttpFeedClient client = client(builder, properties);
        FeedProperties.FeedSource source = source("https://example.com/feed.xml");
        byte[] feedBytes = "<rss/>".getBytes(StandardCharsets.UTF_8);

        server.expect(requestTo(source.url()))
                .andRespond(withException(new SocketTimeoutException("timed out")));
        server.expect(requestTo(source.url()))
                .andRespond(withSuccess(feedBytes, MediaType.APPLICATION_XML));

        try (InputStream inputStream = client.fetch(source)) {
            assertArrayEquals(feedBytes, inputStream.readAllBytes());
        }
        server.verify();
    }

    @Test
    void stopsAfterMaxAttemptsForTemporaryServerFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FeedHttpProperties properties = properties();
        HttpFeedClient client = client(builder, properties);
        FeedProperties.FeedSource source = source("https://example.com/feed.xml");

        server.expect(requestTo(source.url()))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        server.expect(requestTo(source.url()))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        FeedFetchException exception = assertThrows(FeedFetchException.class, () -> client.fetch(source));

        assertEquals(FeedFetchFailureType.HTTP_STATUS, exception.failureType());
        assertEquals(502, exception.httpStatus());
        assertEquals(2, exception.attemptCount());
        assertEquals(2, exception.maxAttempts());
        assertNotNull(exception.getCause());
        server.verify();
    }

    @Test
    void stopsAfterMaxAttemptsForTemporaryIoFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FeedHttpProperties properties = properties();
        HttpFeedClient client = client(builder, properties);
        FeedProperties.FeedSource source = source("https://example.com/feed.xml");

        server.expect(requestTo(source.url()))
                .andRespond(withException(new SocketTimeoutException("timed out")));
        server.expect(requestTo(source.url()))
                .andRespond(withException(new SocketTimeoutException("timed out")));

        FeedFetchException exception = assertThrows(FeedFetchException.class, () -> client.fetch(source));

        assertEquals(FeedFetchFailureType.CLIENT_IO, exception.failureType());
        assertNull(exception.httpStatus());
        assertEquals(2, exception.attemptCount());
        assertEquals(2, exception.maxAttempts());
        assertNotNull(exception.getCause());
        server.verify();
    }

    @Test
    void preservesPreviousFailureWhenRetryBackoffIsInterrupted() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FeedHttpProperties properties = properties(Duration.ofMillis(1));
        HttpFeedClient client = client(builder, properties);
        FeedProperties.FeedSource source = source("https://example.com/feed.xml");

        server.expect(requestTo(source.url()))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        Thread.currentThread().interrupt();
        try {
            FeedFetchException exception = assertThrows(FeedFetchException.class, () -> client.fetch(source));

            assertEquals(FeedFetchFailureType.HTTP_STATUS, exception.failureType());
            assertEquals(502, exception.httpStatus());
            assertEquals(1, exception.attemptCount());
            assertEquals(2, exception.maxAttempts());
            assertNotNull(exception.getCause());
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, exception.getSuppressed().length);
            FeedFetchException previousFailure = assertInstanceOf(
                    FeedFetchException.class,
                    exception.getSuppressed()[0]
            );
            assertEquals(FeedFetchFailureType.HTTP_STATUS, previousFailure.failureType());
            assertEquals(502, previousFailure.httpStatus());
            assertNotNull(previousFailure.getCause());
        } finally {
            Thread.interrupted();
        }
        server.verify();
    }

    @Test
    void doesNotRetryNonRetryableClientFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FeedHttpProperties properties = properties();
        HttpFeedClient client = client(builder, properties);
        FeedProperties.FeedSource source = source("https://example.com/feed.xml");

        server.expect(requestTo(source.url()))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        FeedFetchException exception = assertThrows(FeedFetchException.class, () -> client.fetch(source));

        assertEquals(FeedFetchFailureType.HTTP_STATUS, exception.failureType());
        assertEquals(404, exception.httpStatus());
        assertEquals(1, exception.attemptCount());
        assertEquals(2, exception.maxAttempts());
        server.verify();
    }

    @Test
    void wrapsNonSuccessfulResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FeedHttpProperties properties = properties();
        HttpFeedClient client = client(builder, properties);
        FeedProperties.FeedSource source = source("https://example.com/feed.xml");

        server.expect(requestTo(source.url()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        FeedFetchException exception = assertThrows(FeedFetchException.class, () -> client.fetch(source));

        assertTrue(exception.getMessage().contains("HTTP 404"));
        server.verify();
    }

    private HttpFeedClient client(RestClient.Builder builder, FeedHttpProperties properties) {
        RestClient restClient = builder
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .build();
        return new HttpFeedClient(restClient, properties);
    }

    private FeedHttpProperties properties() {
        return properties(Duration.ZERO);
    }

    private FeedHttpProperties properties(Duration backoff) {
        return new FeedHttpProperties(
                "signal-brief-test",
                Duration.ofSeconds(3),
                Duration.ofSeconds(10),
                new FeedHttpProperties.Retry(2, backoff)
        );
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
