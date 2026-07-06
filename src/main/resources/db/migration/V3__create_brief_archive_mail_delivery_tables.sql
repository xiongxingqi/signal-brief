CREATE TABLE brief_generation (
    id BIGSERIAL PRIMARY KEY,
    start_inclusive TIMESTAMPTZ NOT NULL,
    end_exclusive TIMESTAMPTZ NOT NULL,
    status VARCHAR(50) NOT NULL,
    draft_markdown TEXT NOT NULL,
    summary_markdown TEXT,
    error_summary VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT ck_brief_generation_window
        CHECK (start_inclusive < end_exclusive),
    CONSTRAINT ck_brief_generation_status
        CHECK (status IN ('GENERATING', 'SUCCESS', 'FAILED')),
    CONSTRAINT ck_brief_generation_status_context
        CHECK (
            (status = 'GENERATING' AND summary_markdown IS NULL
                AND error_summary IS NULL AND completed_at IS NULL)
            OR (status = 'SUCCESS' AND summary_markdown IS NOT NULL
                AND error_summary IS NULL AND completed_at IS NOT NULL)
            OR (status = 'FAILED' AND summary_markdown IS NULL
                AND error_summary IS NOT NULL
                AND completed_at IS NOT NULL)
        )
);

CREATE INDEX idx_brief_generation_created_at
    ON brief_generation (created_at DESC, id DESC);

CREATE INDEX idx_brief_generation_window
    ON brief_generation (start_inclusive, end_exclusive, id DESC);

CREATE TABLE brief_mail_delivery (
    id BIGSERIAL PRIMARY KEY,
    brief_generation_id BIGINT NOT NULL REFERENCES brief_generation (id) ON DELETE CASCADE,
    recipient VARCHAR(320) NOT NULL,
    status VARCHAR(50) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    error_summary VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ,
    CONSTRAINT ck_brief_mail_delivery_status
        CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT ck_brief_mail_delivery_status_context
        CHECK (
            (status = 'PENDING' AND error_summary IS NULL AND sent_at IS NULL)
            OR (status = 'SENT' AND error_summary IS NULL AND sent_at IS NOT NULL)
            OR (status = 'FAILED' AND error_summary IS NOT NULL AND sent_at IS NULL)
        )
);

CREATE INDEX idx_brief_mail_delivery_generation_id
    ON brief_mail_delivery (brief_generation_id, id);

COMMENT ON TABLE brief_generation IS
    '保存一次简报生成尝试，包括 Markdown 草稿、AI 摘要、状态和错误摘要。';
COMMENT ON COLUMN brief_generation.start_inclusive IS
    '候选文章时间窗口开始时间，包含该时刻。';
COMMENT ON COLUMN brief_generation.end_exclusive IS
    '候选文章时间窗口结束时间，不包含该时刻。';
COMMENT ON COLUMN brief_generation.status IS
    '生成状态：GENERATING 表示生成中，SUCCESS 表示 AI 摘要成功，FAILED 表示生成失败。';
COMMENT ON COLUMN brief_generation.draft_markdown IS
    '确定性 Markdown 简报草稿，由候选文章渲染得到。';
COMMENT ON COLUMN brief_generation.summary_markdown IS
    'AI 摘要 Markdown，生成成功时写入。';
COMMENT ON COLUMN brief_generation.error_summary IS
    '生成失败摘要，截断后保存，不包含完整堆栈或 Provider 原始响应。';
COMMENT ON COLUMN brief_generation.completed_at IS
    '生成成功或失败的完成时间。';

COMMENT ON TABLE brief_mail_delivery IS
    '保存归档简报的邮件发送结果，按收件人记录。';
COMMENT ON COLUMN brief_mail_delivery.brief_generation_id IS
    '关联的简报生成归档 ID。';
COMMENT ON COLUMN brief_mail_delivery.recipient IS
    '本次发送的收件人邮箱。';
COMMENT ON COLUMN brief_mail_delivery.status IS
    '发送状态：PENDING 表示待发送，SENT 表示已发送，FAILED 表示发送失败。';
COMMENT ON COLUMN brief_mail_delivery.subject IS
    '邮件主题。';
COMMENT ON COLUMN brief_mail_delivery.error_summary IS
    '发送失败摘要，截断后保存，不包含完整堆栈或 SMTP 敏感信息。';
COMMENT ON COLUMN brief_mail_delivery.sent_at IS
    '邮件发送成功时间。';
