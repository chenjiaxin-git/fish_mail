ALTER TABLE message_log
    ADD COLUMN event_key VARCHAR(100) NULL,
    ADD COLUMN quote_version INT NOT NULL DEFAULT 0,
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMP(3) NULL,
    ADD COLUMN operation_token VARCHAR(36) NULL,
    ADD COLUMN operation_until TIMESTAMP(3) NULL,
    ADD CONSTRAINT uk_message_event UNIQUE(event_key);
CREATE INDEX idx_message_pending ON message_log(state,next_attempt_at,id);
CREATE INDEX idx_message_lease ON message_log(state,operation_until);
