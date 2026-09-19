CREATE DATABASE IF NOT EXISTS llm_gateway
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE llm_gateway;

CREATE TABLE IF NOT EXISTS upstream_channel (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    base_url VARCHAR(255) NOT NULL,
    api_key_ciphertext VARCHAR(512) NULL,
    model VARCHAR(100) NOT NULL,
    weight INT NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    health_status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_channel_status (status),
    KEY idx_channel_model (model)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS downstream_api_key (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    key_hash CHAR(64) NOT NULL,
    key_prefix VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    quota_amount DECIMAL(18, 6) NOT NULL DEFAULT 0,
    used_amount DECIMAL(18, 6) NOT NULL DEFAULT 0,
    expires_at DATETIME NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_api_key_hash (key_hash),
    KEY idx_api_key_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS request_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_id VARCHAR(64) NOT NULL,
    api_key_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    model VARCHAR(100) NULL,
    prompt_tokens INT NULL,
    completion_tokens INT NULL,
    cost DECIMAL(18, 6) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    latency_ms BIGINT NULL,
    error_code VARCHAR(100) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_log_request_id (request_id),
    KEY idx_request_log_api_key_created (api_key_id, created_at),
    KEY idx_request_log_channel_created (channel_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
