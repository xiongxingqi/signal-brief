package cn.name.celestrong.signalbrief.ai;

import cn.name.celestrong.signalbrief.config.AiSummaryProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 使用 MockRestServiceServer 固定 AI Provider 边界，避免测试访问真实服务。
 */
class OpenAiCompatibleAiSummaryClientTest {

    @Test
    void postsChatCompletionRequestAndReturnsAssistantContent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleAiSummaryClient client = client(builder);

        server.expect(requestTo("https://api.example.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("系统提示"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("用户内容"))
                .andExpect(jsonPath("$.temperature").value(0.2))
                .andExpect(jsonPath("$.max_tokens").value(2000))
                .andRespond(withSuccess("""
                        {
                          "id": "chatcmpl-test",
                          "object": "chat.completion",
                          "created": 1783267200,
                          "model": "test-model",
                          "choices": [
                            {
                              "index": 0,
                              "message": {
                                "role": "assistant",
                                "content": "## 摘要\\n- 重点更新"
                              },
                              "finish_reason": "stop"
                            }
                          ],
                          "usage": {
                            "prompt_tokens": 10,
                            "completion_tokens": 20,
                            "total_tokens": 30
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        String summary = client.summarize("系统提示", "用户内容");

        assertEquals("## 摘要\n- 重点更新", summary);
        server.verify();
    }

    @Test
    void mapsProviderServerErrorToAiSummaryException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleAiSummaryClient client = client(builder);

        server.expect(requestTo("https://api.example.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        AiSummaryException exception = assertThrows(
                AiSummaryException.class,
                () -> client.summarize("系统提示", "用户内容")
        );

        assertTrue(exception.getMessage().contains("AI Provider 返回 HTTP 500"));
        assertFalse(exception.getCause() instanceof RestClientResponseException);
        server.verify();
    }

    @Test
    void rejectsResponseWithoutAssistantContent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleAiSummaryClient client = client(builder);

        server.expect(requestTo("https://api.example.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

        AiSummaryException exception = assertThrows(
                AiSummaryException.class,
                () -> client.summarize("系统提示", "用户内容")
        );

        assertEquals("AI Provider 响应缺少摘要内容", exception.getMessage());
        server.verify();
    }

    @Test
    void rejectsNullChoice() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleAiSummaryClient client = client(builder);

        server.expect(requestTo("https://api.example.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"choices\":[null]}", MediaType.APPLICATION_JSON));

        AiSummaryException exception = assertThrows(
                AiSummaryException.class,
                () -> client.summarize("系统提示", "用户内容")
        );

        assertEquals("AI Provider 响应缺少摘要内容", exception.getMessage());
        server.verify();
    }

    @Test
    void rejectsBlankAssistantContent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleAiSummaryClient client = client(builder);

        server.expect(requestTo("https://api.example.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "role": "assistant",
                                "content": "  "
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        AiSummaryException exception = assertThrows(
                AiSummaryException.class,
                () -> client.summarize("系统提示", "用户内容")
        );

        assertEquals("AI Provider 响应缺少摘要内容", exception.getMessage());
        server.verify();
    }

    @Test
    void mapsMalformedProviderResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleAiSummaryClient client = client(builder);

        server.expect(requestTo("https://api.example.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{", MediaType.APPLICATION_JSON));

        AiSummaryException exception = assertThrows(
                AiSummaryException.class,
                () -> client.summarize("系统提示", "用户内容")
        );

        assertEquals("AI Provider 响应结构异常", exception.getMessage());
        server.verify();
    }

    private OpenAiCompatibleAiSummaryClient client(RestClient.Builder builder) {
        AiSummaryProperties properties = new AiSummaryProperties(
                true,
                "https://api.example.com/v1",
                "test-key",
                "test-model",
                null,
                null,
                null,
                null
        );
        RestClient restClient = builder
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .build();
        return new OpenAiCompatibleAiSummaryClient(restClient, properties);
    }
}
