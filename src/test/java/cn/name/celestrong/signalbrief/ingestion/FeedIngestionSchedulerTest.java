package cn.name.celestrong.signalbrief.ingestion;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用 ApplicationContextRunner 验证条件装配，不启动完整 Spring Boot 应用。
 */
class FeedIngestionSchedulerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(FeedIngestionScheduler.class, TestConfiguration.class);

    @Test
    void doesNotRegisterSchedulerWhenPropertyIsMissing() {
        contextRunner.run(context -> assertFalse(context.containsBean("feedIngestionScheduler")));
    }

    @Test
    void doesNotRegisterSchedulerWhenDisabled() {
        contextRunner
                .withPropertyValues("signal-brief.ingestion.enabled=false")
                .run(context -> assertFalse(context.containsBean("feedIngestionScheduler")));
    }

    @Test
    void registersSchedulerWhenEnabled() {
        contextRunner
                .withPropertyValues("signal-brief.ingestion.enabled=true")
                .run(context -> assertTrue(context.containsBean("feedIngestionScheduler")));
    }

    @Test
    void scheduledMethodTriggersIngestionOnce() {
        contextRunner
                .withPropertyValues("signal-brief.ingestion.enabled=true")
                .run(context -> {
                    FeedIngestionScheduler scheduler = context.getBean(FeedIngestionScheduler.class);
                    CountingFeedIngestionOperations operations = context.getBean(CountingFeedIngestionOperations.class);

                    scheduler.ingestEnabledFeeds();

                    assertTrue(operations.called);
                    assertTrue(operations.scheduled);
                });
    }

    @Configuration
    static class TestConfiguration {

        @Bean
        CountingFeedIngestionOperations feedIngestionOperations() {
            return new CountingFeedIngestionOperations();
        }
    }

    static class CountingFeedIngestionOperations implements FeedIngestionOperations {
        private boolean called;
        private boolean scheduled;

        @Override
        public FeedIngestionResult ingestEnabledFeeds(IngestionTriggerType triggerType) {
            called = true;
            scheduled = IngestionTriggerType.SCHEDULED == triggerType;
            return FeedIngestionResult.empty();
        }
    }
}
