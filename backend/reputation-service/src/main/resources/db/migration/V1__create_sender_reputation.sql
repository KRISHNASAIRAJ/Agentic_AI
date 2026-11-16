CREATE TABLE sender_reputation (
    sender_hash VARCHAR(64) PRIMARY KEY,
    score INTEGER NOT NULL,
    label VARCHAR(32) NOT NULL,
    source VARCHAR(32) DEFAULT 'community',
    last_updated BIGINT NOT NULL
);

CREATE INDEX idx_reputation_label ON sender_reputation(label);
