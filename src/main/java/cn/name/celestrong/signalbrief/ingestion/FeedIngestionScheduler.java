package cn.name.celestrong.signalbrief.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * RSS 入库定时任务。
 *
 * <p>该组件只有在 {@code signal-brief.ingestion.enabled=true} 时注册，避免默认环境产生外部网络副作用。</p>
 */
@Component
@ConditionalOnProperty(prefix = "signal-brief.ingestion", name = "enabled", havingValue = "true")
public class FeedIngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(FeedIngestionScheduler.class);

    private final FeedIngestionService feedIngestionService;

    public FeedIngestionScheduler(FeedIngestionService feedIngestionService) {
        this.feedIngestionService = feedIngestionService;
    }

    /**
     * 按配置 cron 触发 RSS 入库，并记录批次统计结果。
     */
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
