CREATE TABLE wx_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    openid VARCHAR(64) NOT NULL,
    nickname VARCHAR(100) NULL,
    avatar_url VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_login_at TIMESTAMP(3) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_wx_user_openid UNIQUE (openid),
    CONSTRAINT ck_wx_user_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE user_address (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    wx_user_id BIGINT NOT NULL,
    contact_name VARCHAR(50) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    detail_address VARCHAR(200) NOT NULL,
    is_default TINYINT(1) NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_user_address_wx_user FOREIGN KEY (wx_user_id) REFERENCES wx_user (id),
    CONSTRAINT ck_user_address_default CHECK (is_default IN (0, 1)),
    CONSTRAINT ck_user_address_deleted CHECK (deleted IN (0, 1))
);

CREATE INDEX idx_user_address_user ON user_address (wx_user_id, deleted, is_default);

CREATE TABLE cart_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    wx_user_id BIGINT NOT NULL,
    product_spec_id BIGINT NOT NULL,
    quantity DECIMAL(12,3) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_cart_item_wx_user FOREIGN KEY (wx_user_id) REFERENCES wx_user (id),
    CONSTRAINT fk_cart_item_spec FOREIGN KEY (product_spec_id) REFERENCES product_spec (id),
    CONSTRAINT uk_cart_item_user_spec UNIQUE (wx_user_id, product_spec_id),
    CONSTRAINT ck_cart_item_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_cart_item_user ON cart_item (wx_user_id, updated_at);

ALTER TABLE sales_order ADD COLUMN idempotency_key VARCHAR(64) NULL;

CREATE UNIQUE INDEX uk_sales_order_user_idempotency ON sales_order (wx_user_id, idempotency_key);
