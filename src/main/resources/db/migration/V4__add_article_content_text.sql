ALTER TABLE article
    ADD COLUMN content_text TEXT;

COMMENT ON COLUMN article.content_text IS
    'RSS / Atom 正文片段清洗后的纯文本，供后续 AI 摘要上下文使用';
