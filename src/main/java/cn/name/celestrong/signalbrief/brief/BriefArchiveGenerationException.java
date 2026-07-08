package cn.name.celestrong.signalbrief.brief;

/**
 * 简报归档生成失败异常。
 *
 * <p>携带已创建的归档 ID，便于调用方查询失败记录和草稿内容。</p>
 */
public class BriefArchiveGenerationException extends RuntimeException {

    private final Long briefGenerationId;

    public BriefArchiveGenerationException(Long briefGenerationId, String message, Throwable cause) {
        super(message, cause);
        this.briefGenerationId = briefGenerationId;
    }

    public Long briefGenerationId() {
        return briefGenerationId;
    }
}
