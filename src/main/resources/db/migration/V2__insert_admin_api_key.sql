-- ========================================================================
-- Insert default Admin API Key for initial setup
-- IMPORTANT: Rotate this key in production environments!
-- ========================================================================

INSERT INTO oas_api_keys (id, api_key, api_secret, business_system_name, status, permissions, description, created_at, updated_at, deleted)
VALUES (
    38654705664,
    'AEDUADMIN2024DEFAULTAPIKEY0000001',
    'QWRtaW5Jbml0aWFsU2VjcmV0S2V5MjAyNExvbmdQYXNzd29yZA==',
    '管理后台',
    1,
    'admin',
    '管理后台默认 API Key，请在生产环境中替换',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    FALSE
);
