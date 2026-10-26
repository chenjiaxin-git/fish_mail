CREATE TABLE sales_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(40) NOT NULL,
    wx_user_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    source VARCHAR(20) NOT NULL DEFAULT 'MINI_PROGRAM',
    remark VARCHAR(500) NULL,
    address_snapshot JSON NOT NULL,
    estimated_amount_cent BIGINT NOT NULL,
    final_amount_cent BIGINT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'CNY',
    quote_version INT NOT NULL DEFAULT 0,
    quoted_at TIMESTAMP(3) NULL,
    pay_deadline_at TIMESTAMP(3) NULL,
    paid_at TIMESTAMP(3) NULL,
    delivered_at TIMESTAMP(3) NULL,
    completed_at TIMESTAMP(3) NULL,
    closed_at TIMESTAMP(3) NULL,
    cancelled_at TIMESTAMP(3) NULL,
    cancel_reason VARCHAR(500) NULL,
    refund_status VARCHAR(20) NOT NULL DEFAULT 'NONE',
    refund_reference VARCHAR(100) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sales_order_no UNIQUE (order_no),
    CONSTRAINT ck_sales_order_status CHECK (status IN ('CREATED', 'QUOTED', 'PENDING_PAYMENT', 'PAID_WAIT_DELIVERY', 'DELIVERING', 'COMPLETED', 'CANCELLED', 'CLOSED')),
    CONSTRAINT ck_sales_order_source CHECK (source IN ('MINI_PROGRAM', 'ADMIN')),
    CONSTRAINT ck_sales_order_currency CHECK (currency = 'CNY'),
    CONSTRAINT ck_sales_order_estimated CHECK (estimated_amount_cent >= 0),
    CONSTRAINT ck_sales_order_final CHECK (final_amount_cent IS NULL OR final_amount_cent >= 0),
    CONSTRAINT ck_sales_order_refund CHECK (refund_status IN ('NONE', 'REQUIRED', 'MANUAL_REFUNDED'))
);

CREATE INDEX idx_sales_order_user_status_created ON sales_order (wx_user_id, status, created_at);
CREATE INDEX idx_sales_order_status_pay_deadline ON sales_order (status, pay_deadline_at);
CREATE INDEX idx_sales_order_status_created ON sales_order (status, created_at);

CREATE TABLE sales_order_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_spec_id BIGINT NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    spec_name VARCHAR(50) NOT NULL,
    quantity_kind VARCHAR(20) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    unit_price_cent BIGINT NOT NULL,
    estimated_quantity DECIMAL(12,3) NOT NULL,
    actual_quantity DECIMAL(12,3) NULL,
    estimated_amount_cent BIGINT NOT NULL,
    final_amount_cent BIGINT NULL,
    product_image_url VARCHAR(500) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sales_order_item_order FOREIGN KEY (order_id) REFERENCES sales_order (id),
    CONSTRAINT fk_sales_order_item_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT fk_sales_order_item_spec FOREIGN KEY (product_spec_id) REFERENCES product_spec (id),
    CONSTRAINT ck_sales_order_item_quantity_kind CHECK (quantity_kind IN ('PIECE', 'WEIGHT')),
    CONSTRAINT ck_sales_order_item_estimated_qty CHECK (estimated_quantity > 0),
    CONSTRAINT ck_sales_order_item_actual_qty CHECK (actual_quantity IS NULL OR actual_quantity >= 0),
    CONSTRAINT ck_sales_order_item_unit_price CHECK (unit_price_cent >= 0),
    CONSTRAINT ck_sales_order_item_estimated_amount CHECK (estimated_amount_cent >= 0),
    CONSTRAINT ck_sales_order_item_final_amount CHECK (final_amount_cent IS NULL OR final_amount_cent >= 0)
);

CREATE INDEX idx_sales_order_item_order ON sales_order_item (order_id);
CREATE INDEX idx_sales_order_item_spec ON sales_order_item (product_spec_id);

CREATE TABLE order_price_revision (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    revision_no INT NOT NULL,
    previous_amount_cent BIGINT NOT NULL,
    final_amount_cent BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    items_snapshot JSON NOT NULL,
    adjusted_by_type VARCHAR(20) NOT NULL,
    adjusted_by_id BIGINT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_price_revision_order FOREIGN KEY (order_id) REFERENCES sales_order (id),
    CONSTRAINT uk_order_price_revision_no UNIQUE (order_id, revision_no),
    CONSTRAINT ck_order_price_revision_type CHECK (adjusted_by_type IN ('USER', 'ADMIN', 'SYSTEM')),
    CONSTRAINT ck_order_price_revision_previous CHECK (previous_amount_cent >= 0),
    CONSTRAINT ck_order_price_revision_final CHECK (final_amount_cent >= 0)
);

CREATE INDEX idx_order_price_revision_order ON order_price_revision (order_id, revision_no);

CREATE TABLE payment_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    out_trade_no VARCHAR(64) NOT NULL,
    transaction_id VARCHAR(64) NULL,
    state VARCHAR(20) NOT NULL,
    amount_cent BIGINT NOT NULL,
    trade_state VARCHAR(30) NULL,
    notify_raw JSON NULL,
    paid_at TIMESTAMP(3) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_record_order FOREIGN KEY (order_id) REFERENCES sales_order (id),
    CONSTRAINT uk_payment_record_out_trade_no UNIQUE (out_trade_no),
    CONSTRAINT uk_payment_record_transaction_id UNIQUE (transaction_id),
    CONSTRAINT ck_payment_record_state CHECK (state IN ('INIT', 'SUCCESS', 'FAILED')),
    CONSTRAINT ck_payment_record_amount CHECK (amount_cent >= 0)
);

CREATE INDEX idx_payment_record_order ON payment_record (order_id, created_at);

CREATE TABLE order_status_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    from_status VARCHAR(30) NULL,
    to_status VARCHAR(30) NOT NULL,
    operator_type VARCHAR(20) NOT NULL,
    operator_id BIGINT NULL,
    reason VARCHAR(500) NULL,
    source VARCHAR(30) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_status_log_order FOREIGN KEY (order_id) REFERENCES sales_order (id),
    CONSTRAINT ck_order_status_log_operator CHECK (operator_type IN ('USER', 'ADMIN', 'SYSTEM', 'PAYMENT'))
);

CREATE INDEX idx_order_status_log_order ON order_status_log (order_id, created_at);

CREATE TABLE message_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    wx_user_id BIGINT NULL,
    order_id BIGINT NULL,
    template_type VARCHAR(50) NOT NULL,
    template_id VARCHAR(100) NULL,
    request_json JSON NULL,
    response_json JSON NULL,
    state VARCHAR(20) NOT NULL,
    error_message VARCHAR(500) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_message_log_order FOREIGN KEY (order_id) REFERENCES sales_order (id)
);

CREATE INDEX idx_message_log_order ON message_log (order_id, created_at);

ALTER TABLE product_stock_ledger ADD COLUMN order_id BIGINT NULL;
CREATE INDEX idx_product_stock_ledger_order ON product_stock_ledger (order_id);
