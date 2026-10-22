CREATE TABLE product_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    sort INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_product_category_name UNIQUE (name),
    CONSTRAINT ck_product_category_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX idx_product_category_query ON product_category (deleted, status, sort);

CREATE TABLE product (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    subtitle VARCHAR(150) NULL,
    description TEXT NULL,
    main_image_url VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES product_category (id),
    CONSTRAINT ck_product_status CHECK (status IN ('ACTIVE', 'OFF_SHELF'))
);

CREATE INDEX idx_product_category ON product (category_id, deleted, status);
CREATE INDEX idx_product_name ON product (deleted, name);

CREATE TABLE product_spec (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    quantity_kind VARCHAR(20) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    price_cent BIGINT NOT NULL,
    min_quantity DECIMAL(12,3) NOT NULL DEFAULT 1,
    step_quantity DECIMAL(12,3) NOT NULL DEFAULT 1,
    physical_stock_qty DECIMAL(12,3) NOT NULL DEFAULT 0,
    reserved_qty DECIMAL(12,3) NOT NULL DEFAULT 0,
    warning_qty DECIMAL(12,3) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_spec_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT uk_product_spec_name UNIQUE (product_id, name),
    CONSTRAINT ck_product_spec_quantity_kind CHECK (quantity_kind IN ('PIECE', 'WEIGHT')),
    CONSTRAINT ck_product_spec_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_product_spec_price CHECK (price_cent > 0),
    CONSTRAINT ck_product_spec_min CHECK (min_quantity > 0),
    CONSTRAINT ck_product_spec_step CHECK (step_quantity > 0),
    CONSTRAINT ck_product_spec_physical CHECK (physical_stock_qty >= 0),
    CONSTRAINT ck_product_spec_reserved CHECK (reserved_qty >= 0 AND reserved_qty <= physical_stock_qty),
    CONSTRAINT ck_product_spec_warning CHECK (warning_qty >= 0)
);

CREATE INDEX idx_product_spec_product ON product_spec (product_id, deleted, status);

CREATE TABLE product_stock_ledger (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_spec_id BIGINT NOT NULL,
    change_type VARCHAR(30) NOT NULL,
    quantity DECIMAL(12,3) NOT NULL,
    physical_before DECIMAL(12,3) NOT NULL,
    physical_after DECIMAL(12,3) NOT NULL,
    reserved_before DECIMAL(12,3) NOT NULL,
    reserved_after DECIMAL(12,3) NOT NULL,
    reason VARCHAR(200) NOT NULL,
    operator_type VARCHAR(20) NOT NULL,
    operator_id BIGINT NULL,
    biz_key VARCHAR(120) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_stock_ledger_spec FOREIGN KEY (product_spec_id) REFERENCES product_spec (id),
    CONSTRAINT uk_product_stock_ledger_biz_key UNIQUE (biz_key),
    CONSTRAINT ck_product_stock_ledger_change_type CHECK (change_type IN ('INITIAL', 'ADMIN_ADJUST', 'RESERVE', 'PAY_DEDUCT', 'RELEASE', 'REFUND_RELEASE'))
);

CREATE INDEX idx_product_stock_ledger_spec ON product_stock_ledger (product_spec_id, created_at);

CREATE TABLE admin_operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    admin_id BIGINT NOT NULL,
    admin_username VARCHAR(32) NOT NULL,
    module VARCHAR(30) NOT NULL,
    action VARCHAR(50) NOT NULL,
    biz_id VARCHAR(64) NULL,
    result VARCHAR(20) NOT NULL,
    reason VARCHAR(200) NULL,
    trace_id VARCHAR(64) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_admin_operation_log_created ON admin_operation_log (created_at);
CREATE INDEX idx_admin_operation_log_module ON admin_operation_log (module, action);
