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
