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
