# ADR-0005：SsoClient 配置与凭证职责分离 + client_id 终身稳定

- **状态**：Accepted
- **日期**：2026-08-10
- **关联**：ADR-0003（SsoClient 聚合，§6 `client_id` 轮换性、§10 字段集）；identity ADR-0005（`post_logout` 独立白名单）；GitHub #18
- **修订**：ADR-0003 §6「`client_id` 可完全轮换」→ **`client_id` 终身稳定**

## 背景（Context）

ADR-0003 落地时管理流是单端点 `createOrRotate`：创建与轮换同一动作，**每次改配置（`redirect_uris`/`scopes`/`grants`）都连带重新生成 `client_id` + `client_secret`**。两个问题：

1. 配置频繁微调（加一个回调、调一个 scope）被迫连换凭证——`client_secret` 明文要重发、应用方要重配 OIDC client，代价与「改一行配置」严重不匹配。
2. `client_id` 连带轮换使 identity 按 `client_id` 缓存的条目失效、bootstrap URL `GET /sso-clients/{clientId}` 变化、下游写死 `client_id` 的配置全断。OIDC 惯例 `client_id` 是公开稳定标识、`client_secret` 才是密钥。

identity #39 要 `post_logout_redirect_uris` 新字段（独立登出回跳白名单），正好触发重构管理流。

## 决策（Decision）

1. **两个独立管理原语，调用端自由编排**（服务不强制顺序）：
   - **凭证接口**（生成 / 重置 `client_id`+`client_secret`）：首次调用 = **创建** SsoClient（SecureRandom 生成 `client_id` + 明文 `client_secret` → argon2 hash 入库）；再次调用 = **仅重置 `client_secret`**（`client_id` 不变）。明文 `client_secret` 仅此一次返回，之后任何接口不返。
   - **配置 PUT**（整份替换 `redirect_uris` / `post_logout_redirect_uris` / `scopes` / `grants`）：要求 SsoClient 已存在（`client_id` 已生成），不存在 → 404；**不动凭证、不动 status**。
2. **`client_id` 终身稳定**：创建时 SecureRandom 生成一次，**不再轮换**（修订 ADR-0003 §6「可完全轮换」）。轮换 = 只换 `client_secret`。
3. **原 `createOrRotate` 删除**：其「配置连带换凭证」语义正是本次消除对象，保留会自相矛盾。属**破坏性管理契约变更**。
4. **顺带加 `post_logout_redirect_uris` 列**（ADR-0003 §10 预见的「identity 建时真需要再加列」时刻）：JSONB array，OIDC RP-Initiated Logout 登出回跳白名单，与 `redirect_uris` 平级独立。
5. **不变式迁移**：`redirect_uris` 与 `post_logout_redirect_uris` 的「至少一个」校验从 `create()` 迁到**配置 PUT**；凭证接口不校验配置（允许「凭证已建、配置未 PUT」的中间态）。

## 结果（Consequences）

- 正向：identity 缓存键（`client_id`）稳定，不再因配置微调失效；配置与凭证生命周期解耦、各自独立演进；`post_logout_redirect_uris` 就位供 identity #39 消费。
- 代价：**破坏性管理契约变更**——`createOrRotate` 删除、调用端（管理后台 admin）须改调「凭证接口 + 配置 PUT」两步；需开 admin 联动 issue。
- 残余（已接受）：「凭证已建、配置未 PUT」的 SsoClient 会以 `active=true` + 空 `redirect_uris` 出现在 bootstrap GET——由调用端负责编排（先凭证后配置）、identity 自行 guard 空配置，app-registry 不特殊处理。

## 备选方案（Alternatives considered）

- **保留 `createOrRotate` + 加两个独立接口**：否。三接口并存更杂，且 `createOrRotate` 的「配置连带换凭证」正是要消除的语义，留着自相矛盾。
- **凭证轮换连 `client_id` 一起换**（保留 ADR-0003 §6 原貌）：否。`client_id` 嵌在 OIDC URL / 发现文档 / identity 缓存键，换它代价远大于换 `client_secret`；OIDC 约定 `client_id` 稳定。
- **`post_logout_redirect_uris` 可空**（对齐 OIDC 可选语义）：未选。本项目 SsoClient 一律要求支持 RP-Initiated Logout，故与 `redirect_uris` 同为「至少一个」。若日后出现不支持登出的 client 再放宽。
