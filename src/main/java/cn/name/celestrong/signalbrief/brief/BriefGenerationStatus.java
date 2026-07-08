package cn.name.celestrong.signalbrief.brief;

/**
 * 简报归档生成状态。
 *
 * <p>状态转换只允许从 {@code GENERATING} 到终态，避免外部调用重试时覆盖历史结果。</p>
 */
public enum BriefGenerationStatus {
    GENERATING,
    SUCCESS,
    FAILED
}
