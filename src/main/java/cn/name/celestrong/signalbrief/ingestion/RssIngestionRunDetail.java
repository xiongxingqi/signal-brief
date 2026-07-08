package cn.name.celestrong.signalbrief.ingestion;

import java.util.List;

/**
 * RSS 入库批次详情，包含批次汇总和各源执行明细。
 */
public record RssIngestionRunDetail(
        RssIngestionRun run,
        List<RssIngestionSourceRun> sources
) {
}
