# RSS Source Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the first real RSS source list and make feed HTTP fetching configurable, retryable, and easier to diagnose.

**Architecture:** Keep feed sources in `signal-brief.feeds`, add a focused `signal-brief.feed-http` configuration object, and create a feed-only `RestClient` using Apache HttpClient 5. Retry stays inside `HttpFeedClient`, while `FeedIngestionService` keeps single-source failure isolation.

**Tech Stack:** Java 25, Spring Boot 4.0.7, Spring `RestClient`, Apache HttpClient 5, JUnit 5, `MockRestServiceServer`.

---

## File Map

- Create `src/main/java/cn/name/celestrong/signalbrief/config/FeedHttpProperties.java`: bind and validate feed HTTP settings.
- Create `src/main/java/cn/name/celestrong/signalbrief/config/FeedHttpConfiguration.java`: build the feed-only Apache-backed `RestClient`.
- Create `src/test/java/cn/name/celestrong/signalbrief/config/FeedHttpPropertiesTest.java`: cover defaults, binding, and invalid values.
- Create `src/main/java/cn/name/celestrong/signalbrief/feed/FeedFetchFailureType.java`: stable failure categories for logs and retry decisions.
- Modify `src/main/java/cn/name/celestrong/signalbrief/feed/FeedFetchException.java`: carry failure type, HTTP status, and attempts.
- Modify `src/main/java/cn/name/celestrong/signalbrief/feed/HttpFeedClient.java`: use injected feed `RestClient` and implement retry.
- Modify `src/main/java/cn/name/celestrong/signalbrief/ingestion/FeedIngestionService.java`: log failure context.
- Modify `src/test/java/cn/name/celestrong/signalbrief/feed/HttpFeedClientTest.java`: cover headers, retry, and non-retry behavior.
- Modify `src/main/resources/application.yaml`: add `feed-http` defaults and real source list.
- Modify `.env.example`, `README.md`, `docs/records/rss-ingestion.md`, `docs/personal-tech-newsletter-system.md`: document configuration and source strategy.

---

### Task 1: Add Feed HTTP Properties

**Files:**
- Create: `src/main/java/cn/name/celestrong/signalbrief/config/FeedHttpProperties.java`
- Create: `src/test/java/cn/name/celestrong/signalbrief/config/FeedHttpPropertiesTest.java`
- Modify: `src/main/resources/application.yaml`
- Modify: `.env.example`

- [ ] **Step 1: Write the failing properties test**

Create `src/test/java/cn/name/celestrong/signalbrief/config/FeedHttpPropertiesTest.java`:

```java
package cn.name.celestrong.signalbrief.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeedHttpPropertiesTest {

    @Test
    void defaultsFeedHttpSettings() {
        FeedHttpProperties properties = new FeedHttpProperties(null, null, null, null);

        assertEquals("signal-brief/0.0.1", properties.userAgent());
        assertEquals(Duration.ofSeconds(3), properties.connectTimeout());
        assertEquals(Duration.ofSeconds(10), properties.readTimeout());
        assertEquals(2, properties.retry().maxAttempts());
        assertEquals(Duration.ofSeconds(1), properties.retry().backoff());
    }

    @Test
    void bindsExplicitConfiguration() throws Exception {
        String yaml = """
                signal-brief:
                  feed-http:
                    user-agent: signal-brief-test
                    connect-timeout: 5s
                    read-timeout: 20s
                    retry:
                      max-attempts: 3
                      backoff: 250ms
                """;

        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.addFirst(new YamlPropertySourceLoader().load(
                "test",
                new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8))
        ).getFirst());

        FeedHttpProperties properties = Binder.get(environment)
                .bind("signal-brief.feed-http", Bindable.of(FeedHttpProperties.class))
                .orElseThrow(IllegalStateException::new);

        assertEquals("signal-brief-test", properties.userAgent());
        assertEquals(Duration.ofSeconds(5), properties.connectTimeout());
        assertEquals(Duration.ofSeconds(20), properties.readTimeout());
        assertEquals(3, properties.retry().maxAttempts());
        assertEquals(Duration.ofMillis(250), properties.retry().backoff());
    }

    @Test
    void rejectsInvalidRetryAttempts() {
        assertThrows(IllegalArgumentException.class, () ->
                new FeedHttpProperties(null, null, null, new FeedHttpProperties.Retry(0, null)));
    }

    @Test
    void rejectsNegativeTimeout() {
        assertThrows(IllegalArgumentException.class, () ->
                new FeedHttpProperties(null, Duration.ofMillis(-1), null, null));
    }
}
```

- [ ] **Step 2: Run the properties test and verify it fails**

Run:

```bash
./mvnw -Dspring.docker.compose.enabled=false -Dtest=FeedHttpPropertiesTest test
```

Expected: compilation fails because `FeedHttpProperties` does not exist.

- [ ] **Step 3: Implement `FeedHttpProperties`**

Create `src/main/java/cn/name/celestrong/signalbrief/config/FeedHttpProperties.java`:

```java
package cn.name.celestrong.signalbrief.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * RSS feed HTTP client configuration.
 *
 * <p>These settings only apply to external feed fetching and do not affect internal APIs.</p>
 */
@ConfigurationProperties(prefix = "signal-brief.feed-http")
public record FeedHttpProperties(
        String userAgent,
        Duration connectTimeout,
        Duration readTimeout,
        Retry retry
) {
    private static final String DEFAULT_USER_AGENT = "signal-brief/0.0.1";
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(10);

    public FeedHttpProperties {
        userAgent = StringUtils.hasText(userAgent) ? userAgent : DEFAULT_USER_AGENT;
        connectTimeout = defaultNonNegative(connectTimeout, DEFAULT_CONNECT_TIMEOUT, "connectTimeout");
        readTimeout = defaultNonNegative(readTimeout, DEFAULT_READ_TIMEOUT, "readTimeout");
        retry = retry == null ? new Retry(null, null) : retry;
    }

    private static Duration defaultNonNegative(Duration value, Duration defaultValue, String name) {
        Duration resolved = value == null ? defaultValue : value;
        if (resolved.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return resolved;
    }

    public record Retry(
            Integer maxAttempts,
            Duration backoff
    ) {
        private static final int DEFAULT_MAX_ATTEMPTS = 2;
        private static final Duration DEFAULT_BACKOFF = Duration.ofSeconds(1);

        public Retry {
            maxAttempts = maxAttempts == null ? DEFAULT_MAX_ATTEMPTS : maxAttempts;
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("retry.maxAttempts must be greater than 0");
            }
            backoff = defaultNonNegative(backoff, DEFAULT_BACKOFF, "retry.backoff");
        }
    }
}
```

- [ ] **Step 4: Add default YAML and environment examples**

Modify `src/main/resources/application.yaml` under `signal-brief`:

```yaml
signal-brief:
  internal-api:
    enabled: ${SIGNAL_BRIEF_INTERNAL_API_ENABLED:false}
  ingestion:
    enabled: ${SIGNAL_BRIEF_INGESTION_ENABLED:false}
    cron: "${SIGNAL_BRIEF_INGESTION_CRON:0 0 6 1,16 * *}"
  feed-http:
    user-agent: ${SIGNAL_BRIEF_FEED_HTTP_USER_AGENT:signal-brief/0.0.1}
    connect-timeout: ${SIGNAL_BRIEF_FEED_HTTP_CONNECT_TIMEOUT:3s}
    read-timeout: ${SIGNAL_BRIEF_FEED_HTTP_READ_TIMEOUT:10s}
    retry:
      max-attempts: ${SIGNAL_BRIEF_FEED_HTTP_RETRY_MAX_ATTEMPTS:2}
      backoff: ${SIGNAL_BRIEF_FEED_HTTP_RETRY_BACKOFF:1s}
  feeds: []
```

Append to `.env.example` near RSS settings:

```dotenv
# RSS feed HTTP User-Agent。访问外部 RSS 时发送，便于对方识别调用来源。
SIGNAL_BRIEF_FEED_HTTP_USER_AGENT=signal-brief/0.0.1

# RSS feed HTTP 连接建立超时和响应读取超时。
SIGNAL_BRIEF_FEED_HTTP_CONNECT_TIMEOUT=3s
SIGNAL_BRIEF_FEED_HTTP_READ_TIMEOUT=10s

# RSS feed HTTP 总尝试次数和两次尝试之间的等待时间。
SIGNAL_BRIEF_FEED_HTTP_RETRY_MAX_ATTEMPTS=2
SIGNAL_BRIEF_FEED_HTTP_RETRY_BACKOFF=1s
```

- [ ] **Step 5: Run the properties test and verify it passes**

Run:

```bash
./mvnw -Dspring.docker.compose.enabled=false -Dtest=FeedHttpPropertiesTest test
```

Expected: `FeedHttpPropertiesTest` passes.

---

### Task 2: Add a Feed-Specific Apache RestClient

**Files:**
- Create: `src/main/java/cn/name/celestrong/signalbrief/config/FeedHttpConfiguration.java`
- Modify: `src/main/java/cn/name/celestrong/signalbrief/feed/HttpFeedClient.java`
- Modify: `src/test/java/cn/name/celestrong/signalbrief/feed/HttpFeedClientTest.java`

- [ ] **Step 1: Update the HTTP client test constructor usage**

In `src/test/java/cn/name/celestrong/signalbrief/feed/HttpFeedClientTest.java`, replace direct `new HttpFeedClient(builder)` construction with a helper:

```java
private HttpFeedClient client(RestClient.Builder builder, FeedHttpProperties properties) {
    RestClient restClient = builder
            .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
            .build();
    return new HttpFeedClient(restClient, properties);
}

private FeedHttpProperties properties() {
    return new FeedHttpProperties(
            "signal-brief-test",
            Duration.ofSeconds(3),
            Duration.ofSeconds(10),
            new FeedHttpProperties.Retry(2, Duration.ZERO)
    );
}
```

Add imports:

```java
import cn.name.celestrong.signalbrief.config.FeedHttpProperties;
import org.springframework.http.HttpHeaders;

import java.time.Duration;
```

Update existing test setup:

```java
FeedHttpProperties properties = properties();
HttpFeedClient client = client(builder, properties);
```

- [ ] **Step 2: Run the HTTP client test and verify it fails**

Run:

```bash
./mvnw -Dspring.docker.compose.enabled=false -Dtest=HttpFeedClientTest test
```

Expected: compilation fails because `HttpFeedClient` does not have the new constructor.

- [ ] **Step 3: Create feed RestClient configuration**

Create `src/main/java/cn/name/celestrong/signalbrief/config/FeedHttpConfiguration.java`:

```java
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
 * RSS feed HTTP client wiring.
 *
 * <p>The bean is separate from other RestClient usage so external feed timeouts and headers stay isolated.</p>
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
```

- [ ] **Step 4: Change `HttpFeedClient` to use the feed RestClient bean**

Modify constructor and fields in `src/main/java/cn/name/celestrong/signalbrief/feed/HttpFeedClient.java`:

```java
private final RestClient restClient;
private final FeedHttpProperties properties;

public HttpFeedClient(@Qualifier("feedRestClient") RestClient restClient, FeedHttpProperties properties) {
    this.restClient = restClient;
    this.properties = properties;
}
```

Add imports:

```java
import cn.name.celestrong.signalbrief.config.FeedHttpProperties;
import org.springframework.beans.factory.annotation.Qualifier;
```

Remove `RestClient.Builder` constructor code and its inline `defaultHeader` call.

- [ ] **Step 5: Verify the constructor change**

Run:

```bash
./mvnw -Dspring.docker.compose.enabled=false -Dtest=HttpFeedClientTest test
```

Expected: existing HTTP client tests pass again.

---

### Task 3: Add Failure Context and Retry Rules

**Files:**
- Create: `src/main/java/cn/name/celestrong/signalbrief/feed/FeedFetchFailureType.java`
- Modify: `src/main/java/cn/name/celestrong/signalbrief/feed/FeedFetchException.java`
- Modify: `src/main/java/cn/name/celestrong/signalbrief/feed/HttpFeedClient.java`
- Modify: `src/main/java/cn/name/celestrong/signalbrief/ingestion/FeedIngestionService.java`
- Modify: `src/test/java/cn/name/celestrong/signalbrief/feed/HttpFeedClientTest.java`

- [ ] **Step 1: Add failing tests for headers, retryable status, retryable I/O failure, and non-retryable status**

Add to `HttpFeedClientTest`:

```java
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
```

Add imports:

```java
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
```

- [ ] **Step 2: Run the HTTP client tests and verify they fail**

Run:

```bash
./mvnw -Dspring.docker.compose.enabled=false -Dtest=HttpFeedClientTest test
```

Expected: compilation fails because `FeedFetchFailureType` and exception accessors do not exist, or retry test fails because retry is not implemented.

- [ ] **Step 3: Add failure type enum**

Create `src/main/java/cn/name/celestrong/signalbrief/feed/FeedFetchFailureType.java`:

```java
package cn.name.celestrong.signalbrief.feed;

public enum FeedFetchFailureType {
    HTTP_STATUS,
    CLIENT_IO,
    UNEXPECTED
}
```

- [ ] **Step 4: Expand `FeedFetchException`**

Replace `src/main/java/cn/name/celestrong/signalbrief/feed/FeedFetchException.java` with:

```java
package cn.name.celestrong.signalbrief.feed;

/**
 * Feed 抓取失败异常。
 *
 * <p>用于把 HTTP 状态、网络错误等底层异常统一包装到入库链路中。</p>
 */
public class FeedFetchException extends RuntimeException {

    private final FeedFetchFailureType failureType;
    private final Integer httpStatus;
    private final int attemptCount;
    private final int maxAttempts;

    public FeedFetchException(
            String message,
            FeedFetchFailureType failureType,
            Integer httpStatus,
            int attemptCount,
            int maxAttempts,
            Throwable cause
    ) {
        super(message, cause);
        this.failureType = failureType;
        this.httpStatus = httpStatus;
        this.attemptCount = attemptCount;
        this.maxAttempts = maxAttempts;
    }

    public FeedFetchFailureType failureType() {
        return failureType;
    }

    public Integer httpStatus() {
        return httpStatus;
    }

    public int attemptCount() {
        return attemptCount;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public FeedFetchException withAttempts(int attemptCount, int maxAttempts) {
        return new FeedFetchException(
                getMessage(),
                failureType,
                httpStatus,
                attemptCount,
                maxAttempts,
                getCause()
        );
    }
}
```

- [ ] **Step 5: Implement retry in `HttpFeedClient`**

Replace `fetch` and add helper methods in `src/main/java/cn/name/celestrong/signalbrief/feed/HttpFeedClient.java`:

```java
@Override
public InputStream fetch(FeedProperties.FeedSource source) {
    int maxAttempts = properties.retry().maxAttempts();
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
        try {
            return fetchOnce(source);
        } catch (FeedFetchException ex) {
            FeedFetchException attempted = ex.withAttempts(attempt, maxAttempts);
            if (!shouldRetry(attempted)) {
                throw attempted;
            }
            sleepBeforeRetry(attempted);
        } catch (ResourceAccessException ex) {
            FeedFetchException attempted = new FeedFetchException(
                    "Failed to access feed source: " + source.name(),
                    FeedFetchFailureType.CLIENT_IO,
                    null,
                    attempt,
                    maxAttempts,
                    ex
            );
            if (!shouldRetry(attempted)) {
                throw attempted;
            }
            sleepBeforeRetry(attempted);
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
    throw new IllegalStateException("Unreachable feed fetch retry state");
}

private InputStream fetchOnce(FeedProperties.FeedSource source) {
    byte[] feedBytes = restClient.get()
            .uri(source.url())
            .accept(MediaType.APPLICATION_XML, MediaType.TEXT_XML, MediaType.APPLICATION_ATOM_XML, MediaType.ALL)
            .exchange((request, response) -> {
                if (!response.getStatusCode().is2xxSuccessful()) {
                    int status = response.getStatusCode().value();
                    throw new FeedFetchException(
                            "Feed source returned HTTP " + status + ": " + source.name(),
                            FeedFetchFailureType.HTTP_STATUS,
                            status,
                            1,
                            1,
                            response.createException()
                    );
                }
                return StreamUtils.copyToByteArray(response.getBody());
            });
    // exchange 回调结束后响应体会关闭，这里转为可由解析器继续读取的内存流。
    return new ByteArrayInputStream(feedBytes);
}

private boolean shouldRetry(FeedFetchException exception) {
    if (exception.attemptCount() >= exception.maxAttempts()) {
        return false;
    }
    if (exception.failureType() == FeedFetchFailureType.CLIENT_IO) {
        return true;
    }
    Integer httpStatus = exception.httpStatus();
    return exception.failureType() == FeedFetchFailureType.HTTP_STATUS
            && httpStatus != null
            && (httpStatus == 429 || httpStatus >= 500);
}

private void sleepBeforeRetry(FeedFetchException exception) {
    if (properties.retry().backoff().isZero()) {
        return;
    }
    try {
        Thread.sleep(properties.retry().backoff().toMillis());
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new FeedFetchException(
                "Interrupted before retrying feed source",
                exception.failureType(),
                exception.httpStatus(),
                exception.attemptCount(),
                exception.maxAttempts(),
                ex
        );
    }
}
```

Add import:

```java
import org.springframework.web.client.ResourceAccessException;
```

- [ ] **Step 6: Improve ingestion failure logging**

Modify the catch block in `FeedIngestionService.ingestSource`:

```java
} catch (FeedFetchException ex) {
    log.warn(
            "Failed to fetch feed source name={}, url={}, failureType={}, httpStatus={}, attempts={}/{}",
            source.name(),
            source.url(),
            ex.failureType(),
            ex.httpStatus(),
            ex.attemptCount(),
            ex.maxAttempts(),
            ex
    );
    return new FeedIngestionResult(1, 0, 0, 0, 1);
} catch (Exception ex) {
    log.warn("Failed to ingest feed source name={}, url={}", source.name(), source.url(), ex);
    return new FeedIngestionResult(1, 0, 0, 0, 1);
}
```

Add import:

```java
import cn.name.celestrong.signalbrief.feed.FeedFetchException;
```

- [ ] **Step 7: Run HTTP and ingestion tests**

Run:

```bash
./mvnw -Dspring.docker.compose.enabled=false -Dtest=HttpFeedClientTest,FeedIngestionServiceTest test
```

Expected: both test classes pass.

---

### Task 4: Add Real Sources and Documentation

**Files:**
- Modify: `src/main/resources/application.yaml`
- Modify: `README.md`
- Modify: `docs/records/rss-ingestion.md`
- Modify: `docs/personal-tech-newsletter-system.md`

- [ ] **Step 1: Add real feed sources to common configuration**

Replace `feeds: []` in `src/main/resources/application.yaml` with:

```yaml
  feeds:
    - name: Spring Blog
      url: https://spring.io/blog.atom
      category: FRAMEWORK
      enabled: true
    - name: Inside Java
      url: https://inside.java/feed.xml
      category: JAVA
      enabled: true
    - name: Kubernetes Blog
      url: https://kubernetes.io/feed.xml
      category: FRAMEWORK
      enabled: true
    - name: OpenAI News
      url: https://openai.com/news/rss.xml
      category: AI
      enabled: true
```

Before committing implementation, verify these URLs from official pages or live responses. Do not add a third-party RSS proxy URL.

- [ ] **Step 2: Update README configuration docs**

In `README.md`, update “常用环境变量” with:

```markdown
- `SIGNAL_BRIEF_FEED_HTTP_USER_AGENT`：RSS 抓取请求的 User-Agent，默认 `signal-brief/0.0.1`。
- `SIGNAL_BRIEF_FEED_HTTP_CONNECT_TIMEOUT`：RSS 抓取连接超时，默认 `3s`。
- `SIGNAL_BRIEF_FEED_HTTP_READ_TIMEOUT`：RSS 抓取读取超时，默认 `10s`。
- `SIGNAL_BRIEF_FEED_HTTP_RETRY_MAX_ATTEMPTS`：RSS 抓取总尝试次数，默认 `2`。
- `SIGNAL_BRIEF_FEED_HTTP_RETRY_BACKOFF`：RSS 抓取重试间隔，默认 `1s`。
```

Replace the single-source RSS example with a note:

```markdown
仓库默认配置包含第一批官方或一手 RSS / Atom 源，例如 Spring Blog、Inside Java、Kubernetes Blog 和 OpenAI News。RSS 源默认 `enabled=true`，但定时任务默认关闭，因此普通启动不会自动访问外部站点。只有开启定时任务或调用内部手动触发接口时才会抓取真实源。
```

- [ ] **Step 3: Update RSS record**

In `docs/records/rss-ingestion.md`, update “配置” and “维护约束” sections with:

```markdown
`signal-brief.feed-http` 控制外部 feed 抓取客户端，包含 `user-agent`、`connect-timeout`、`read-timeout` 和 `retry`。这些配置只作用于 `feedRestClient`，不影响内部 API 或后续 AI Provider HTTP 客户端。

HTTP 抓取失败按类型记录：HTTP 状态失败、客户端 I/O 失败和未预期失败。仅网络异常、超时、`429` 和 `5xx` 参与有限重试；其他 `4xx`、解析失败和入库失败不在 HTTP 层重试。
```

Update “当前暂不覆盖” to remove “重试告警” and replace it with:

```markdown
当前暂不覆盖：RSS 源数据库管理、任务运行表、失败告警、AI 摘要和邮件发送。
```

- [ ] **Step 4: Update project roadmap**

In `docs/personal-tech-newsletter-system.md`, move “补充真实 RSS 源清单和运行配置” and “增加抓取超时、重试、User-Agent” from future work into current capabilities after implementation. Keep “失败告警策略” as future work.

- [ ] **Step 5: Run focused config and feed tests**

Run:

```bash
./mvnw -Dspring.docker.compose.enabled=false -Dtest=FeedPropertiesTest,FeedHttpPropertiesTest,HttpFeedClientTest test
```

Expected: focused tests pass.

---

### Task 5: Final Verification

**Files:**
- All files modified above.

- [ ] **Step 1: Run full local basic tests**

Run:

```bash
./mvnw -Dspring.docker.compose.enabled=false test
```

Expected: all local unit and slice tests pass. Database `*IT` tests are not required locally for this change.

- [ ] **Step 2: Check formatting and whitespace**

Run:

```bash
git diff --check
```

Expected: no whitespace errors.

- [ ] **Step 3: Review changed files**

Run:

```bash
git status --short
git diff --stat
```

Expected: changes are limited to feed HTTP reliability, real source config, and docs. No Flyway migration is expected.

- [ ] **Step 4: Commit only after explicit user approval**

The repository rule requires explicit user approval before committing. If the user says “提交”, run:

```bash
git add src/main/java/cn/name/celestrong/signalbrief/config/FeedHttpProperties.java \
  src/main/java/cn/name/celestrong/signalbrief/config/FeedHttpConfiguration.java \
  src/main/java/cn/name/celestrong/signalbrief/feed/FeedFetchFailureType.java \
  src/main/java/cn/name/celestrong/signalbrief/feed/FeedFetchException.java \
  src/main/java/cn/name/celestrong/signalbrief/feed/HttpFeedClient.java \
  src/main/java/cn/name/celestrong/signalbrief/ingestion/FeedIngestionService.java \
  src/test/java/cn/name/celestrong/signalbrief/config/FeedHttpPropertiesTest.java \
  src/test/java/cn/name/celestrong/signalbrief/feed/HttpFeedClientTest.java \
  src/main/resources/application.yaml \
  .env.example \
  README.md \
  docs/records/rss-ingestion.md \
  docs/personal-tech-newsletter-system.md \
  docs/superpowers/specs/2026-07-05-rss-source-reliability-design.md \
  docs/superpowers/plans/2026-07-05-rss-source-reliability.md
git commit -m "feat(feed): 增强 RSS 抓取可靠性"
```
