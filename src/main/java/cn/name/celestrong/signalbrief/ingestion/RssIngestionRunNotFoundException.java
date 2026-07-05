package cn.name.celestrong.signalbrief.ingestion;

/**
 * RSS 入库运行记录不存在。
 */
public class RssIngestionRunNotFoundException extends RuntimeException {

    public RssIngestionRunNotFoundException(Long runId) {
        super("RSS 入库运行记录不存在: " + runId);
    }
}
