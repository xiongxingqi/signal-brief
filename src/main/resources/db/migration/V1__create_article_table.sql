CREATE TABLE article (
    id BIGSERIAL PRIMARY KEY,
    source_name VARCHAR(200) NOT NULL,
    source_url TEXT NOT NULL,
    category VARCHAR(50) NOT NULL,
    title TEXT NOT NULL,
    url TEXT,
    guid TEXT,
    published_at TIMESTAMPTZ,
    summary TEXT,
    content_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_article_url
    ON article (url)
    WHERE url IS NOT NULL;

CREATE UNIQUE INDEX uk_article_source_guid
    ON article (source_name, guid)
    WHERE guid IS NOT NULL;

CREATE UNIQUE INDEX uk_article_content_hash
    ON article (content_hash);

CREATE INDEX idx_article_published_at
    ON article (published_at);
