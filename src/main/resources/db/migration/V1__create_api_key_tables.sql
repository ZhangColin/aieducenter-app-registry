-- ========================================================================
-- OpenAPI Context: API Key Management
-- ========================================================================

CREATE TABLE oas_api_keys (
    id BIGINT PRIMARY KEY,
    api_key VARCHAR(64) NOT NULL UNIQUE,
    api_secret VARCHAR(128) NOT NULL,
    business_system_name VARCHAR(128) NOT NULL,
    status INTEGER NOT NULL DEFAULT 1,
    permissions TEXT,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE oas_api_key_permissions (
    id BIGSERIAL PRIMARY KEY,
    api_key_id BIGINT NOT NULL,
    permission VARCHAR(128) NOT NULL,
    CONSTRAINT fk_api_key_permissions_api_key
        FOREIGN KEY (api_key_id) REFERENCES oas_api_keys(id)
);

-- Indexes
CREATE INDEX idx_oas_api_keys_api_key ON oas_api_keys(api_key) WHERE deleted = FALSE;
CREATE INDEX idx_oas_api_keys_system ON oas_api_keys(business_system_name) WHERE deleted = FALSE;
CREATE INDEX idx_oas_api_key_permissions_api_key_id ON oas_api_key_permissions(api_key_id);
