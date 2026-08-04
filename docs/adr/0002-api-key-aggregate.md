# ADR-0002：ApiKey 聚合

- **状态**：Accepted
- **日期**：2026-07-29
- **关联**：ADR-0001（聚合边界与标识模型）；架构仓库 `app-registry.md`、`architecture.md` §4/§6.1；本项目 `CONTEXT.md`「ApiKey」「bootstrap 端点加固」

## 背景（Context）

ApiKey 供 `cartisan-openapi` 机机验签（HMAC-SHA256）：消费方应用持 `apiKey`/`apiSecret` 签自己的请求；provider 服务的 `RemoteApiKeyProvider`（Caffeine 30min + HTTP）调本服务 `GET /api/app-registry/api-keys/{apiKey}` 取 `ApiKeyInfo` 来验签。

约束：
- 架构不变式：`apiSecret` 必须**可取回**（消费方要明文做 HMAC）；`GET /api/app-registry/api-keys/{apiKey}` 只返 ApiKey；bootstrap 端点无验签、必须加固。
- `cartisan-openapi` 契约：`ApiKeyProvider.getByAppId(apiKey) → ApiKeyInfo(appId/appName/apiSecret/status)`（框架已移除 `permissions`——签名=纯认证）；`callerAppId` 锁死 = `X-App-Id` = `api_key`；框架禁改。
- 安全约束（用户）：**暂不引入 KMS/Vault**；**不过度设计**；主密钥用环境变量。
- 旧实现债：`ApiKey` 用 hutool `RandomUtil` 生成 `apiKey`（非密码学安全）、`api_secret` 明文存。

## 决策（Decision）

1. **聚合根 + 表 `ar_api_keys`**（1:1 on `RegisteredApp`，可空）：列 `id`(Tsid) / `app_id`(FK) / `api_key` / `api_secret` / `status`(ACTIVE/DISABLED) / 审计 + 软删。

2. **`api_secret` = AES-GCM 密文**：主密钥来自环境变量；DB 只存密文；`LocalApiKeyProvider` 在内存中解密得明文，组进 `ApiKeyInfo.apiSecret` 供验签。
   - 加密是**可逆**的（与 hash 不同）——这正是 ApiKey 的需要：验签要明文做 HMAC，故必须能取回。明文仅存在于内存 + 加密通道，不入库、不入日志。

3. **`api_key` 用 SecureRandom 生成**（修旧 hutool `RandomUtil` 不安全实现）；全局唯一（含软删行）；可完全轮换。

4. **不设 `api_secret_prev`**：零停机轮换重叠先不做（YAGNI，当前无零停机轮换需求）；真需要再加影子列（与 SsoClient `client_secret_prev_hash` 同款模式）。

5. **不设 `permissions` 列**：框架已判定 `ApiKeyInfo.permissions` 为臆想需求并硬删（见 cartisan-boot issue `apikey-info-permissions`）；签名 = 纯认证（已登记应用可调）。

6. **`api_key` 撞名**：native query 先查（看含软删全行）+ `throw DomainException`；DB 普通唯一约束（软删行也占名额 = 全局唯一不可复用）作并发兜底——框架现已把 `DuplicateKeyException → 409`，兜底路径不再返 500。

7. **`LocalApiKeyProvider`**（本服务实现 `ApiKeyProvider`）：直读 `ar_api_keys`（按 `api_key` 查、内存解密）、组 `ApiKeyInfo(appId=api_key, appName, apiSecret=明文, status)`，不自调 HTTP。`appId` 字段即 `api_key` 本身（回填 `callerAppId`）。

8. **bootstrap 端点 = `GET /api/app-registry/api-keys/{apiKey}`**：返 `ApiResponse.ok(ApiKeyInfo)`；路径变量与 `RemoteApiKeyProvider` 契约对齐；**只返 ApiKey，不返 SsoClient 字段**。

9. **加固 = 网络隔离（只对内可达）**：不做 bootstrap token / mTLS / IP allowlist。app-registry 部署为只对内可达（内网域名/IP + 防火墙）；调用方按"谁需要调"全是可信的自有 provider 服务，外部不知此服务；网络边界即信任边界，端点上再叠鉴权冗余。

## 结果（Consequences）

- 正向：ApiKey 与 SsoClient 解耦；`apiSecret` 可取回供验签、又非明文入库；与 `cartisan-openapi` 契约（4 字段 `ApiKeyInfo`）对齐；清掉旧明文 secret + 不安全随机两个安全债。
- 代价：多一个环境变量（AES 主密钥）；轮换非零停机（无 prev 列，需短暂双窗口或约定停机）。
- 残余风险（已接受）：`apiKey` 在 `X-App-Id` header 流转、相对 `apiSecret` 更暴露；若泄漏 + 端点被未授权触达，可换出 `apiSecret`。**由"只对内可达"网络边界 + 不公开端点**控制；暂不引入 KMS/Vault/envelope 加密。

## 备选方案（Alternatives considered）

- **`apiSecret` 明文存**（旧实现）：否。DB 落库即泄露面，违背"secret 不明文存"基本卫生。
- **`apiSecret` hash 存**：否。验签要明文做 HMAC，hash 不可逆，无法取回——与 ApiKey 的"可取回"不变式冲突（这是 SsoClient 才用 hash 的原因）。
- **`api_secret_prev` 影子列（零停机轮换）**：未选。当前无零停机轮换需求，YAGNI；留作未来。
- **`permissions` 列（per-key ACL）**：否。框架已判定臆想、已硬删；签名=纯认证。
- **bootstrap token / mTLS 加固 bootstrap 端点**：否。app-registry 只对内可达、外部不知此服务，网络边界已是强制信任边界；端点再叠鉴权是冗余（与用户"不过度设计"约束一致）。
- **KMS / Vault 托管主密钥**：未选。平台暂不引入；环境变量主密钥对当前威胁模型够用，留作未来。

## 开放问题（待后续 ADR）

- AES-GCM 主密钥的环境变量名、轮换流程（主密钥换导致存量密文失效，需重加密迁移或双密钥窗口）。
- `api_key` 的具体格式/长度（32-char Base64URL？）。
- 凭证轮换的管理端点契约（update `api_key` + `api_secret`）。
