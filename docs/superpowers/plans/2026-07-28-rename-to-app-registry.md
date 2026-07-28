# 项目改名 aieducenter-openapi → aieducenter-app-registry 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将服务名/包名从 `aieducenter-openapi` / `com.aieducenter.aieducenteropenapi` 改为 `aieducenter-app-registry` / `com.aieducenter.appregistry`，代码与文档先对齐，运维/DB/域名状态保持不变。

**Architecture:** 纯机械式重命名重构，无行为变更。分四块：Java 包目录与声明、Maven 坐标、Spring/文档配置、构建部署脚本。回归保护 = 现有 `contextLoads` 冒烟测试与 `mvn compile` 保持绿色。

**Tech Stack:** Java 21（--enable-preview）、Spring Boot 3.4、Maven、cartisan-boot、Flyway、macOS（BSD sed `-i ''`）。

## Global Constraints

- **改**：package `com.aieducenter.aieducenteropenapi` → `com.aieducenter.appregistry`；artifactId `aieducenter-openapi` → `aieducenter-app-registry`；主类 `AieducenterOpenapiApplication` → `AppRegistryApplication`；`spring.application.name` → `aieducenter-app-registry`；CLAUDE.md 标题；jar 文件名 `aieducenter-openapi-1.0.0-SNAPSHOT.jar` → `aieducenter-app-registry-1.0.0-SNAPSHOT.jar`（跟随 artifactId，必改，否则构建/部署断）。
- **保留不变（用户决策 Tier 1）**：Flyway 历史表名 `aieducenter-openapi_flyway_schema_history`（application.yml:11）、Docker `container_name: aieducenter-openapi`（docker-compose.prod.yml:4）、服务器路径 `SERVER_PATH="/opt/hcy/aieducenter-openapi"`（publish.sh:7）、外部域名 `https://openapi.aieducenter.com` 与 `apikey-service-url`（application.yml:29）、`@BoundedContext(name = "OpenApi")` 与 `OpenApiMessage`（领域概念名，非项目名）、groupId `com.aieducenter`、`com.cartisan.*`。
- **关键纪律**：禁止对裸串 `aieducenter-openapi` 做全局 sed——会误伤上表"保留"项。包名/类名/jar-token 三种替换才允许全局，其余一律按文件定点 Edit。
- 非 git 仓库（`git rev-parse` 确认），故无 commit 步骤；每个任务以验证命令收尾。
- macOS 用 BSD sed，原地编辑写 `-i ''`。

## 标识符替换映射表（速查）

| 旧值 | 新值 | 替换方式 |
|------|------|----------|
| `com.aieducenter.aieducenteropenapi` | `com.aieducenter.appregistry` | 全局 sed（仅包名，安全）|
| `AieducenterOpenapiApplication` | `AppRegistryApplication` | 全局 sed（仅主类名，安全）|
| `aieducenter-openapi-1.0.0-SNAPSHOT.jar` | `aieducenter-app-registry-1.0.0-SNAPSHOT.jar` | 定点（3 个脚本文件）|
| `<artifactId>aieducenter-openapi</artifactId>` | `<artifactId>aieducenter-app-registry</artifactId>` | 定点（pom.xml）|
| `name: aieducenter-openapi` | `name: aieducenter-app-registry` | 定点（application.yml，勿碰 flyway.table 行）|
| `# aieducenter-openapi` | `# aieducenter-app-registry` | 定点（CLAUDE.md 标题）|

---

### Task 1: 移动 Java 包目录 + 改写 package/import 声明 + 重命名主类与测试类

**Files:**
- Move: `src/main/java/com/aieducenter/aieducenteropenapi/` → `src/main/java/com/aieducenter/appregistry/`
- Move: `src/test/java/com/aieducenter/aieducenteropenapi/` → `src/test/java/com/aieducenter/appregistry/`
- Rename: `.../appregistry/AieducenterOpenapiApplication.java` → `.../appregistry/AppRegistryApplication.java`
- Rename: `.../appregistry/AieducenterOpenapiApplicationTest.java` → `.../appregistry/AppRegistryApplicationTest.java`
- Modify（package/import 声明）: 上述目录下全部 14 个 main + 1 个 test Java 文件

**Interfaces:**
- Produces: 新包路径 `com.aieducenter.appregistry`；主类 `com.aieducenter.appregistry.AppRegistryApplication`。Task 2 的 pom mainClass/pitest、Task 5 的 Dockerfile/deploy.sh 依赖此结果。

- [ ] **Step 1: 移动 main 与 test 包目录**

```bash
cd /Users/zhangcolin/workspace/aieducenter-openapi
mv src/main/java/com/aieducenter/aieducenteropenapi src/main/java/com/aieducenter/appregistry
mv src/test/java/com/aieducenter/aieducenteropenapi src/test/java/com/aieducenter/appregistry
```

- [ ] **Step 2: 全局替换包名声明与 import（仅此 token，安全）**

```bash
find src -name "*.java" -exec sed -i '' 's/com\.aieducenter\.aieducenteropenapi/com.aieducenter.appregistry/g' {} +
```

- [ ] **Step 3: 全局替换主类名 token（含文件内容中的类名引用）**

```bash
find src -name "*.java" -exec sed -i '' 's/AieducenterOpenapiApplication/AppRegistryApplication/g' {} +
```

- [ ] **Step 4: 重命名两个物理文件**

```bash
mv src/main/java/com/aieducenter/appregistry/AieducenterOpenapiApplication.java \
   src/main/java/com/aieducenter/appregistry/AppRegistryApplication.java
mv src/test/java/com/aieducenter/appregistry/AieducenterOpenapiApplicationTest.java \
   src/test/java/com/aieducenter/appregistry/AppRegistryApplicationTest.java
```

- [ ] **Step 5: 验证目录/文件落地、无残留旧名**

```bash
cd /Users/zhangcolin/workspace/aieducenter-openapi
# 旧包目录应不存在
test ! -d src/main/java/com/aieducenter/aieducenteropenapi && echo "OK: main old dir gone"
test ! -d src/test/java/com/aieducenter/aieducenteropenapi && echo "OK: test old dir gone"
# Java 源里不应再有任何旧 token
test -z "$(grep -rn 'aieducenteropenapi\|AieducenterOpenapiApplication' src/)" && echo "OK: no old tokens in src"
# 新主类文件存在
test -f src/main/java/com/aieducenter/appregistry/AppRegistryApplication.java && echo "OK: main class renamed"
```
Expected: 四行 `OK:` 全部输出。

---

### Task 2: 更新 pom.xml（artifactId / mainClass / pitest targetClasses）

**Files:**
- Modify: `pom.xml:15`（artifactId）
- Modify: `pom.xml:158`（spring-boot mainClass）
- Modify: `pom.xml:181`（pitest targetClasses）

> 注意：`pom.xml` 中无 flyway/container/path，但为审慎仍按定点 Edit，不做裸串 sed。

**Interfaces:**
- Produces: artifactId `aieducenter-app-registry` → 决定打包出的 jar 名 `aieducenter-app-registry-1.0.0-SNAPSHOT.jar`（Task 5 依赖）；mainClass 指向 Task 1 的新类。

- [ ] **Step 1: 改 artifactId**

定点替换 `pom.xml:15`：
- 旧：`    <artifactId>aieducenter-openapi</artifactId>`
- 新：`    <artifactId>aieducenter-app-registry</artifactId>`

- [ ] **Step 2: 改 spring-boot 插件 mainClass**

定点替换 `pom.xml:158`：
- 旧：`<mainClass>com.aieducenter.aieducenteropenapi.AieducenterOpenapiApplication</mainClass>`
- 新：`<mainClass>com.aieducenter.appregistry.AppRegistryApplication</mainClass>`

- [ ] **Step 3: 改 pitest targetClasses**

定点替换 `pom.xml:181`：
- 旧：`<param>com.aieducenter.aieducenteropenapi.*</param>`
- 新：`<param>com.aieducenter.appregistry.*</param>`

- [ ] **Step 4: 验证 pom 无旧名残留**

```bash
cd /Users/zhangcolin/workspace/aieducenter-openapi
test -z "$(grep -n 'aieducenteropenapi\|AieducenterOpenapiApplication\|aieducenter-openapi' pom.xml)" && echo "OK: pom clean"
```
Expected: `OK: pom clean`

- [ ] **Step 5: 编译验证（关键回归门）**

```bash
cd /Users/zhangcolin/workspace/aieducenter-openapi
mvn -q clean compile
```
Expected: BUILD SUCCESS。这是改名正确性的主门：所有 package/import/类名解析通过。

- [ ] **Step 6: 确认编译产物落在新包路径**

```bash
cd /Users/zhangcolin/workspace/aieducenter-openapi
test -f target/classes/com/aieducenter/appregistry/AppRegistryApplication.class && echo "OK: compiled to new pkg"
test -z "$(find target/classes -path '*aieducenteropenapi*')" && echo "OK: no old-pkg classes"
```
Expected: 两行 `OK:`。

---

### Task 3: 更新 application.yml（仅 spring.application.name）与 CLAUDE.md 标题

**Files:**
- Modify: `src/main/resources/application.yml:6`（spring.application.name）
- Modify: `CLAUDE.md:1`（标题）
- **勿碰**：`application.yml:11`（flyway.table，保留）、`application.yml:29`（apikey-service-url 域名，保留）

- [ ] **Step 1: 改 spring.application.name**

定点替换 `application.yml:6`（带上下文，唯一）：
- 旧：
```yaml
spring:
  application:
    name: aieducenter-openapi
```
- 新：
```yaml
spring:
  application:
    name: aieducenter-app-registry
```

- [ ] **Step 2: 改 CLAUDE.md 标题**

定点替换 `CLAUDE.md:1`：
- 旧：`# aieducenter-openapi`
- 新：`# aieducenter-app-registry`

- [ ] **Step 3: 验证保留项未被误改**

```bash
cd /Users/zhangcolin/workspace/aieducenter-openapi
grep -q 'table: aieducenter-openapi_flyway_schema_history' src/main/resources/application.yml && echo "OK: flyway table kept"
grep -q 'apikey-service-url: https://openapi.aieducenter.com' src/main/resources/application.yml && echo "OK: domain kept"
grep -q 'name: aieducenter-app-registry' src/main/resources/application.yml && echo "OK: app name updated"
grep -q '^# aieducenter-app-registry' CLAUDE.md && echo "OK: claude title updated"
```
Expected: 四行 `OK:`。

---

### Task 4: 更新构建/部署脚本的 jar 文件名引用

**Files:**
- Modify: `Dockerfile:7`
- Modify: `deploy.sh:22`, `deploy.sh:23`
- Modify: `publish.sh:30`, `publish.sh:31`, `publish.sh:47`
- **勿碰**：`publish.sh:7`（SERVER_PATH `/opt/hcy/aieducenter-openapi`，保留）、`docker-compose.prod.yml:4`（container_name，保留）

> jar token `aieducenter-openapi-1.0.0-SNAPSHOT.jar` 全局唯一，与 SERVER_PATH/container_name 不同串，可安全定点替换。

- [ ] **Step 1: 替换三个脚本里的 jar token**

```bash
cd /Users/zhangcolin/workspace/aieducenter-openapi
sed -i '' 's/aieducenter-openapi-1\.0\.0-SNAPSHOT\.jar/aieducenter-app-registry-1.0.0-SNAPSHOT.jar/g' Dockerfile deploy.sh publish.sh
```

- [ ] **Step 2: 验证 jar 名已更新、保留项未动**

```bash
cd /Users/zhangcolin/workspace/aieducenter-openapi
test -z "$(grep -n 'aieducenter-openapi-1.0.0-SNAPSHOT.jar' Dockerfile deploy.sh publish.sh)" && echo "OK: old jar name gone"
grep -q 'aieducenter-app-registry-1.0.0-SNAPSHOT.jar' Dockerfile && echo "OK: Dockerfile new jar"
grep -q 'SERVER_PATH="/opt/hcy/aieducenter-openapi"' publish.sh && echo "OK: server path kept"
grep -q 'container_name: aieducenter-openapi' docker-compose.prod.yml && echo "OK: container name kept"
```
Expected: 四行 `OK:`。

---

### Task 5: 全量验证 + 清理误编译产物

**Files:** 无源码改动；运行验证命令、清理根目录误编译的 `/com` 目录（gitignored `*.class`，历史误产物）。

- [ ] **Step 1: 清理并重新编译**

```bash
cd /Users/zhangcolin/workspace/aieducenter-openapi
mvn -q clean compile
```
Expected: BUILD SUCCESS。

- [ ] **Step 2: 全仓最终扫描——除"保留项"外不应再有旧名**

```bash
cd /Users/zhangcolin/workspace/aieducenter-openapi
echo "--- 应改的旧 token（期望无输出）---"
grep -rnE 'aieducenteropenapi|AieducenterOpenapiApplication' src/ pom.xml CLAUDE.md Dockerfile deploy.sh publish.sh 2>/dev/null
grep -rn 'aieducenter-openapi-1.0.0-SNAPSHOT.jar' Dockerfile deploy.sh publish.sh 2>/dev/null
grep -n '<artifactId>aieducenter-openapi</artifactId>' pom.xml 2>/dev/null
echo "--- 仅这些位置的 aieducenter-openapi 是有意保留（期望命中）---"
grep -n 'aieducenter-openapi_flyway_schema_history' src/main/resources/application.yml
grep -n 'container_name: aieducenter-openapi' docker-compose.prod.yml
grep -n 'SERVER_PATH="/opt/hcy/aieducenter-openapi"' publish.sh
```
Expected: "应改"三段无输出；"保留"三段各命中 1 行。

- [ ] **Step 3: 单元测试（需本地 Postgres+Redis；否则记为环境原因）**

```bash
cd /Users/zhangcolin/workspace/aieducenter-openapi
mvn -q test
```
Expected: BUILD SUCCESS、`contextLoads` 通过。
> 说明：`@SpringBootTest` 默认 local profile，需 `localhost:5432/aieducenter`（user aiedu/pass dev123）+ Redis `localhost:6379`。若失败仅为 DB/Redis 连接（非 ClassNotFoundException / package 错误），属环境问题，非改名引入。判读重点：无类/包解析错误。

- [ ] **Step 4（可选）: 清理根目录误编译的 /com 目录**

```bash
cd /Users/zhangcolin/workspace/aieducenter-openapi
# 根目录 /com 仅含历史误编译的 cartisan .class（已 gitignore），确认后删除
ls com/ 2>/dev/null && rm -rf com && echo "OK: stray /com removed" || echo "skip: no /com dir"
```

- [ ] **Step 5: 交付确认清单**

逐项核对：
- [ ] `mvn clean compile` 绿
- [ ] 编译产物在 `target/classes/com/aieducenter/appregistry/`
- [ ] `spring.application.name: aieducenter-app-registry`
- [ ] artifactId `aieducenter-app-registry`
- [ ] CLAUDE.md 标题 `# aieducenter-app-registry`
- [ ] Flyway 表名 / container_name / SERVER_PATH / openapi.aieducenter.com 域名 均未变
- [ ] （若 DB/Redis 可用）`mvn test` 绿

---

## 范围外（本次不动，留待后续）

- ~~**仓库目录本身** `/Users/zhangcolin/workspace/aieducenter-openapi` 的 OS 级改名~~：**已于 2026-07-28 执行**，`mv` 为 `/Users/zhangcolin/workspace/aieducenter-app-registry`（同文件系统即时重命名，内容未变，15 个 java 文件齐全）。需在新路径重新打开 IDE。
- **Flyway 表名 / Docker 容器名 / 服务器部署路径 / 外部域名**：按用户 Tier 1 决策保留，待 DB/运维窗口期再追。
- **消费方仓库** 的 `apikey-service-url` 配置：不动，靠网关路由到新服务名。
