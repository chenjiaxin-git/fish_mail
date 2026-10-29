ALTER TABLE sales_order
    ADD COLUMN timeout_next_attempt_at TIMESTAMP(3) NULL,
    ADD COLUMN timeout_attempt_count INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_order_timeout_attempt_count CHECK (timeout_attempt_count >= 0);
CREATE INDEX idx_order_timeout_retry ON sales_order (status, timeout_next_attempt_at, id);
