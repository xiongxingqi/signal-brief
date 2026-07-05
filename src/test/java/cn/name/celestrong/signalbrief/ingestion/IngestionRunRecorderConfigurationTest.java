package cn.name.celestrong.signalbrief.ingestion;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngestionRunRecorderConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(IngestionRunRecorder.class, TestConfiguration.class);

    @Test
    void registersRecorderWithMapperConstructor() {
        contextRunner.run(context -> {
            assertNull(context.getStartupFailure());
            assertTrue(context.containsBean("ingestionRunRecorder"));
        });
    }

    @Configuration
    static class TestConfiguration {

        @Bean
        RssIngestionRunMapper rssIngestionRunMapper() {
            return new StubRssIngestionRunMapper();
        }
    }

    private static final class StubRssIngestionRunMapper implements RssIngestionRunMapper {

        @Override
        public Long insertRun(
                IngestionTriggerType triggerType,
                IngestionRunStatus status,
                Instant startedAt,
                int sourceCount
        ) {
            return 1L;
        }

        @Override
        public int insertSourceRun(NewRssIngestionSourceRun sourceRun) {
            return 1;
        }

        @Override
        public int finishRun(
                Long runId,
                IngestionRunStatus status,
                Instant finishedAt,
                long durationMillis,
                int fetchedCount,
                int insertedCount,
                int skippedCount,
                int failedSourceCount
        ) {
            return 1;
        }

        @Override
        public Optional<RssIngestionRun> findRunById(Long runId) {
            return Optional.empty();
        }

        @Override
        public List<RssIngestionRun> findRecentRuns(int limit) {
            return List.of();
        }

        @Override
        public List<RssIngestionSourceRun> findSourcesByRunId(Long runId) {
            return List.of();
        }
    }
}
