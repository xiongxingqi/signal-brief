# Manual Trigger API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增默认关闭的内部 REST API 和 OpenAPI 文档，用于手动触发 RSS 入库、生成指定时间窗口的 Markdown 简报草稿，并方便本地管理接口。

**Architecture:** 在 `internal` 包增加薄 Controller、请求响应对象、异常处理器和 OpenAPI 配置，只做 HTTP 协议与文档适配。业务仍复用 `FeedIngestionService.ingestEnabledFeeds()` 与 `BriefGenerationService.generate(...)`，内部 API 通过 `signal-brief.internal-api.enabled` 控制注册，OpenAPI 通过 `SIGNAL_BRIEF_OPENAPI_ENABLED` 默认关闭。

**Tech Stack:** Java 25, Spring Boot 4 WebMVC, springdoc-openapi 3.0.3, JUnit 5, MockMvc, Maven Wrapper.

---

## 执行约束

本项目代理协作时不要主动提交代码。下面的检查点只表示完成一个可审查阶段；只有用户明确要求“提交”时，才执行 `git add` 和 `git commit`。

## 文件结构

- Modify: `pom.xml`
  引入 `springdoc-openapi-starter-webmvc-ui`，版本用 `springdoc-openapi.version` 属性集中管理。
- Create: `src/main/java/cn/name/celestrong/signalbrief/config/InternalApiProperties.java`
  绑定 `signal-brief.internal-api` 配置。
- Modify: `src/main/resources/application.yaml`
  增加内部 API 与 OpenAPI 默认关闭配置。
- Modify: `.env.example`
  增加本地启用内部 API 和 OpenAPI 的示例环境变量。
- Create: `src/test/java/cn/name/celestrong/signalbrief/config/InternalApiPropertiesTest.java`
  覆盖默认值和 YAML 绑定。
- Create: `src/main/java/cn/name/celestrong/signalbrief/internal/ManualTriggerController.java`
  暴露 RSS 入库和 Markdown 简报生成接口。
- Create: `src/main/java/cn/name/celestrong/signalbrief/internal/MarkdownBriefRequest.java`
  接收 Markdown 简报时间窗口。
- Create: `src/main/java/cn/name/celestrong/signalbrief/internal/MarkdownBriefResponse.java`
  返回时间窗口和 Markdown 文本。
- Create: `src/main/java/cn/name/celestrong/signalbrief/internal/InternalApiErrorResponse.java`
  返回内部 API 的简短错误说明。
- Create: `src/main/java/cn/name/celestrong/signalbrief/internal/ManualTriggerExceptionHandler.java`
  将请求解析、窗口校验和未预期异常映射成 HTTP 响应。
- Create: `src/main/java/cn/name/celestrong/signalbrief/internal/OpenApiConfiguration.java`
  配置 OpenAPI 元信息和 internal 分组。
- Create: `src/test/java/cn/name/celestrong/signalbrief/internal/ManualTriggerControllerTest.java`
  使用 `@WebMvcTest` 和 fake service 覆盖接口行为。
- Create: `src/test/java/cn/name/celestrong/signalbrief/internal/ManualTriggerControllerConditionTest.java`
  使用 `ApplicationContextRunner` 覆盖配置关闭时 Controller 不注册。
- Create: `src/test/java/cn/name/celestrong/signalbrief/internal/OpenApiConfigurationTest.java`
  覆盖 OpenAPI 配置默认关闭和开启后的 bean。
- Modify: `README.md`
  增加内部 API 启用方式和 curl 示例。
- Modify: `docs/personal-tech-newsletter-system.md`
  同步当前阶段和后续路线。
- Modify: `docs/records/rss-ingestion.md`
  记录手动触发入口与定时开关边界。
- Modify: `docs/records/markdown-brief-generation.md`
  记录 Markdown 草稿可通过内部 API 生成。

### Task 1: 配置开关

**Files:**
- Create: `src/main/java/cn/name/celestrong/signalbrief/config/InternalApiProperties.java`
- Modify: `src/main/resources/application.yaml`
- Modify: `.env.example`
- Test: `src/test/java/cn/name/celestrong/signalbrief/config/InternalApiPropertiesTest.java`

- [ ] **Step 1: 写配置绑定测试**

Create `src/test/java/cn/name/celestrong/signalbrief/config/InternalApiPropertiesTest.java`:

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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalApiPropertiesTest {

    @Test
    void defaultsDisableInternalApi() {
        InternalApiProperties properties = new InternalApiProperties(false);

        assertFalse(properties.enabled());
    }

    @Test
    void bindsExplicitConfiguration() throws Exception {
        String yaml = """
                signal-brief:
                  internal-api:
                    enabled: true
                """;

        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.addFirst(new YamlPropertySourceLoader().load(
                "test",
                new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8))
        ).getFirst());

        InternalApiProperties properties = Binder.get(environment)
                .bind("signal-brief.internal-api", Bindable.of(InternalApiProperties.class))
                .orElseThrow(IllegalStateException::new);

        assertTrue(properties.enabled());
    }
}
```

- [ ] **Step 2: 运行新测试确认失败**

Run:

```bash
./mvnw -o -Dspring.docker.compose.enabled=false -Dtest=InternalApiPropertiesTest test
```

Expected: 编译失败，提示 `InternalApiProperties` 不存在。

- [ ] **Step 3: 增加配置 record**

Create `src/main/java/cn/name/celestrong/signalbrief/config/InternalApiProperties.java`:

```java
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
```

- [ ] **Step 4: 增加默认配置和环境变量示例**

Modify `src/main/resources/application.yaml`:

```yaml
springdoc:
  api-docs:
    enabled: ${SIGNAL_BRIEF_OPENAPI_ENABLED:false}
    path: /internal/api-docs
  swagger-ui:
    enabled: ${SIGNAL_BRIEF_OPENAPI_ENABLED:false}
    path: /internal/swagger-ui.html
    urls:
      - name: internal
        url: /internal/api-docs/internal
    urls-primary-name: internal

signal-brief:
  internal-api:
    enabled: ${SIGNAL_BRIEF_INTERNAL_API_ENABLED:false}
  ingestion:
    enabled: ${SIGNAL_BRIEF_INGESTION_ENABLED:false}
    cron: "${SIGNAL_BRIEF_INGESTION_CRON:0 0 6 1,16 * *}"
  feeds: []
```

Modify `.env.example` under APP section:

```dotenv
SIGNAL_BRIEF_INTERNAL_API_ENABLED=false
SIGNAL_BRIEF_OPENAPI_ENABLED=false
```

- [ ] **Step 5: 运行配置测试确认通过**

Run:

```bash
./mvnw -o -Dspring.docker.compose.enabled=false -Dtest=InternalApiPropertiesTest test
```

Expected: `BUILD SUCCESS`，`InternalApiPropertiesTest` 2 个测试通过。

### Task 2: 内部 REST API 成功路径

**Files:**
- Create: `src/main/java/cn/name/celestrong/signalbrief/internal/ManualTriggerController.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/internal/MarkdownBriefRequest.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/internal/MarkdownBriefResponse.java`
- Test: `src/test/java/cn/name/celestrong/signalbrief/internal/ManualTriggerControllerTest.java`

- [ ] **Step 1: 写 Web 层成功路径测试**

Create `src/test/java/cn/name/celestrong/signalbrief/internal/ManualTriggerControllerTest.java`:

```java
package cn.name.celestrong.signalbrief.internal;

import cn.name.celestrong.signalbrief.brief.BriefGenerationService;
import cn.name.celestrong.signalbrief.ingestion.FeedIngestionResult;
import cn.name.celestrong.signalbrief.ingestion.FeedIngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ManualTriggerController.class)
@Import(ManualTriggerControllerTest.InternalApiTestConfiguration.class)
@TestPropertySource(properties = "signal-brief.internal-api.enabled=true")
class ManualTriggerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingFeedIngestionService feedIngestionService;

    @Autowired
    private RecordingBriefGenerationService briefGenerationService;

    @Test
    void triggersRssIngestion() throws Exception {
        mockMvc.perform(post("/internal/ingestions/rss"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceCount").value(2))
                .andExpect(jsonPath("$.fetchedCount").value(8))
                .andExpect(jsonPath("$.insertedCount").value(3))
                .andExpect(jsonPath("$.skippedCount").value(5))
                .andExpect(jsonPath("$.failedSourceCount").value(1));

        assertEquals(1, feedIngestionService.calls);
    }

    @Test
    void generatesMarkdownBriefForRequestedWindow() throws Exception {
        mockMvc.perform(post("/internal/briefs/markdown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startInclusive": "2026-07-01T00:00:00Z",
                                  "endExclusive": "2026-07-16T00:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startInclusive").value("2026-07-01T00:00:00Z"))
                .andExpect(jsonPath("$.endExclusive").value("2026-07-16T00:00:00Z"))
                .andExpect(jsonPath("$.markdown").value("# SignalBrief 技术半月报\n"));

        assertEquals(1, briefGenerationService.calls);
        assertEquals(Instant.parse("2026-07-01T00:00:00Z"), briefGenerationService.startInclusive);
        assertEquals(Instant.parse("2026-07-16T00:00:00Z"), briefGenerationService.endExclusive);
    }

    @TestConfiguration
    static class InternalApiTestConfiguration {

        @Bean
        RecordingFeedIngestionService feedIngestionService() {
            return new RecordingFeedIngestionService();
        }

        @Bean
        RecordingBriefGenerationService briefGenerationService() {
            return new RecordingBriefGenerationService();
        }
    }

    static class RecordingFeedIngestionService extends FeedIngestionService {

        private int calls;

        RecordingFeedIngestionService() {
            super(null, null, null, null);
        }

        @Override
        public FeedIngestionResult ingestEnabledFeeds() {
            calls++;
            return new FeedIngestionResult(2, 8, 3, 5, 1);
        }
    }

    static class RecordingBriefGenerationService extends BriefGenerationService {

        private int calls;
        private Instant startInclusive;
        private Instant endExclusive;

        RecordingBriefGenerationService() {
            super(null, null);
        }

        @Override
        public String generate(Instant startInclusive, Instant endExclusive) {
            calls++;
            this.startInclusive = startInclusive;
            this.endExclusive = endExclusive;
            return "# SignalBrief 技术半月报\n";
        }
    }
}
```

- [ ] **Step 2: 运行成功路径测试确认失败**

Run:

```bash
./mvnw -o -Dspring.docker.compose.enabled=false -Dtest=ManualTriggerControllerTest test
```

Expected: 编译失败，提示 `ManualTriggerController`、`MarkdownBriefRequest` 或 `MarkdownBriefResponse` 不存在。

- [ ] **Step 3: 增加请求和响应对象**

Create `src/main/java/cn/name/celestrong/signalbrief/internal/MarkdownBriefRequest.java`:

```java
package cn.name.celestrong.signalbrief.internal;

import java.time.Instant;

public record MarkdownBriefRequest(
        Instant startInclusive,
        Instant endExclusive
) {
}
```

Create `src/main/java/cn/name/celestrong/signalbrief/internal/MarkdownBriefResponse.java`:

```java
package cn.name.celestrong.signalbrief.internal;

import java.time.Instant;

public record MarkdownBriefResponse(
        Instant startInclusive,
        Instant endExclusive,
        String markdown
) {
}
```

- [ ] **Step 4: 增加 Controller**

Create `src/main/java/cn/name/celestrong/signalbrief/internal/ManualTriggerController.java`:

```java
package cn.name.celestrong.signalbrief.internal;

import cn.name.celestrong.signalbrief.brief.BriefGenerationService;
import cn.name.celestrong.signalbrief.ingestion.FeedIngestionResult;
import cn.name.celestrong.signalbrief.ingestion.FeedIngestionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部手动触发入口。
 *
 * <p>该入口只做 HTTP 协议适配，具体入库和简报生成逻辑继续委托应用服务。</p>
 */
@RestController
@RequestMapping("/internal")
@ConditionalOnProperty(prefix = "signal-brief.internal-api", name = "enabled", havingValue = "true")
public class ManualTriggerController {

    private final FeedIngestionService feedIngestionService;
    private final BriefGenerationService briefGenerationService;

    public ManualTriggerController(
            FeedIngestionService feedIngestionService,
            BriefGenerationService briefGenerationService
    ) {
        this.feedIngestionService = feedIngestionService;
        this.briefGenerationService = briefGenerationService;
    }

    @PostMapping("/ingestions/rss")
    public FeedIngestionResult triggerRssIngestion() {
        return feedIngestionService.ingestEnabledFeeds();
    }

    @PostMapping("/briefs/markdown")
    public MarkdownBriefResponse generateMarkdownBrief(@RequestBody MarkdownBriefRequest request) {
        if (request == null || request.startInclusive() == null || request.endExclusive() == null) {
            throw new IllegalArgumentException("startInclusive 和 endExclusive 必须提供");
        }

        String markdown = briefGenerationService.generate(request.startInclusive(), request.endExclusive());
        return new MarkdownBriefResponse(request.startInclusive(), request.endExclusive(), markdown);
    }
}
```

- [ ] **Step 5: 运行成功路径测试确认通过**

Run:

```bash
./mvnw -o -Dspring.docker.compose.enabled=false -Dtest=ManualTriggerControllerTest test
```

Expected: `BUILD SUCCESS`，`ManualTriggerControllerTest` 中 2 个成功路径测试通过。

### Task 3: 错误响应与配置关闭行为

**Files:**
- Create: `src/main/java/cn/name/celestrong/signalbrief/internal/InternalApiErrorResponse.java`
- Create: `src/main/java/cn/name/celestrong/signalbrief/internal/ManualTriggerExceptionHandler.java`
- Modify: `src/test/java/cn/name/celestrong/signalbrief/internal/ManualTriggerControllerTest.java`
- Create: `src/test/java/cn/name/celestrong/signalbrief/internal/ManualTriggerControllerConditionTest.java`

- [ ] **Step 1: 扩展 Web 层错误测试**

Modify `src/test/java/cn/name/celestrong/signalbrief/internal/ManualTriggerControllerTest.java`:

```java
@WebMvcTest(ManualTriggerController.class)
@Import({
        ManualTriggerExceptionHandler.class,
        ManualTriggerControllerTest.InternalApiTestConfiguration.class
})
@TestPropertySource(properties = "signal-brief.internal-api.enabled=true")
class ManualTriggerControllerTest {
```

Add these tests inside the class:

```java
    @Test
    void rejectsMissingMarkdownWindowField() throws Exception {
        mockMvc.perform(post("/internal/briefs/markdown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startInclusive": "2026-07-01T00:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("startInclusive 和 endExclusive 必须提供"));
    }

    @Test
    void rejectsInvalidMarkdownWindow() throws Exception {
        mockMvc.perform(post("/internal/briefs/markdown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startInclusive": "2026-07-16T00:00:00Z",
                                  "endExclusive": "2026-07-01T00:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Brief candidate start time must be before end time"));
    }

    @Test
    void rejectsMalformedMarkdownWindow() throws Exception {
        mockMvc.perform(post("/internal/briefs/markdown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startInclusive": "not-an-instant",
                                  "endExclusive": "2026-07-16T00:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请求体格式不正确"));
    }

    @Test
    void mapsUnexpectedExceptionToServerError() throws Exception {
        briefGenerationService.failUnexpectedly = true;

        mockMvc.perform(post("/internal/briefs/markdown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startInclusive": "2026-07-01T00:00:00Z",
                                  "endExclusive": "2026-07-16T00:00:00Z"
                                }
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("内部接口执行失败"));
    }
```

Modify `RecordingBriefGenerationService`:

```java
    static class RecordingBriefGenerationService extends BriefGenerationService {

        private int calls;
        private Instant startInclusive;
        private Instant endExclusive;
        private boolean failUnexpectedly;

        RecordingBriefGenerationService() {
            super(null, null);
        }

        @Override
        public String generate(Instant startInclusive, Instant endExclusive) {
            if (failUnexpectedly) {
                throw new IllegalStateException("boom");
            }
            if (!startInclusive.isBefore(endExclusive)) {
                throw new IllegalArgumentException("Brief candidate start time must be before end time");
            }
            calls++;
            this.startInclusive = startInclusive;
            this.endExclusive = endExclusive;
            return "# SignalBrief 技术半月报\n";
        }
    }
```

- [ ] **Step 2: 写配置关闭测试**

Create `src/test/java/cn/name/celestrong/signalbrief/internal/ManualTriggerControllerConditionTest.java`:

```java
package cn.name.celestrong.signalbrief.internal;

import cn.name.celestrong.signalbrief.brief.BriefGenerationService;
import cn.name.celestrong.signalbrief.ingestion.FeedIngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualTriggerControllerConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ManualTriggerController.class, TestConfiguration.class);

    @Test
    void doesNotRegisterControllerWhenPropertyIsMissing() {
        contextRunner.run(context -> assertFalse(context.containsBean("manualTriggerController")));
    }

    @Test
    void doesNotRegisterControllerWhenDisabled() {
        contextRunner
                .withPropertyValues("signal-brief.internal-api.enabled=false")
                .run(context -> assertFalse(context.containsBean("manualTriggerController")));
    }

    @Test
    void registersControllerWhenEnabled() {
        contextRunner
                .withPropertyValues("signal-brief.internal-api.enabled=true")
                .run(context -> assertTrue(context.containsBean("manualTriggerController")));
    }

    @Configuration
    static class TestConfiguration {

        @Bean
        FeedIngestionService feedIngestionService() {
            return new FeedIngestionService(null, null, null, null);
        }

        @Bean
        BriefGenerationService briefGenerationService() {
            return new BriefGenerationService(null, null);
        }
    }
}
```

- [ ] **Step 3: 运行错误与条件装配测试确认失败**

Run:

```bash
./mvnw -o -Dspring.docker.compose.enabled=false -Dtest=ManualTriggerControllerTest,ManualTriggerControllerConditionTest test
```

Expected: 错误响应测试失败，提示没有 `ManualTriggerExceptionHandler` 或响应结构不是 `{"message": ...}`。

- [ ] **Step 4: 增加错误响应对象**

Create `src/main/java/cn/name/celestrong/signalbrief/internal/InternalApiErrorResponse.java`:

```java
package cn.name.celestrong.signalbrief.internal;

public record InternalApiErrorResponse(String message) {
}
```

- [ ] **Step 5: 增加内部 API 异常处理器**

Create `src/main/java/cn/name/celestrong/signalbrief/internal/ManualTriggerExceptionHandler.java`:

```java
package cn.name.celestrong.signalbrief.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 内部手动触发 API 的 HTTP 错误映射。
 */
@RestControllerAdvice(assignableTypes = ManualTriggerController.class)
@ConditionalOnProperty(prefix = "signal-brief.internal-api", name = "enabled", havingValue = "true")
public class ManualTriggerExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<InternalApiErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new InternalApiErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<InternalApiErrorResponse> handleUnreadableRequest() {
        return ResponseEntity.badRequest().body(new InternalApiErrorResponse("请求体格式不正确"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<InternalApiErrorResponse> handleUnexpectedException() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new InternalApiErrorResponse("内部接口执行失败"));
    }
}
```

- [ ] **Step 6: 运行错误与条件装配测试确认通过**

Run:

```bash
./mvnw -o -Dspring.docker.compose.enabled=false -Dtest=ManualTriggerControllerTest,ManualTriggerControllerConditionTest test
```

Expected: `BUILD SUCCESS`，Web 成功路径、错误响应和条件装配测试全部通过。

### Task 4: OpenAPI 文档

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/cn/name/celestrong/signalbrief/internal/OpenApiConfiguration.java`
- Modify: `src/main/java/cn/name/celestrong/signalbrief/internal/ManualTriggerController.java`
- Modify: `src/main/java/cn/name/celestrong/signalbrief/internal/MarkdownBriefRequest.java`
- Modify: `src/main/java/cn/name/celestrong/signalbrief/internal/MarkdownBriefResponse.java`
- Modify: `src/main/java/cn/name/celestrong/signalbrief/internal/InternalApiErrorResponse.java`
- Test: `src/test/java/cn/name/celestrong/signalbrief/internal/OpenApiConfigurationTest.java`
- Test: `src/test/java/cn/name/celestrong/signalbrief/internal/ManualTriggerControllerTest.java`

- [ ] **Step 1: 引入 springdoc 依赖**

Modify `pom.xml` properties:

```xml
<springdoc-openapi.version>3.0.3</springdoc-openapi.version>
```

Add dependency near WebMVC dependencies:

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>${springdoc-openapi.version}</version>
</dependency>
```

Run:

```bash
./mvnw -o -Dspring.docker.compose.enabled=false -DskipTests compile
```

Expected: `BUILD SUCCESS`。如果本地离线缓存缺少 springdoc 3.0.3，改用联网 Maven 执行同一命令；不要改用 springdoc 2.x，因为当前项目运行在 Spring Boot 4。

- [ ] **Step 2: 写 OpenAPI 配置测试**

Create `src/test/java/cn/name/celestrong/signalbrief/internal/OpenApiConfigurationTest.java`:

```java
package cn.name.celestrong.signalbrief.internal;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(OpenApiConfiguration.class);

    @Test
    void doesNotRegisterOpenApiConfigurationWhenPropertyIsMissing() {
        contextRunner.run(context -> {
            assertFalse(context.containsBean("signalBriefOpenApi"));
            assertFalse(context.containsBean("internalGroupedOpenApi"));
        });
    }

    @Test
    void doesNotRegisterOpenApiConfigurationWhenDisabled() {
        contextRunner
                .withPropertyValues("springdoc.api-docs.enabled=false")
                .run(context -> {
                    assertFalse(context.containsBean("signalBriefOpenApi"));
                    assertFalse(context.containsBean("internalGroupedOpenApi"));
                });
    }

    @Test
    void registersInternalOpenApiConfigurationWhenEnabled() {
        contextRunner
                .withPropertyValues("springdoc.api-docs.enabled=true")
                .run(context -> {
                    assertTrue(context.containsBean("signalBriefOpenApi"));
                    assertTrue(context.containsBean("internalGroupedOpenApi"));

                    OpenAPI openAPI = context.getBean("signalBriefOpenApi", OpenAPI.class);
                    assertEquals("SignalBrief Internal API", openAPI.getInfo().getTitle());

                    GroupedOpenApi groupedOpenApi = context.getBean("internalGroupedOpenApi", GroupedOpenApi.class);
                    assertEquals("internal", groupedOpenApi.getGroup());
                    assertEquals(List.of("/internal/**"), groupedOpenApi.getPathsToMatch());
                    assertEquals(
                            List.of("cn.name.celestrong.signalbrief.internal"),
                            groupedOpenApi.getPackagesToScan()
                    );
                });
    }
}
```

- [ ] **Step 3: 运行 OpenAPI 配置测试确认失败**

Run:

```bash
./mvnw -o -Dspring.docker.compose.enabled=false -Dtest=OpenApiConfigurationTest test
```

Expected: 编译失败，提示 `OpenApiConfiguration` 不存在。

- [ ] **Step 4: 增加 OpenAPI 配置类**

Create `src/main/java/cn/name/celestrong/signalbrief/internal/OpenApiConfiguration.java`:

```java
package cn.name.celestrong.signalbrief.internal;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 内部 API 的 OpenAPI 文档配置。
 */
@Configuration
@ConditionalOnProperty(prefix = "springdoc.api-docs", name = "enabled", havingValue = "true")
public class OpenApiConfiguration {

    @Bean
    public OpenAPI signalBriefOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SignalBrief Internal API")
                        .version("v1")
                        .description("SignalBrief 内部手动触发接口文档。"));
    }

    @Bean
    public GroupedOpenApi internalGroupedOpenApi() {
        return GroupedOpenApi.builder()
                .group("internal")
                .pathsToMatch("/internal/**")
                .packagesToScan("cn.name.celestrong.signalbrief.internal")
                .build();
    }
}
```

- [ ] **Step 5: 运行 OpenAPI 配置测试确认通过**

Run:

```bash
./mvnw -o -Dspring.docker.compose.enabled=false -Dtest=OpenApiConfigurationTest test
```

Expected: `BUILD SUCCESS`，`OpenApiConfigurationTest` 3 个测试通过。

- [ ] **Step 6: 给内部 API 补充 OpenAPI 注解**

Modify `src/main/java/cn/name/celestrong/signalbrief/internal/ManualTriggerController.java`:

```java
package cn.name.celestrong.signalbrief.internal;

import cn.name.celestrong.signalbrief.brief.BriefGenerationService;
import cn.name.celestrong.signalbrief.ingestion.FeedIngestionResult;
import cn.name.celestrong.signalbrief.ingestion.FeedIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部手动触发入口。
 *
 * <p>该入口只做 HTTP 协议适配，具体入库和简报生成逻辑继续委托应用服务。</p>
 */
@Tag(name = "内部手动触发", description = "RSS 入库和 Markdown 简报草稿的手动触发接口")
@RestController
@RequestMapping("/internal")
@ConditionalOnProperty(prefix = "signal-brief.internal-api", name = "enabled", havingValue = "true")
public class ManualTriggerController {

    private final FeedIngestionService feedIngestionService;
    private final BriefGenerationService briefGenerationService;

    public ManualTriggerController(
            FeedIngestionService feedIngestionService,
            BriefGenerationService briefGenerationService
    ) {
        this.feedIngestionService = feedIngestionService;
        this.briefGenerationService = briefGenerationService;
    }

    @Operation(summary = "触发 RSS 入库", description = "立即抓取所有已启用 RSS / Atom 源并执行去重入库。")
    @ApiResponse(
            responseCode = "200",
            description = "返回本次入库统计",
            content = @Content(schema = @Schema(implementation = FeedIngestionResult.class))
    )
    @PostMapping("/ingestions/rss")
    public FeedIngestionResult triggerRssIngestion() {
        return feedIngestionService.ingestEnabledFeeds();
    }

    @Operation(summary = "生成 Markdown 简报草稿", description = "按半开时间窗口查询候选文章并生成 Markdown 草稿。")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "返回 Markdown 简报草稿",
                    content = @Content(schema = @Schema(implementation = MarkdownBriefResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "请求体或时间窗口非法",
                    content = @Content(schema = @Schema(implementation = InternalApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "内部接口执行失败",
                    content = @Content(schema = @Schema(implementation = InternalApiErrorResponse.class))
            )
    })
    @PostMapping("/briefs/markdown")
    public MarkdownBriefResponse generateMarkdownBrief(@RequestBody MarkdownBriefRequest request) {
        if (request == null || request.startInclusive() == null || request.endExclusive() == null) {
            throw new IllegalArgumentException("startInclusive 和 endExclusive 必须提供");
        }

        String markdown = briefGenerationService.generate(request.startInclusive(), request.endExclusive());
        return new MarkdownBriefResponse(request.startInclusive(), request.endExclusive(), markdown);
    }
}
```

Modify `src/main/java/cn/name/celestrong/signalbrief/internal/MarkdownBriefRequest.java`:

```java
package cn.name.celestrong.signalbrief.internal;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Markdown 简报生成请求")
public record MarkdownBriefRequest(
        @Schema(description = "查询窗口开始时间，包含该时刻", example = "2026-07-01T00:00:00Z")
        Instant startInclusive,

        @Schema(description = "查询窗口结束时间，不包含该时刻", example = "2026-07-16T00:00:00Z")
        Instant endExclusive
) {
}
```

Modify `src/main/java/cn/name/celestrong/signalbrief/internal/MarkdownBriefResponse.java`:

```java
package cn.name.celestrong.signalbrief.internal;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Markdown 简报生成响应")
public record MarkdownBriefResponse(
        @Schema(description = "查询窗口开始时间，包含该时刻")
        Instant startInclusive,

        @Schema(description = "查询窗口结束时间，不包含该时刻")
        Instant endExclusive,

        @Schema(description = "生成的 Markdown 简报草稿")
        String markdown
) {
}
```

Modify `src/main/java/cn/name/celestrong/signalbrief/internal/InternalApiErrorResponse.java`:

```java
package cn.name.celestrong.signalbrief.internal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "内部 API 错误响应")
public record InternalApiErrorResponse(
        @Schema(description = "错误说明", example = "请求体格式不正确")
        String message
) {
}
```

- [ ] **Step 7: 运行 OpenAPI 和 Controller 测试确认通过**

Run:

```bash
./mvnw -o -Dspring.docker.compose.enabled=false -Dtest=OpenApiConfigurationTest,ManualTriggerControllerTest test
```

Expected: `BUILD SUCCESS`，OpenAPI 配置测试和 Controller Web 层测试全部通过。

### Task 5: 文档同步

**Files:**
- Modify: `README.md`
- Modify: `docs/personal-tech-newsletter-system.md`
- Modify: `docs/records/rss-ingestion.md`
- Modify: `docs/records/markdown-brief-generation.md`
- Modify: `docs/superpowers/specs/2026-07-05-manual-trigger-api-design.md`

- [ ] **Step 1: 修正 spec 中的真实方法名**

Modify `docs/superpowers/specs/2026-07-05-manual-trigger-api-design.md`:

```markdown
该接口触发一次 `FeedIngestionService.ingestEnabledFeeds()`。它不受 `signal-brief.ingestion.enabled` 影响，因为该配置只表示定时任务是否注册。
```

- [ ] **Step 2: 更新 README 配置说明**

Add `SIGNAL_BRIEF_INTERNAL_API_ENABLED` and `SIGNAL_BRIEF_OPENAPI_ENABLED` to README configuration section:

```markdown
- `SIGNAL_BRIEF_INTERNAL_API_ENABLED`：内部手动触发 API 开关，默认 `false`。
- `SIGNAL_BRIEF_OPENAPI_ENABLED`：OpenAPI / Swagger UI 文档开关，默认 `false`。
```

Add a manual trigger section after RSS source configuration:

````markdown
### 内部手动触发 API

内部 API 和 OpenAPI 文档默认关闭。如需本地调试或手动补偿执行，启动时显式开启：

```bash
SPRING_PROFILES_ACTIVE=dev \
SIGNAL_BRIEF_INTERNAL_API_ENABLED=true \
SIGNAL_BRIEF_OPENAPI_ENABLED=true \
./mvnw spring-boot:run
```

Swagger UI 地址：

```text
http://localhost:8080/internal/swagger-ui.html
```

Internal 分组 OpenAPI JSON 地址：

```text
http://localhost:8080/internal/api-docs/internal
```

触发一次 RSS 入库：

```bash
curl -X POST http://localhost:8080/internal/ingestions/rss
```

生成指定窗口的 Markdown 简报草稿：

```bash
curl -X POST http://localhost:8080/internal/briefs/markdown \
  -H 'Content-Type: application/json' \
  -d '{
    "startInclusive": "2026-07-01T00:00:00Z",
    "endExclusive": "2026-07-16T00:00:00Z"
  }'
```
````

- [ ] **Step 3: 更新项目说明当前阶段**

Modify `docs/personal-tech-newsletter-system.md` current capability list:

```markdown
- 通过内部 REST API 手动触发 RSS 入库和 Markdown 简报草稿生成，并通过 OpenAPI / Swagger UI 管理接口文档。
```

Modify recent priorities by removing the completed manual trigger item and keeping later items:

```markdown
1. 补充真实 RSS 源清单和运行配置。
2. 增加抓取超时、重试、User-Agent 和失败告警策略。
3. 基于 Markdown 简报草稿接入 AI 摘要和压缩。
4. 接入一个 AI Provider，完成板块摘要。
5. 实现邮件发送和简报归档。
6. 增加任务运行记录，沉淀抓取数、入库数、摘要状态和发送状态。
```

- [ ] **Step 4: 更新 RSS record**

Modify `docs/records/rss-ingestion.md` 当前覆盖范围：

```markdown
当前已经覆盖：配置源读取、HTTP 抓取、RSS / Atom 解析、文章去重、PostgreSQL 入库、定时触发、手动触发、批次统计和简报候选查询。
```

Add under configuration or maintenance constraints:

```markdown
手动触发入口位于 `POST /internal/ingestions/rss`，由 `signal-brief.internal-api.enabled` 控制是否注册。该入口不受 `signal-brief.ingestion.enabled` 影响，后者只控制定时任务是否注册。

OpenAPI 文档由 `SIGNAL_BRIEF_OPENAPI_ENABLED` 控制，默认关闭；本地开启后可访问 `/internal/api-docs/internal` 和 `/internal/swagger-ui.html`。当前文档通过 internal 分组匹配 `/internal/**`，后续对外 API 应新增 public 分组，不要使用全局扫描配置互相影响。
```

- [ ] **Step 5: 更新 Markdown record**

Modify `docs/records/markdown-brief-generation.md` 当前覆盖范围：

```markdown
当前已经覆盖：候选文章查询编排、分类分组、Markdown 渲染、空值降级、基础 Markdown 转义和内部 API 手动生成。
```

Add under current modules:

```markdown
- `internal/ManualTriggerController`：在内部 API 开启后，按指定时间窗口调用 `BriefGenerationService` 生成 Markdown 草稿。
- `internal/OpenApiConfiguration`：在 OpenAPI 开启后提供 internal 分组和接口元信息。
```

- [ ] **Step 6: 文档格式检查**

Run:

```bash
git diff --check
```

Expected: 无输出，退出码为 0。

### Task 6: 全量基础验证

**Files:**
- All changed files from Tasks 1-5.

- [ ] **Step 1: 运行本地基础测试**

Run:

```bash
./mvnw -o -Dspring.docker.compose.enabled=false test
```

Expected: `BUILD SUCCESS`，全部单元测试通过。已有入库失败隔离测试可能输出 `feed unavailable` 相关日志，只要测试结果为 0 failures / 0 errors 即可。

- [ ] **Step 2: 检查工作区差异**

Run:

```bash
git status --short
```

Expected: 只出现本计划列出的源码、测试、配置和文档文件。

- [ ] **Step 3: 人工核对接口语义**

确认以下条件满足：

- `signal-brief.internal-api.enabled` 默认关闭。
- `POST /internal/ingestions/rss` 调用 `FeedIngestionService.ingestEnabledFeeds()`。
- `POST /internal/briefs/markdown` 调用 `BriefGenerationService.generate(...)`。
- RSS 手动触发不依赖 `signal-brief.ingestion.enabled=true`。
- 请求解析错误和非法窗口返回 `400 Bad Request`。
- 未预期服务异常返回 `500 Internal Server Error`。
- `SIGNAL_BRIEF_OPENAPI_ENABLED` 默认关闭 OpenAPI 和 Swagger UI。
- OpenAPI internal 分组只匹配 `/internal/**` 路径和 `cn.name.celestrong.signalbrief.internal` 包。
- Swagger UI 路径为 `/internal/swagger-ui.html`，internal 分组 OpenAPI JSON 路径为 `/internal/api-docs/internal`。
- 未引入认证体系、AI、邮件、归档表或命令行入口。

- [ ] **Step 4: 等待用户审查**

向用户报告测试结果和文件变化。不要提交代码；如用户明确要求提交，再按项目提交规范执行。
