CREATE TABLE rss_ingestion_run (
    id BIGSERIAL PRIMARY KEY,
    trigger_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    duration_millis BIGINT,
    source_count INTEGER NOT NULL DEFAULT 0,
    fetched_count INTEGER NOT NULL DEFAULT 0,
    inserted_count INTEGER NOT NULL DEFAULT 0,
    skipped_count INTEGER NOT NULL DEFAULT 0,
    failed_source_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_rss_ingestion_run_trigger_type
        CHECK (trigger_type IN ('MANUAL', 'SCHEDULED')),
    CONSTRAINT ck_rss_ingestion_run_status
        CHECK (status IN ('RUNNING', 'SUCCESS', 'PARTIAL_SUCCESS', 'FAILED')),
    CONSTRAINT ck_rss_ingestion_run_counts
        CHECK (
            source_count >= 0
            AND fetched_count >= 0
            AND inserted_count >= 0
            AND skipped_count >= 0
            AND failed_source_count >= 0
        ),
    CONSTRAINT ck_rss_ingestion_run_duration
        CHECK (duration_millis IS NULL OR duration_millis >= 0)
);

CREATE INDEX idx_rss_ingestion_run_started_at
    ON rss_ingestion_run (started_at DESC, id DESC);

CREATE TABLE rss_ingestion_source_run (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES rss_ingestion_run (id) ON DELETE CASCADE,
    source_name VARCHAR(200) NOT NULL,
    source_url TEXT NOT NULL,
    category VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    failure_type VARCHAR(50),
    http_status INTEGER,
    attempt_count INTEGER,
    max_attempts INTEGER,
    error_message VARCHAR(1000),
    fetched_count INTEGER NOT NULL DEFAULT 0,
    inserted_count INTEGER NOT NULL DEFAULT 0,
    skipped_count INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ NOT NULL,
    duration_millis BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_rss_ingestion_source_run_status
        CHECK (status IN ('SUCCESS', 'FAILED')),
    CONSTRAINT ck_rss_ingestion_source_run_counts
        CHECK (
            fetched_count >= 0
            AND inserted_count >= 0
            AND skipped_count >= 0
        ),
    CONSTRAINT ck_rss_ingestion_source_run_duration
        CHECK (duration_millis >= 0),
    CONSTRAINT ck_rss_ingestion_source_run_failure_context
        CHECK (
            (status = 'SUCCESS' AND failure_type IS NULL AND http_status IS NULL
                AND attempt_count IS NULL AND max_attempts IS NULL AND error_message IS NULL)
            OR (status = 'FAILED' AND failure_type IS NOT NULL)
        )
);

CREATE INDEX idx_rss_ingestion_source_run_run_id
    ON rss_ingestion_source_run (run_id, id);
