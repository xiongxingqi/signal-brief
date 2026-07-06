package cn.name.celestrong.signalbrief.brief;

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
