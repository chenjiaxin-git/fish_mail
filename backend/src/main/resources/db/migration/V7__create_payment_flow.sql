ALTER TABLE sales_order_item
    ADD COLUMN payment_extra_reserved_qty DECIMAL(12,3) NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_order_item_extra_reserved CHECK (payment_extra_reserved_qty >= 0);

ALTER TABLE payment_record
    ADD COLUMN quote_version INT NOT NULL DEFAULT 0,
    ADD COLUMN appid VARCHAR(64) NULL,
    ADD COLUMN mchid VARCHAR(64) NULL,
    ADD COLUMN payer_openid VARCHAR(128) NULL,
    ADD COLUMN prepay_id VARCHAR(128) NULL,
    ADD COLUMN expires_at TIMESTAMP(3) NULL,
    ADD COLUMN phase VARCHAR(20) NOT NULL DEFAULT 'NEW',
    ADD COLUMN operation_token VARCHAR(36) NULL,
    ADD COLUMN operation_until TIMESTAMP(3) NULL,
    ADD COLUMN sync_after TIMESTAMP(3) NULL,
    ADD CONSTRAINT uk_payment_order UNIQUE (order_id),
    ADD CONSTRAINT ck_payment_phase CHECK (phase IN ('NEW','CREATING','READY','UNKNOWN','SYNCING','CLOSING','CLOSED','SUCCESS'));

CREATE TABLE payment_event_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_key VARCHAR(100) NOT NULL,
    out_trade_no VARCHAR(64) NULL,
    result VARCHAR(64) NOT NULL,
    encrypted_notification MEDIUMTEXT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_payment_event_key UNIQUE (event_key)
);
CREATE INDEX idx_payment_event_trade ON payment_event_log (out_trade_no, created_at);
