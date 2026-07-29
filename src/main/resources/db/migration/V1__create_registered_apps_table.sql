-- V1: 应用登记主表（RegisteredApp 聚合根）
-- 一个应用一行，持签名 facet（#4 ar_api_keys）与 SSO facet（#5 ar_sso_clients），以 app 内部 id 关联。
-- 详见 ADR-0001（应用聚合边界与标识模型）。
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
COMMENT ON COLUMN ar_registered_apps.app_code IS '稳定公开 slug，创建时填写、不可修改（facet 无关的应用级稳定身份）';
COMMENT ON COLUMN ar_registered_apps.status    IS '1=ACTIVE 启用, 0=DISABLED 禁用';
