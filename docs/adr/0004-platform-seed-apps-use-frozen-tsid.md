# ADR-0004：平台 seed 应用的 id 用冻结框架 TSID

- **状态**：Accepted
- **日期**：2026-08-10
- **关联**：ADR-0001（标识谱系：`id` 内部主键不外暴露、`app_code` 稳定引用）、`CONTEXT.md`「应用注册」；迁移 `V2__seed_identity_app.sql` / `V3__seed_payment_app.sql`；`PlatformSeedAppService`（admin-console，对照）

## 背景（Context）

app-registry 通过 Flyway SQL 迁移 seed 平台一方应用（identity、payment、……）到 `ar_registered_apps`。

框架的分布式 id 是 **TSID**（`com.cartisan.data.jpa.id.TsidGenerator`，42 位时间戳 + 22 位随机），且**只在 Java 运行时生成**——实体在构造器里 `this.id = TsidGenerator.newInstance().generate()`（"出生即有"），数据库侧没有 TSID 函数、没有 sequence、列也没有 DEFAULT。因此 **Flyway SQL 无法在迁移执行时调用框架生成 TSID**。

identity 原本（V2）用保留小字面量 `id=100` seed，理由是"TsidGenerator 产出的 id 量级远大于此、不会冲突"。维护者要求平台 seed 的 id 是**真正的框架 TSID**（不要 100/200 这种圆整数），让 seed 行与运行时创建的应用视觉一致、不像占位假数据；identity 也要一并统一。

约束：seed 行的 `app_code` 是稳定引用键（ADR-0001），`id` 仅内部主键、不应被测试或下游硬编码引用。

## 决策（Decision）

1. **平台 seed 行的 `id` 用冻结的框架 TSID**——在写迁移脚本时用 `TsidGenerator.newInstance().generate()` 一次性产出，作为 BIGINT 字面量嵌进 Flyway SQL。跨所有环境是同一个数（对 seed 行是优点：稳定、幂等）。
2. **identity（V2）从保留小字面量 `100` 迁到冻结 TSID；payment（V3）沿用同一约定。** V2/V3 注释互相引用，写明"统一策略"。identity 的 `id=100` 是历史例外，由本 ADR 收口。
3. **"冻结"是字面量，因为 Flyway 无法在运行时生成 TSID**（框架只在 Java 侧生成）。"框架 TSID"在 seed 里 = 脚本编写时冻结的字面量，非运行时生成——这是 id 用字面量的根本原因。
4. **范围仅限 Flyway seed 的平台应用（identity、payment）。** 不改 `admin-console`：它由 Java `PlatformSeedAppService`（`ApplicationRunner`）在启动时 seed，用的是真正的运行时 TSID + 完整 ApiKey 加密/轮换自愈——另一套刻意的模式（首启即需可用 ApiKey）。两套 seed 机制并存是有意的：identity/payment 只需登记行（ApiKey 事后运维 createOrRotate），admin-console 需首启可用凭证。

## 结果（Consequences）

- 正向：seed id 是货真价实的框架 TSID，与运行时应用同算法/同量级、视觉一致；跨环境确定性（同一字面量），seed 幂等。
- 代价：冻结字面量内嵌"脚本编写时"的时间戳，非每环境重新生成（对 seed 是特性，非缺陷）。
- 与运行时 TSID 碰撞概率可忽略（同毫秒 + 同 22 位随机 ≈ 1/4M）。
- **操作锋利边**：改已 apply 的 Flyway 迁移会变 checksum——已 apply 旧 V2（`id=100`）的环境须 `flyway repair` + 重建 schema 或删行重插（`ON CONFLICT (app_code) DO NOTHING` 不会自动改既有行 id）；fresh 环境直接得 TSID。Greenfield（本服务 V2 刚提交）无此问题。
- **测试纪律**：seed 的 `id` 不应被测试/下游硬编码引用；引用 seed 应用走 `app_code`（ADR-0001 稳定身份）。此外，Flyway seed 的 `app_code`（如 `payment`）可能与既有测试 fixture（如 keyword=`payment`）碰撞——测试应用不与平台 seed 碰撞的 fixture，或用基线计数对 seed 免疫（见 `AppControllerTest` 既有 `countApps()`/`countByStatus()` 模式）。

## 备选方案（Alternatives considered）

- **保留小字面量（100、200、…）靠量级隔离**：否。维护者要真正的框架 id、非圆整占位数；与运行时应用视觉不一致。
- **Java 运行时 seed（`ApplicationRunner`，如 admin-console）**：对 identity/payment 否。它们只需登记行（ApiKey 事后 createOrRotate），Java seed 服务更重、是另一套模式；Flyway SQL 才是这两者的真相源。
- **数据库侧生成（sequence / 函数 / 列 DEFAULT）**：否。框架在应用层分配 id；无 DB 侧 TSID 机制；引入它与框架可移植性/哲学冲突。
- **迁移运行时每环境生成新 TSID**：不可行——Flyway 无法调用 Java。

## 开放问题（待后续 ADR）

- 平台应用清单的治理（何时新增 seed、`app_code` 裸词 vs `-service` slug 的统一）——`CONTEXT.md` 示例 `payment-service` 与 identity/payment 裸词约定存在轻微张力，待清单增长时收口。
