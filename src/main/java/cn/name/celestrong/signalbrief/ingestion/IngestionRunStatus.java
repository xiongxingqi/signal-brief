package cn.name.celestrong.signalbrief.ingestion;

/**
 * RSS 入库批次状态。
 */
public enum IngestionRunStatus {
    RUNNING,
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILED
}
