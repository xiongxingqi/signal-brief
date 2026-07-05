package cn.name.celestrong.signalbrief.ingestion;

import java.util.List;

public record RssIngestionRunDetail(
        RssIngestionRun run,
        List<RssIngestionSourceRun> sources
) {
}
