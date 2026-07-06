package cn.name.celestrong.signalbrief.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiSummaryConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withUserConfiguration(AiSummaryConfiguration.class)
            .withBean(AiSummaryProperties.class, () -> new AiSummaryProperties(
                    true,
                    "https://api.example.com/v1/",
                    "test-key",
                    "test-model",
                    null,
                    null,
                    null,
                    null
            ));

    @Test
    void registersAiSummaryRestClientAndRequestFactory() {
        contextRunner.run(context -> {
            assertTrue(context.containsBean("aiSummaryRestClient"));
            context.getBean("aiSummaryRestClient", RestClient.class);

            assertTrue(context.containsBean("aiSummaryClientHttpRequestFactory"));
            context.getBean(
                    "aiSummaryClientHttpRequestFactory",
                    HttpComponentsClientHttpRequestFactory.class
            );
        });
    }

    @Test
    void coexistsWithFeedHttpConfiguration() {
        contextRunner
                .withUserConfiguration(FeedHttpConfiguration.class)
                .withBean(FeedHttpProperties.class, () -> new FeedHttpProperties(null, null, null, null))
                .run(context -> {
                    context.getBean("aiSummaryRestClient", RestClient.class);
                    context.getBean("feedRestClient", RestClient.class);
                    context.getBean(
                            "aiSummaryClientHttpRequestFactory",
                            HttpComponentsClientHttpRequestFactory.class
                    );
                    context.getBean(
                            "feedClientHttpRequestFactory",
                            HttpComponentsClientHttpRequestFactory.class
                    );
                });
    }

    @Test
    void usesExplicitQualifiersForDedicatedRequestFactories() throws Exception {
        Method aiMethod = AiSummaryConfiguration.class.getDeclaredMethod(
                "aiSummaryRestClient",
                RestClient.Builder.class,
                HttpComponentsClientHttpRequestFactory.class,
                AiSummaryProperties.class
        );
        Method feedMethod = FeedHttpConfiguration.class.getDeclaredMethod(
                "feedRestClient",
                RestClient.Builder.class,
                HttpComponentsClientHttpRequestFactory.class,
                FeedHttpProperties.class
        );

        assertEquals(
                "aiSummaryClientHttpRequestFactory",
                requestFactoryQualifierValue(aiMethod)
        );
        assertEquals(
                "feedClientHttpRequestFactory",
                requestFactoryQualifierValue(feedMethod)
        );
    }

    @Test
    void doesNotMutateSharedRestClientBuilder() {
        RestClient.Builder sharedBuilder = RestClient.builder();
        new AiSummaryConfiguration().aiSummaryRestClient(
                sharedBuilder,
                new HttpComponentsClientHttpRequestFactory(),
                new AiSummaryProperties(
                        true,
                        "https://api.example.com/v1/",
                        "test-key",
                        "test-model",
                        null,
                        null,
                        null,
                        null
                )
        );
        MockRestServiceServer server = MockRestServiceServer.bindTo(sharedBuilder).build();
        RestClient restClient = sharedBuilder.build();

        server.expect(requestTo("https://plain.example.com/ping"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess("", MediaType.TEXT_PLAIN));

        restClient.get()
                .uri("https://plain.example.com/ping")
                .retrieve()
                .toBodilessEntity();

        server.verify();
    }

    private String requestFactoryQualifierValue(Method method) {
        Parameter parameter = Arrays.stream(method.getParameters())
                .filter(candidate -> candidate.getType().equals(HttpComponentsClientHttpRequestFactory.class))
                .findFirst()
                .orElseThrow(IllegalStateException::new);
        Qualifier qualifier = parameter.getAnnotation(Qualifier.class);

        assertNotNull(qualifier);
        return qualifier.value();
    }
}
