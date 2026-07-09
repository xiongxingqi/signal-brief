package cn.name.celestrong.signalbrief.ai;

import cn.name.celestrong.signalbrief.config.AiSummaryProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/**
 * 调用兼容 OpenAI Chat Completions 协议的摘要 Provider。
 *
 * <p>请求和响应 DTO 保持在适配器内部，避免协议字段泄露到应用服务层。</p>
 */
@Component
public class OpenAiCompatibleAiSummaryClient implements AiSummaryClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleAiSummaryClient.class);

    private final RestClient restClient;
    private final AiSummaryProperties properties;

    public OpenAiCompatibleAiSummaryClient(
            @Qualifier("aiSummaryRestClient") RestClient restClient,
            AiSummaryProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public String summarize(String systemPrompt, String userContent) {
        long startedAt = System.nanoTime();
        ChatCompletionRequest request = new ChatCompletionRequest(
                properties.model(),
                List.of(
                        new ChatCompletionMessage("system", systemPrompt),
                        new ChatCompletionMessage("user", userContent)
                ),
                properties.temperature(),
                properties.maxOutputTokens()
        );

        try {
            ChatCompletionResponse response = requestSummary(request);
            String summary = extractSummaryContent(response);
            log.info(
                    "AI summary completed: model={}, inputCharacters={}, outputCharacters={}, durationMs={}",
                    properties.model(),
                    userContent.length(),
                    summary.length(),
                    elapsedMillis(startedAt)
            );
            return summary;
        } catch (AiSummaryException ex) {
            log.warn(
                    "AI summary failed: model={}, inputCharacters={}, durationMs={}, error={}",
                    properties.model(),
                    userContent.length(),
                    elapsedMillis(startedAt),
                    ex.getMessage()
            );
            throw ex;
        }
    }

    private ChatCompletionResponse requestSummary(ChatCompletionRequest request) {
        try {
            return restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatCompletionResponse.class);
        } catch (RestClientResponseException ex) {
            // 不把 Provider 响应体透传给调用方，避免泄露供应商错误细节或敏感信息。
            throw new AiSummaryException("AI Provider 返回 HTTP " + ex.getStatusCode().value());
        } catch (ResourceAccessException ex) {
            throw new AiSummaryException("AI Provider 访问失败", ex);
        } catch (RestClientException ex) {
            throw new AiSummaryException("AI Provider 响应结构异常");
        } catch (Exception ex) {
            throw new AiSummaryException("AI Provider 调用失败", ex);
        }
    }

    private String extractSummaryContent(ChatCompletionResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new AiSummaryException("AI Provider 响应缺少摘要内容");
        }

        ChatCompletionChoice choice = response.choices().getFirst();
        if (choice == null || choice.message() == null || StringUtils.isBlank(choice.message().content())) {
            throw new AiSummaryException("AI Provider 响应缺少摘要内容");
        }

        return choice.message().content().strip();
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private record ChatCompletionRequest(
            String model,
            List<ChatCompletionMessage> messages,
            Double temperature,
            @JsonProperty("max_tokens") Integer maxTokens
    ) {
    }

    private record ChatCompletionMessage(
            String role,
            String content
    ) {
    }

    private record ChatCompletionResponse(
            List<ChatCompletionChoice> choices
    ) {
    }

    private record ChatCompletionChoice(
            ChatCompletionMessage message
    ) {
    }
}
