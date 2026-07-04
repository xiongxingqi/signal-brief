package cn.name.celestrong.signalbrief.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "signal-brief.ingestion", name = "enabled", havingValue = "true")
public class FeedIngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(FeedIngestionScheduler.class);

    private final FeedIngestionService feedIngestionService;

    public FeedIngestionScheduler(FeedIngestionService feedIngestionService) {
        this.feedIngestionService = feedIngestionService;
    }

    @Scheduled(cron = "${signal-brief.ingestion.cron}")
    public void ingestEnabledFeeds() {
        FeedIngestionResult result = feedIngestionService.ingestEnabledFeeds();
        log.info(
                "Scheduled feed ingestion finished: sources={}, fetched={}, inserted={}, skipped={}, failedSources={}",
                result.sourceCount(),
                result.fetchedCount(),
                result.insertedCount(),
                result.skippedCount(),
                result.failedSourceCount()
        );
    }
}
