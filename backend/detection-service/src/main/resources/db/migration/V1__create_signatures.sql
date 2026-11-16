CREATE TABLE signatures (
    id BIGSERIAL PRIMARY KEY,
    message_hash VARCHAR(64) NOT NULL UNIQUE,
    verdict VARCHAR(32) NOT NULL,
    category VARCHAR(32),
    spam_score REAL DEFAULT 0,
    rule_id VARCHAR(64),
    reports INTEGER DEFAULT 1,
    created_at BIGINT NOT NULL
);

CREATE INDEX idx_signatures_verdict ON signatures(verdict);
