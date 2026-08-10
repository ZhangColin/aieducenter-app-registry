-- V4: 给 ar_sso_clients 加 post_logout_redirect_uris 列（OIDC RP-Initiated Logout 登出回跳白名单）。
-- 与 redirect_uris 平级独立、JSONB array；identity #39 前置字段。详见 ADR-0005、CONTEXT.md「SsoClient」字段表。
-- 幂等：ADD COLUMN IF NOT EXISTS；COMMENT 本身幂等（重设而非新增）。

ALTER TABLE ar_sso_clients
    ADD COLUMN IF NOT EXISTS post_logout_redirect_uris JSONB NOT NULL DEFAULT '[]'::jsonb;

COMMENT ON COLUMN ar_sso_clients.post_logout_redirect_uris IS 'OIDC RP-Initiated Logout 登出回跳白名单（jsonb array），与 redirect_uris 平级独立';
