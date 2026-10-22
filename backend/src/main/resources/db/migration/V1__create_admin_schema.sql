CREATE TABLE admin_user (
    id BIGINT PRIMARY KEY,
    username VARCHAR(32) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    token_version BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_login_at TIMESTAMP(3) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_admin_user_username UNIQUE (username),
    CONSTRAINT ck_admin_user_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE admin_auth_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(32) NOT NULL,
    action VARCHAR(30) NOT NULL,
    result VARCHAR(20) NOT NULL,
    failure_code VARCHAR(50) NULL,
    ip VARCHAR(64) NULL,
    user_agent VARCHAR(255) NULL,
    trace_id VARCHAR(64) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_admin_auth_log_result CHECK (result IN ('SUCCESS', 'FAILED'))
);

CREATE INDEX idx_admin_auth_log_created ON admin_auth_log (created_at);
CREATE INDEX idx_admin_auth_log_result ON admin_auth_log (result);
