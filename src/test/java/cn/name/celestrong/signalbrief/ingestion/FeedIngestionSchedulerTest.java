package cn.name.celestrong.signalbrief.ingestion;

import cn.name.celestrong.signalbrief.config.FeedProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                    CountingFeedIngestionService service = context.getBean(CountingFeedIngestionService.class);

                    scheduler.ingestEnabledFeeds();

                    assertEquals(1, service.calls);
                });
    }

    @Configuration
    static class TestConfiguration {

        @Bean
        FeedProperties feedProperties() {
            return new FeedProperties(List.of());
        }

        @Bean
        CountingFeedIngestionService feedIngestionService() {
            return new CountingFeedIngestionService();
        }
    }

    static class CountingFeedIngestionService extends FeedIngestionService {
        private int calls;

        CountingFeedIngestionService() {
            super(null, null, null, null);
        }

        /**
         * 只记录调度调用次数，避免测试触发真实抓取链路。
         */
        @Override
        public FeedIngestionResult ingestEnabledFeeds() {
            calls++;
            return FeedIngestionResult.empty();
        }
    }
}
