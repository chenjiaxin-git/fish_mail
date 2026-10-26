CREATE TABLE shop_banner (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(100) NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    jump_type VARCHAR(20) NOT NULL DEFAULT 'NONE',
    jump_target_id BIGINT NULL,
    sort INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_shop_banner_jump_type CHECK (jump_type IN ('NONE', 'PRODUCT', 'CATEGORY')),
    CONSTRAINT ck_shop_banner_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX idx_shop_banner_query ON shop_banner (deleted, status, sort);
