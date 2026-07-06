package cn.name.celestrong.signalbrief.ai;

import cn.name.celestrong.signalbrief.config.AiSummaryProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Component
public class OpenAiCompatibleAiSummaryClient implements AiSummaryClient {

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
        ChatCompletionRequest request = new ChatCompletionRequest(
                properties.model(),
                List.of(
                        new ChatCompletionMessage("system", systemPrompt),
                        new ChatCompletionMessage("user", userContent)
                ),
                properties.temperature(),
                properties.maxOutputTokens()
        );

        ChatCompletionResponse response = requestSummary(request);
        return extractSummaryContent(response);
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
