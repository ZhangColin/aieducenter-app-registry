-- V3: SSO facet（SsoClient 聚合）——供 identity（IdP）OIDC：登记「哪些应用能走 SSO」+ 持其 client 元数据。
-- 1:1 挂在 ar_registered_apps 上、可空（应用可不持 SSO facet）。详见 ADR-0003。
-- 存储与签名 facet（ar_api_keys）相反：client_secret = argon2 hash-only（不可逆，永不返明文）。
CREATE TABLE ar_sso_clients (
    id             BIGINT       NOT NULL,
    app_id         BIGINT       NOT NULL,
    client_id      VARCHAR(128) NOT NULL,
    client_secret  VARCHAR(1024) NOT NULL,
    redirect_uri   JSONB        NOT NULL,
    scopes         JSONB        NOT NULL,
    grants         JSONB        NOT NULL,
    status         INTEGER      NOT NULL DEFAULT 1,

    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL,
    created_by     BIGINT,
    updated_by     BIGINT,
    deleted        BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT pk_ar_sso_clients PRIMARY KEY (id),
    -- client_id 全局唯一（含软删行）：普通唯一约束，软删行也占名额 = 不可复用（ADR-0003 §6）。
    -- 撞名主路径由应用层 native query 先查（看含软删全行）+ DomainException(409)；
    -- 本约束作并发兜底（DuplicateKeyException → 409）。
    CONSTRAINT uk_ar_sso_clients_client_id UNIQUE (client_id),
    CONSTRAINT fk_ar_sso_clients_app FOREIGN KEY (app_id) REFERENCES ar_registered_apps (id)
);

-- app_id 1:1：部分唯一索引（仅活跃行），软删的旧 client 不阻塞新 client（create-or-rotate 的并发兜底）。
CREATE UNIQUE INDEX uk_ar_sso_clients_app_active ON ar_sso_clients (app_id) WHERE deleted = false;

COMMENT ON TABLE  ar_sso_clients IS 'SSO facet（SsoClient 聚合）——OIDC client 元数据';
COMMENT ON COLUMN ar_sso_clients.app_id        IS '所属应用（FK → ar_registered_apps.id）';
COMMENT ON COLUMN ar_sso_clients.client_id     IS 'OIDC client_id，SecureRandom 生成，全局唯一（含软删）；可完全轮换';
COMMENT ON COLUMN ar_sso_clients.client_secret IS 'client_secret 的 argon2 hash（hash-only，不可逆，永不返回明文）';
COMMENT ON COLUMN ar_sso_clients.redirect_uri  IS '回调地址列表（jsonb array），OIDC redirect_uri';
COMMENT ON COLUMN ar_sso_clients.scopes        IS '授权范围集合（jsonb array），去重';
COMMENT ON COLUMN ar_sso_clients.grants        IS '授权类型集合（jsonb array），去重';
COMMENT ON COLUMN ar_sso_clients.status        IS '1=ACTIVE 启用, 0=DISABLED 禁用';
