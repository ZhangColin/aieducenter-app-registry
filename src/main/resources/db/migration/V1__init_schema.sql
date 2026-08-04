-- V1: 初始化全部表结构（RegisteredApp / ApiKey / SsoClient 三个聚合根）。
-- 详见 ADR-0001（应用聚合边界与标识模型）、ADR-0002（ApiKey 聚合）、ADR-0003（SsoClient 聚合）。

-- ============================================================
-- 应用登记主表（RegisteredApp 聚合根）
-- 一个应用一行，可持有 ApiKey 和/或 SsoClient，以 app 内部 id 关联。
-- ============================================================
CREATE TABLE ar_registered_apps (
    id           BIGINT       NOT NULL,
    app_code     VARCHAR(64)  NOT NULL,
    name         VARCHAR(128) NOT NULL,
    description  VARCHAR(512),
    status       INTEGER      NOT NULL DEFAULT 1,

    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    created_by   BIGINT,
    updated_by   BIGINT,
    deleted      BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT pk_ar_registered_apps PRIMARY KEY (id),
    -- 普通唯一约束（非 partial）：软删行也占名额 = app_code 全局不可复用（ADR-0001 §6）。
    -- 撞名主路径由应用层 native query 先查（看含软删全行）+ DomainException(409)；
    -- 本约束作并发兜底（DuplicateKeyException → 409）。
    CONSTRAINT uk_ar_registered_apps_app_code UNIQUE (app_code)
);

COMMENT ON TABLE  ar_registered_apps IS '应用登记（RegisteredApp 聚合根）';
COMMENT ON COLUMN ar_registered_apps.app_code IS '稳定公开 slug，创建时填写、不可修改（应用级稳定身份，与凭证解耦）';
COMMENT ON COLUMN ar_registered_apps.status    IS '1=ACTIVE 启用, 0=DISABLED 禁用';

-- ============================================================
-- ApiKey 聚合——机机验签凭证，供 cartisan-openapi HMAC-SHA256 验签。
-- 1:1 挂在 ar_registered_apps 上、可空（应用可不持签名凭证）。详见 ADR-0002。
-- ============================================================
CREATE TABLE ar_api_keys (
    id           BIGINT       NOT NULL,
    app_id       BIGINT       NOT NULL,
    api_key      VARCHAR(128) NOT NULL,
    api_secret   VARCHAR(512) NOT NULL,
    status       INTEGER      NOT NULL DEFAULT 1,

    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    created_by   BIGINT,
    updated_by   BIGINT,
    deleted      BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT pk_ar_api_keys PRIMARY KEY (id),
    -- api_key 全局唯一（含软删行）：普通唯一约束，软删行也占名额 = 不可复用（ADR-0002 §6）。
    -- 撞名主路径由应用层 native query 先查（看含软删全行）+ DomainException(409)；
    -- 本约束作并发兜底（DuplicateKeyException → 409）。
    CONSTRAINT uk_ar_api_keys_api_key UNIQUE (api_key),
    -- app_id 1:1：部分唯一索引（仅活跃行），软删的旧 key 不阻塞新 key（create-or-rotate 的并发兜底）。
    CONSTRAINT fk_ar_api_keys_app FOREIGN KEY (app_id) REFERENCES ar_registered_apps (id)
);

CREATE UNIQUE INDEX uk_ar_api_keys_app_active ON ar_api_keys (app_id) WHERE deleted = false;

COMMENT ON TABLE  ar_api_keys IS 'ApiKey 聚合——机机验签凭证';
COMMENT ON COLUMN ar_api_keys.app_id     IS '所属应用（FK → ar_registered_apps.id）';
COMMENT ON COLUMN ar_api_keys.api_key    IS '凭证标识，SecureRandom 生成，全局唯一（含软删）；= 框架 X-App-Id / callerAppId';
COMMENT ON COLUMN ar_api_keys.api_secret IS 'AES-GCM 密文（主密钥来自环境变量）；内存解密得明文供 HMAC 验签';
COMMENT ON COLUMN ar_api_keys.status     IS '1=ACTIVE 启用, 0=DISABLED 禁用';

-- ============================================================
-- SsoClient 聚合——OIDC client 元数据，供 identity（IdP）消费。
-- 1:1 挂在 ar_registered_apps 上、可空（应用可不持 SSO client）。详见 ADR-0003。
-- 存储与 ApiKey（ar_api_keys）相反：client_secret = argon2 hash-only（不可逆，永不返回明文）。
-- ============================================================
CREATE TABLE ar_sso_clients (
    id             BIGINT        NOT NULL,
    app_id         BIGINT        NOT NULL,
    client_id      VARCHAR(128)  NOT NULL,
    client_secret  VARCHAR(1024) NOT NULL,
    redirect_uris  JSONB         NOT NULL,
    scopes         JSONB         NOT NULL,
    grants         JSONB         NOT NULL,
    status         INTEGER       NOT NULL DEFAULT 1,

    created_at     TIMESTAMP     NOT NULL,
    updated_at     TIMESTAMP     NOT NULL,
    created_by     BIGINT,
    updated_by     BIGINT,
    deleted        BOOLEAN       NOT NULL DEFAULT FALSE,

    CONSTRAINT pk_ar_sso_clients PRIMARY KEY (id),
    -- client_id 全局唯一（含软删行）：普通唯一约束，软删行也占名额 = 不可复用（ADR-0003 §6）。
    -- 撞名主路径由应用层 native query 先查（看含软删全行）+ DomainException(409)；
    -- 本约束作并发兜底（DuplicateKeyException → 409）。
    CONSTRAINT uk_ar_sso_clients_client_id UNIQUE (client_id),
    CONSTRAINT fk_ar_sso_clients_app FOREIGN KEY (app_id) REFERENCES ar_registered_apps (id)
);

-- app_id 1:1：部分唯一索引（仅活跃行），软删的旧 client 不阻塞新 client（create-or-rotate 的并发兜底）。
CREATE UNIQUE INDEX uk_ar_sso_clients_app_active ON ar_sso_clients (app_id) WHERE deleted = false;

COMMENT ON TABLE  ar_sso_clients IS 'SsoClient 聚合——OIDC client 元数据';
COMMENT ON COLUMN ar_sso_clients.app_id        IS '所属应用（FK → ar_registered_apps.id）';
COMMENT ON COLUMN ar_sso_clients.client_id     IS 'OIDC client_id，SecureRandom 生成，全局唯一（含软删）；可完全轮换';
COMMENT ON COLUMN ar_sso_clients.client_secret IS 'client_secret 的 argon2 hash（hash-only，不可逆，永不返回明文）';
COMMENT ON COLUMN ar_sso_clients.redirect_uris IS '回调地址列表（jsonb array），OIDC redirect_uris';
COMMENT ON COLUMN ar_sso_clients.scopes        IS '授权范围集合（jsonb array），去重';
COMMENT ON COLUMN ar_sso_clients.grants        IS '授权类型集合（jsonb array），去重';
COMMENT ON COLUMN ar_sso_clients.status        IS '1=ACTIVE 启用, 0=DISABLED 禁用';
