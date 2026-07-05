package cn.name.celestrong.signalbrief.feed;

import cn.name.celestrong.signalbrief.config.FeedHttpProperties;
import cn.name.celestrong.signalbrief.config.FeedProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;

/**
 * 基于 Spring RestClient 的 feed 获取实现。
 *
 * <p>HTTP 细节限制在该类内，对上层仍暴露 {@code InputStream}，避免解析层感知具体客户端。</p>
 */
@Component
public class HttpFeedClient implements FeedClient {

    private final RestClient restClient;
    private final FeedHttpProperties properties;

    public HttpFeedClient(@Qualifier("feedRestClient") RestClient restClient, FeedHttpProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public InputStream fetch(FeedProperties.FeedSource source) {
        int maxAttempts = properties.retry().maxAttempts();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return fetchOnce(source);
            } catch (FeedFetchException ex) {
                FeedFetchException attemptedException = ex.withAttempts(attempt, maxAttempts);
                if (!isRetryable(attemptedException)) {
                    throw attemptedException;
                }
                sleepBeforeRetry(source, attemptedException);
            } catch (ResourceAccessException ex) {
                FeedFetchException attemptedException = new FeedFetchException(
                        "Failed to fetch feed source due to client IO: " + source.name(),
                        FeedFetchFailureType.CLIENT_IO,
                        null,
                        attempt,
                        maxAttempts,
                        ex
                );
                if (!isRetryable(attemptedException)) {
                    throw attemptedException;
                }
                sleepBeforeRetry(source, attemptedException);
            } catch (Exception ex) {
                throw new FeedFetchException(
                        "Failed to fetch feed source: " + source.name(),
                        FeedFetchFailureType.UNEXPECTED,
                        null,
                        attempt,
                        maxAttempts,
                        ex
                );
            }
        }
        throw new FeedFetchException(
                "Failed to fetch feed source: " + source.name(),
                FeedFetchFailureType.UNEXPECTED,
                null,
                maxAttempts,
                maxAttempts,
                null
        );
    }

    private InputStream fetchOnce(FeedProperties.FeedSource source) {
        byte[] feedBytes = restClient.get()
                .uri(source.url())
                .accept(MediaType.APPLICATION_XML, MediaType.TEXT_XML, MediaType.APPLICATION_ATOM_XML, MediaType.ALL)
                .exchange((request, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        int httpStatus = response.getStatusCode().value();
                        throw new FeedFetchException(
                                "Feed source returned HTTP " + httpStatus + ": " + source.name(),
                                FeedFetchFailureType.HTTP_STATUS,
                                httpStatus,
                                0,
                                0,
                                response.createException()
                        );
                    }
                    return StreamUtils.copyToByteArray(response.getBody());
                });
        // exchange 回调结束后响应体会关闭，这里转为可由解析器继续读取的内存流。
        return new ByteArrayInputStream(feedBytes);
    }

    private boolean isRetryable(FeedFetchException ex) {
        if (ex.attemptCount() >= ex.maxAttempts()) {
            return false;
        }
        if (ex.failureType() == FeedFetchFailureType.CLIENT_IO) {
            return true;
        }
        return ex.failureType() == FeedFetchFailureType.HTTP_STATUS
                && ex.httpStatus() != null
                && (ex.httpStatus() == 429 || ex.httpStatus() >= 500);
    }

    private void sleepBeforeRetry(FeedProperties.FeedSource source, FeedFetchException previousFailure) {
        Duration backoff = properties.retry().backoff();
        if (backoff.isZero()) {
            return;
        }
        try {
            Thread.sleep(backoff);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            FeedFetchException interrupted = new FeedFetchException(
                    "Interrupted before retrying feed source: " + source.name(),
                    previousFailure.failureType(),
                    previousFailure.httpStatus(),
                    previousFailure.attemptCount(),
                    previousFailure.maxAttempts(),
                    ex
            );
            interrupted.addSuppressed(previousFailure);
            throw interrupted;
        }
    }
}
