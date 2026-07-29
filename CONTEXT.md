# aieducenter-app-registry

平台「应用/消费方」的**单一登记处**（基础能力层）——一个应用一行，持身份与多个 **facet**。由 `aieducenter-openapi` 服务**升级**而来（identity SSO 驱动：要登记「哪些应用能走 SSO」）。本文件是本服务的**术语表 + 设计决策**，不含实现细节。

> 架构权威在兄弟仓库 `../aieducenter-architecture/`（起步包 `docs/starters/app-registry.md`、`CONTEXT.md`「应用注册」条目、`architecture.md` §4/§6.1）。本文件是其在 app-registry 服务内的落地折射 + 本项目自己的演进决策（`docs/adr/`）。

## 稳定不变式（务必遵守）

1. **一个应用一行，持多 facet**：签名 facet + SSO facet，两 facet 独立、**可空**（非每个应用两面都要）。
2. **两类 secret 存储相反**：签名 `apiSecret` 必须**可取回**（加密存，消费方要明文验 HMAC）；SSO `client_secret` 必须 **hash-only**（像密码，IdP 本地比对、永不返回）。
3. **`ApiKeyInfo`/`ApiKeyProvider` 只服务签名 facet**：绝不把 OIDC 字段塞进 `cartisan-openapi` 框架；SSO facet 走独立端点、identity 直接消费。
4. **`/by-appId` 只返签名 facet**：不返 SSO 字段。
5. **bootstrap 端点无验签，必须加固**：`/by-appId` 与 SSO 端点都无验签（鸡生蛋）→ mTLS / 内网 only / bootstrap token / IP allowlist。
6. **本服务不做 OIDC token 签发/登录流**（identity 做）；只持 SSO client **元数据**。

## 术语表（Ubiquitous Language）

_以下为架构层已敲定的术语。项目级新术语访谈中沉淀、追加于后。_

**应用 (App / RegisteredApp)**:
平台「应用/消费方」的登记实体——一个应用一行。是签名 facet 与 SSO facet 的共同宿主。聚合根类名 `RegisteredApp`（避开 Spring `Application` 撞名）；表 `ar_registered_apps`。
_Avoid_: 把签名 facet 的凭证载体当作应用本身。

**facet（面）**:
一个应用登记项上挂的身份切片。当前两类：**签名 facet** + **SSO facet**。两 facet 独立、可空。
_Avoid_: 把 facet 叫"模块/功能"。

**签名 facet (Signature Facet)**:
facet 之一：`apiKey` / `apiSecret`，供 `cartisan-openapi` 机机验签（HMAC-SHA256）。secret 必须**可取回**（加密存）。对应框架契约 `ApiKeyInfo(appId/appName/apiSecret/status)`（框架已移除 `permissions`——签名=纯认证）。
_Avoid_: 把 OIDC 字段塞进签名 facet。

**SSO facet (SSO Facet)**:
facet 之一：`client_id` / `client_secret` / `redirect_uri` / `scopes` / `grants`，供 IdP（identity）OIDC。`client_secret` 必须 **hash-only**（永不返回明文）。
_Avoid_: 把 `client_secret` 与签名 `apiSecret` 同列同逻辑存。

**消费方 (Consumer)**:
使用平台各域的应用（对内/对外）。app-registry 登记的就是消费方。
_Avoid_: 客户。

**bootstrap 端点 (Bootstrap Endpoint)**:
无验签的「源端点」——验签机制依赖它（鸡生蛋），故自身不能被验签挡，须 mTLS/内网/token/IP 加固。当前两个：`/by-appId`（签名 facet）、SSO facet 端点。

**callerAppId**:
横切上下文 `RequestContext(userId/tenantId/callerAppId)` 之一，标识「哪个应用在调用」。由 `cartisan-openapi` 的 `SignatureVerificationFilter` 验签后、从 `X-App-Id` header 灌入。

**LocalApiKeyProvider / RemoteApiKeyProvider**:
`ApiKeyProvider`（cartisan-openapi 端口/SPI）的两个实现。`Remote`：框架侧，Caffeine 30min 缓存 + HTTP 调 `/by-appId`，供**其它服务**拉取签名 facet。`Local`：本服务自己的实现，直读自己表、不自调 HTTP。

## 设计决策（访谈中沉淀）

### 聚合边界：三聚合根 / 三表（已定）

`RegisteredApp` / 签名 facet / SSO facet 各自是**独立聚合根**，三张表、以 app 内部 `id` 关联（**非**单表值对象）。

- 判据：两 facet 之间无跨边界不变式（独立轮换/启停、独立唯一性约束、独立被寻址）；塞进同一聚合是"大聚合"反模式。
- `Application` 是**治理锚点**（name/description/应用级状态），不能退化成只剩 name 的空壳。
- **app 禁用级联到 facet（已定）**：查询时 **join app 校验**（非领域事件）。`LocalApiKeyProvider` 按 `api_key` 查时本来就要走 app 拿 `appName`，顺带校验 app 状态——组 `ApiKeyInfo.status = (api_key.status == ACTIVE && app.status == ACTIVE) ? ACTIVE : DISABLED`（app 或 api_key 任一禁用 → 框架拒签）。SSO 端点同理（app / client 任一禁用 → 不返有效元数据）。无冗余状态、实时一致、不靠同事务强一致。
- 表：`ar_registered_apps`（app）+ 签名 facet 表 + SSO facet 表（下轮定，同 `ar_` 前缀、表名对应实体）。**Greenfield**：弃旧 V1–V3 与 `oas_api_keys`，新 V1 起建，无数据迁移。
- **基数（讨论中，未定）**：倾向两 facet 都 **1:1**。轮换重叠（零停机）用「当前 secret + 上一份 secret」影子列解决（与 SSO `client_secret_prev_hash` 同款），**不必上 1:N**。多 apiKey 的其它动机（多环境/子系统/多权限范围）通常更适合建模为**多个独立 app**。仅当出现「同一 app 必须同时持多个活跃签名凭证」的具体需求才放 1:N。
- **多 apiKey 的代价（若启用）**：`callerAppId`（= 具体那张 apiKey）将标识凭证而非应用，须引入 per-key 审计/限流/计量/吊销层——复杂度跳一档。
- **偏离架构原文**：架构 CONTEXT.md「一张 apps 表、两 facet 列可空」是内联设想；我们用三表（normalized）分离——若维持 1:1 则偏离很小，仅当放 1:N 时字面才显著不同。待定稿后回灌架构仓库。

### 标识谱系（部分待定）

| 标识 | 归属 | 性质 | 现状 |
|---|---|---|---|
| `id`（Tsid） | app | 内部主键 | 已定 |
| `api_key` | 签名 facet | 凭证标识，可轮换；= 框架 `X-App-Id` / `callerAppId` | 已定 |
| `client_id` | SSO facet | OIDC 凭证标识，可轮换 | 已定 |
| `app_code` | app | 稳定公开 slug，不轮换；跨域引用/归属/离线聚合键 | **已定**——创建时填写、**不可修改**；slug 格式（如 `payment-service`）。facet 无关的应用级稳定身份，使 `api_key`/`client_id` 可**完全轮换**而不丢应用身份 |

**框架约束**：`cartisan-openapi` 的 `callerAppId` 锁死 = `X-App-Id` = `api_key`（`LocalApiKeyProvider` 按它查）。`app_code` **无法顶替**它进 header（除非改框架，架构禁改）。故：热路径流 `api_key`（凭证级）；稳定应用归属靠 `app_code`——跨域引用/计量/日志聚合按 `app_code`，下游需应用级身份时解析 `api_key → app_code` 或直接以 `app_code` 落库。`api_key`/`client_id` 现可完全轮换 → `callerAppId` **不保证长期稳定**，需稳定归属者必须用 `app_code`。

### 签名 facet（已定主干）

聚合根 + 表 `ar_api_keys`（1:1 on app，可空——应用可不持签名 facet）：

| 列 | 性质 |
|---|---|
| `id`（Tsid） | 内部主键 |
| `app_id` | FK → `ar_registered_apps.id` |
| `api_key` | 凭证标识，**全局唯一（含软删行）**；**SecureRandom** 生成；可轮换；= 框架 `X-App-Id` / `callerAppId` |
| `api_secret` | **AES-GCM 密文**（主密钥来自环境变量）；内存中解密得明文供 HMAC 验签；可轮换 |
| `status` | `ACTIVE` / `DISABLED` |
| 审计 | `created_at`/`updated_at`/`created_by`/`updated_by` + `deleted`（软删） |

- **无 `api_secret_prev`**：零停机轮换重叠先不做（YAGNI）；真需要再加影子列（与 SSO `client_secret_prev_hash` 同款）。
- **无 `permissions` 列**：框架已移除 `ApiKeyInfo.permissions`，签名 = 纯认证（已登记应用可调）。
- **`api_key` 生成必须 SecureRandom**（修旧 `ApiKey` 用 hutool `RandomUtil` 的不安全实现）。
- **`api_key` 撞名检测**：复用 `app_code` 同款——native query 先查（看含软删全行）+ `throw DomainException`；DB 唯一约束作并发兜底（框架现已 `DuplicateKeyException → 409`）。
- **`LocalApiKeyProvider`**（本服务自己的 `ApiKeyProvider` 实现）：直读 `ar_api_keys`（按 `api_key` 查、join app、内存解密 `api_secret`），组 `ApiKeyInfo(appId=api_key, appName, apiSecret=明文, status)`，其中 `status = api_key.status && app.status` 联合（app 禁用则 facet 失效）；**不自调 HTTP**。
- **`/by-appId`**：`GET /api/app-registry/api-keys/{apiKey}`，返 `ApiResponse.ok(ApiKeyInfo)`；**只返签名 facet，不返 SSO 字段**。与 `RemoteApiKeyProvider` 的路径变量契约对齐。
- **加固**：网络隔离（见下）。

### SSO facet（已定主干）

聚合根 + 表 `ar_sso_clients`（1:1 on app，可空——应用可不持 SSO facet）：

| 列 | 性质 |
|---|---|
| `id`（Tsid） | 内部主键 |
| `app_id` | FK → `ar_registered_apps.id` |
| `client_id` | OIDC 凭证标识，**全局唯一（含软删行）**；SecureRandom 生成；可轮换 |
| `client_secret` | **hash-only**（argon2），**永不返回明文**；创建/轮换时生成明文 + hash，存 hash、**明文返应用方一次** |
| `redirect_uri` | **列表**（OIDC 允许多回调）；JSON 列 |
| `scopes` | Set；JSON 列 |
| `grants` | Set（authorization_code / refresh_token / client_credentials…）；JSON 列 |
| `status` + 审计/软删 | 同签名 facet |

- **边界（坐实不变式 6）**：app-registry **只持元数据 + 提供数据**，不做任何 OIDC 流程逻辑（授权 / token 签发 / client 认证比对 / 登录流——皆 identity 职责）。`client_secret` 的**生成**算登记动作（登记处发凭证），归 app-registry；**比对**算 SSO 流程，归 identity。
- **前置假设（hash-only ⟹ client 认证方式）**：`client_secret` hash-only → 排除 `client_secret_jwt`（需明文验签）→ 隐含 **`client_secret_post`**（应用发明文、identity 比对 hash）。这不是 app-registry 的新选择，是不变式的推论。若 SSO 项目最终坚持 `client_secret_jwt`，需回架构重谈"client_secret 永不返回明文"不变式（届时改可逆加密存，同签名 `apiSecret`）。
- **identity 消费契约**：`GET /api/app-registry/sso-clients/{clientId}` 返 client 元数据（**含 `client_secret` 的 hash**，不含明文），identity 拉取 + 缓存 + 本地比对。查询时 join app，**app / client 任一禁用 → 不返有效元数据**（与签名 facet 级联同款）。与签名 facet `/by-appId` 对称（拉取 + 缓存 + 本地验证），差异仅"返 hash 不返明文"（hash-only 不变式使然）。**不做比对端点**（比对是 SSO 职责）。
- **无 `client_secret_prev_hash`**：零停机轮换重叠先不做（YAGNI，与签名 facet 一致）。
- **`client_id` 撞名检测**：复用 `app_code`/`api_key` 同款——native query 先查（看含软删全行）+ `throw DomainException`；DB 唯一约束作并发兜底（`DuplicateKeyException → 409`）。
- **字段集先按 OIDC 标准最小集**：`client_name` 复用 `RegisteredApp.name`（不单存）；`token_endpoint_auth_method`（默认 `client_secret_post`）/ token TTL 等先不留——identity 建时真需要再加列（greenfield 加列成本低）。
- **加固**：网络隔离（同签名 facet，见下）。

### bootstrap 端点加固：网络隔离（已定）

不变式 5「bootstrap 端点必须加固」——本项目选定手段：**内网 only（网络隔离）**。

- app-registry 部署为**只对内可达**（内网域名/IP + 防火墙）；外部不知此服务、不触达其 bootstrap 端点。
- `/by-appId` 与 SSO 端点的调用方，按"谁需要调"全是**可信的自有 provider 服务**（外部消费方只拿 `apiKey`/`apiSecret` 签自己请求、不调 `/by-appId`；外部应用也不触达 SSO 端点）。
- **不做 bootstrap token / mTLS / IP allowlist**：app-registry 本身即安全服务，在其 bootstrap 端点上再叠鉴权是冗余；网络边界已是强制信任边界。
- 残余风险（`apiKey` 泄漏 → fetch `apiSecret`）已接受：`apiKey` 仅在可信服务间 + 加密通道流转，泄漏面可控；平台暂不引入 KMS/Vault。

_其余决议访谈中追加。重大决策同步落 `docs/adr/`。_

