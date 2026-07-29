-- V2: 签名 facet（ApiKey 聚合）——供 cartisan-openapi 机机验签（HMAC-SHA256）。
-- 1:1 挂在 ar_registered_apps 上、可空（应用可不持签名 facet）。详见 ADR-0002。
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

COMMENT ON TABLE  ar_api_keys IS '签名 facet（ApiKey 聚合）——机机验签凭证';
COMMENT ON COLUMN ar_api_keys.app_id     IS '所属应用（FK → ar_registered_apps.id）';
COMMENT ON COLUMN ar_api_keys.api_key    IS '凭证标识，SecureRandom 生成，全局唯一（含软删）；= 框架 X-App-Id / callerAppId';
COMMENT ON COLUMN ar_api_keys.api_secret IS 'AES-GCM 密文（主密钥来自环境变量）；内存解密得明文供 HMAC 验签';
COMMENT ON COLUMN ar_api_keys.status     IS '1=ACTIVE 启用, 0=DISABLED 禁用';
