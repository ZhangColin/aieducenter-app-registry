package com.aieducenter.appregistry.endpoints.controller;

import com.aieducenter.appregistry.application.dto.command.CreateAppCommand;
import com.aieducenter.appregistry.application.dto.command.CreateSsoClientCommand;
import com.aieducenter.appregistry.common.TestSignatureHelper;
import com.aieducenter.appregistry.domain.signature.port.ApiSecretEncrypter;
import com.cartisan.test.base.ApiTestAssertions;
import com.cartisan.test.base.ApiTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SSO facet 端到端测试——MockMvc + 真实 PG，@Transactional 每用例回滚。
 *
 * <p>注：{@code client_id} 撞名 409 走 SecureRandom 生成路径无法经 API 触发（碰撞概率可忽略），
 * 其应用层安全性由 {@code existsByClientId}（native，看含软删全行，同 #3 app_code / #4 api_key 同款已证）保证；
 * DB 兜底（普通唯一约束 → DuplicateKeyException → 409）在本类用 jdbcTemplate 实测（见
 * {@link #givenDuplicateClientIdRow_whenInsert_thenUniqueConstraintEnforced()}）。</p>
 *
 * @since 0.1.0
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = NONE)
@ActiveProfiles("test")
@Transactional
class SsoClientControllerTest extends ApiTestBase {

    private static final List<String> REDIRECT_URIS = List.of("https://a.example.com/cb", "https://b.example.com/cb");
    private static final Set<String> SCOPES = Set.of("openid", "profile");
    private static final Set<String> GRANTS = Set.of("authorization_code", "refresh_token");

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ApiSecretEncrypter encrypter;

    private TestSignatureHelper signer;

    SsoClientControllerTest(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate,
                            ApiSecretEncrypter encrypter) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.encrypter = encrypter;
    }

    @BeforeEach
    void setUpCaller() {
        signer = TestSignatureHelper.setupCaller(jdbcTemplate, encrypter);
    }

    @Test
    void givenAppWithoutFacet_whenCreate_thenReturnsPlaintextOnceAndStoresHash() throws Exception {
        long appId = createApp("sso-app");

        CreateSsoClientCommand command = new CreateSsoClientCommand(REDIRECT_URIS, SCOPES, GRANTS);
        String json = ApiTestAssertions.toJson(command);
        String body = mvc.perform(signer.sign(post("/api/app-registry/apps/{appId}/sso-clients", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json), json))
                .andExpect(status().isOk())
                .andExpect(ApiTestAssertions.assertOk())
                .andExpect(jsonPath("$.data.appId").value(appId))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.clientId").isString())
                .andExpect(jsonPath("$.data.clientSecret").isString())
                .andExpect(jsonPath("$.data.redirectUris.length()").value(2))
                .andExpect(jsonPath("$.data.scopes.length()").value(2))
                .andExpect(jsonPath("$.data.grants.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).path("data");
        String clientId = data.path("clientId").asText();
        String plaintextSecret = data.path("clientSecret").asText();
        assertThat(clientId).isNotBlank();
        assertThat(plaintextSecret).isNotBlank();
        assertThat(plaintextSecret).isNotEqualTo(clientId);

        // DB 断言：存的是 argon2 hash（非明文、hash-only），且 hash 能比对回明文（identity 侧同款验证）
        String stored = jdbcTemplate.queryForObject(
                "SELECT client_secret FROM ar_sso_clients WHERE app_id = ? AND deleted = false",
                String.class, appId);
        assertThat(stored).isNotEqualTo(plaintextSecret);
        assertThat(stored).startsWith("$argon2");
        Argon2PasswordEncoder verifier = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        assertThat(verifier.matches(plaintextSecret, stored)).isTrue();

        // JSON 列 DB 断言：jsonb 数组写入正确
        Integer uriCount = jdbcTemplate.queryForObject(
                "SELECT jsonb_array_length(redirect_uri) FROM ar_sso_clients WHERE app_id = ? AND deleted = false",
                Integer.class, appId);
        assertThat(uriCount).isEqualTo(2);
    }

    @Test
    void givenExistingFacet_whenGet_thenNoPlaintextSecret() throws Exception {
        long appId = createApp("sso-app");
        String clientId = createClient(appId);

        mvc.perform(signer.sign(get("/api/app-registry/apps/{appId}/sso-clients", appId), null))
                .andExpect(status().isOk())
                .andExpect(ApiTestAssertions.assertOk())
                .andExpect(jsonPath("$.data.clientId").value(clientId))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.redirectUris.length()").value(2))
                .andExpect(jsonPath("$.data.clientSecret").doesNotExist());
    }

    @Test
    void givenActiveClient_whenBootstrap_thenReturnsHashAndActiveNoPlaintext() throws Exception {
        long appId = createApp("sso-app");
        String[] created = createClientWithSecret(appId);
        String clientId = created[0];
        String plaintext = created[1];

        // SSO bootstrap 端点需要验签
        String body = mvc.perform(signer.sign(get("/api/app-registry/sso-clients/{clientId}", clientId), null))
                .andExpect(status().isOk())
                .andExpect(ApiTestAssertions.assertOk())
                .andExpect(jsonPath("$.data.clientId").value(clientId))
                .andExpect(jsonPath("$.data.clientName").value("n"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.redirectUris.length()").value(2))
                .andExpect(jsonPath("$.data.scopes.length()").value(2))
                .andExpect(jsonPath("$.data.grants.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        // bootstrap 返 hash、不返明文：hash ≠ 明文，且是 DB 里那份 argon2 hash
        JsonNode data = objectMapper.readTree(body).path("data");
        String hash = data.path("clientSecretHash").asText();
        assertThat(hash).isNotEqualTo(plaintext);
        assertThat(hash).startsWith("$argon2");
        String stored = jdbcTemplate.queryForObject(
                "SELECT client_secret FROM ar_sso_clients WHERE app_id = ? AND deleted = false",
                String.class, appId);
        assertThat(hash).isEqualTo(stored);
        // 响应里绝无明文字段
        assertThat(data.has("clientSecret")).isFalse();
    }

    @Test
    void givenAppDisabled_whenBootstrap_thenInactiveAndHashWithheld() throws Exception {
        long appId = createApp("sso-app");
        String clientId = createClient(appId);

        // 禁用 app → 组合状态 active=false，hash 被扣留（不返有效元数据）
        mvc.perform(signer.sign(put("/api/app-registry/apps/{appId}/disable", appId), null))
                .andExpect(status().isOk());

        mvc.perform(signer.sign(get("/api/app-registry/sso-clients/{clientId}", clientId), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.clientSecretHash").doesNotExist());

        // 重新启用 app → 恢复 active=true，hash 回来
        mvc.perform(signer.sign(put("/api/app-registry/apps/{appId}/enable", appId), null))
                .andExpect(status().isOk());

        mvc.perform(signer.sign(get("/api/app-registry/sso-clients/{clientId}", clientId), null))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.clientSecretHash").isString());
    }

    @Test
    void givenFacetDisabled_whenBootstrap_thenInactiveAndHashWithheld() throws Exception {
        long appId = createApp("sso-app");
        String clientId = createClient(appId);

        mvc.perform(signer.sign(put("/api/app-registry/apps/{appId}/sso-clients/disable", appId), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0));

        mvc.perform(signer.sign(get("/api/app-registry/sso-clients/{clientId}", clientId), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.clientSecretHash").doesNotExist());
    }

    @Test
    void givenExistingFacet_whenRotate_thenNewCredentialsAndMetadataAndOldClientIdGone() throws Exception {
        long appId = createApp("sso-app");
        String[] first = createClientWithSecret(appId);
        String oldClientId = first[0];
        String oldPlaintext = first[1];

        List<String> newUris = List.of("https://new.example.com/cb");
        CreateSsoClientCommand command = new CreateSsoClientCommand(newUris, Set.of("openid"), null);
        String json = ApiTestAssertions.toJson(command);
        String body = mvc.perform(signer.sign(post("/api/app-registry/apps/{appId}/sso-clients", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json), json))
                .andExpect(status().isOk())
                .andExpect(ApiTestAssertions.assertOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        String newClientId = data.path("clientId").asText();
        String newPlaintext = data.path("clientSecret").asText();

        assertThat(newClientId).isNotEqualTo(oldClientId);
        assertThat(newPlaintext).isNotEqualTo(oldPlaintext);
        assertThat(data.path("redirectUris").size()).isEqualTo(1);
        assertThat(data.path("grants").size()).isZero();

        // 旧 client_id 已轮换掉 → bootstrap 404
        mvc.perform(signer.sign(get("/api/app-registry/sso-clients/{clientId}", oldClientId), null))
                .andExpect(status().isNotFound());

        // 新 client_id 可用（active + hash 可比对新明文）
        mvc.perform(signer.sign(get("/api/app-registry/sso-clients/{clientId}", newClientId), null))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    void givenEmptyRedirectUris_whenCreate_then400() throws Exception {
        long appId = createApp("sso-app");

        CreateSsoClientCommand command = new CreateSsoClientCommand(List.of(), SCOPES, GRANTS);
        String json = ApiTestAssertions.toJson(command);
        mvc.perform(signer.sign(post("/api/app-registry/apps/{appId}/sso-clients", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json), json))
                .andExpect(status().isBadRequest())
                .andExpect(ApiTestAssertions.assertError(400));
    }

    @Test
    void givenMissingApp_whenCreate_then404() throws Exception {
        CreateSsoClientCommand command = new CreateSsoClientCommand(REDIRECT_URIS, SCOPES, GRANTS);
        String json = ApiTestAssertions.toJson(command);
        mvc.perform(signer.sign(post("/api/app-registry/apps/{appId}/sso-clients", 77777777777L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json), json))
                .andExpect(status().isNotFound())
                .andExpect(ApiTestAssertions.assertError(404));
    }

    @Test
    void givenAppWithoutFacet_whenGet_then404() throws Exception {
        long appId = createApp("sso-app");

        mvc.perform(signer.sign(get("/api/app-registry/apps/{appId}/sso-clients", appId), null))
                .andExpect(status().isNotFound())
                .andExpect(ApiTestAssertions.assertError(404));
    }

    @Test
    void givenUnknownClientId_whenBootstrap_then404() throws Exception {
        mvc.perform(signer.sign(get("/api/app-registry/sso-clients/{clientId}", "nonexistent-client"), null))
                .andExpect(status().isNotFound())
                .andExpect(ApiTestAssertions.assertError(404));
    }

    @Test
    void givenDisabledFacet_whenDisableAgain_then409() throws Exception {
        long appId = createApp("sso-app");
        createClient(appId);

        mvc.perform(signer.sign(put("/api/app-registry/apps/{appId}/sso-clients/disable", appId), null))
                .andExpect(status().isOk());
        mvc.perform(signer.sign(put("/api/app-registry/apps/{appId}/sso-clients/disable", appId), null))
                .andExpect(status().isConflict())
                .andExpect(ApiTestAssertions.assertError(409));

        mvc.perform(signer.sign(put("/api/app-registry/apps/{appId}/sso-clients/enable", appId), null))
                .andExpect(status().isOk());
        mvc.perform(signer.sign(put("/api/app-registry/apps/{appId}/sso-clients/enable", appId), null))
                .andExpect(status().isConflict())
                .andExpect(ApiTestAssertions.assertError(409));
    }

    @Test
    void givenDuplicateClientIdRow_whenInsert_thenUniqueConstraintEnforced() {
        // client_id 撞名的 DB 兜底：普通唯一约束 → DuplicateKeyException（全局异常 → 409）。
        // 应用层先查路径（existsByClientId native）同 #3 app_code / #4 api_key 同款已证。
        // 注：PG 事务内一次约束违例后整事务 aborted（25P02），故活跃/软删两场景分两个测试方法。
        insertFkTargetApp();
        insertSsoClientRow(900001, "taken-client-id", false);

        assertThatThrownBy(() -> insertSsoClientRow(900002, "taken-client-id", false))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void givenSoftDeletedClientIdRow_whenInsert_thenUniqueConstraintStillEnforced() {
        // 软删行也占名额：client_id 不可复用（同 app_code/api_key 语义，ADR-0003 §6）。
        insertFkTargetApp();
        insertSsoClientRow(900001, "taken-client-id", false);

        assertThatThrownBy(() -> insertSsoClientRow(900002, "taken-client-id", true))
                .isInstanceOf(DuplicateKeyException.class);
    }

    // ---- helpers ----

    private void insertFkTargetApp() {
        jdbcTemplate.update("INSERT INTO ar_registered_apps (id, app_code, name, status, created_at, updated_at, deleted) "
                + "VALUES (900001, 'fk-target', 'n', 1, now(), now(), false)");
    }

    private void insertSsoClientRow(long id, String clientId, boolean deleted) {
        jdbcTemplate.update("INSERT INTO ar_sso_clients (id, app_id, client_id, client_secret, redirect_uri, scopes, grants, "
                + "status, created_at, updated_at, deleted) VALUES (?, 900001, ?, 'hash', '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, "
                + "1, now(), now(), ?)", id, clientId, deleted);
    }

    private long createApp(String appCode) throws Exception {
        String json = ApiTestAssertions.toJson(new CreateAppCommand(appCode, "n", null));
        String body = mvc.perform(signer.sign(post("/api/app-registry/apps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json), json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private String createClient(long appId) throws Exception {
        return createClientWithSecret(appId)[0];
    }

    private String[] createClientWithSecret(long appId) throws Exception {
        CreateSsoClientCommand command = new CreateSsoClientCommand(REDIRECT_URIS, SCOPES, GRANTS);
        String json = ApiTestAssertions.toJson(command);
        String body = mvc.perform(signer.sign(post("/api/app-registry/apps/{appId}/sso-clients", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json), json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        return new String[]{data.path("clientId").asText(), data.path("clientSecret").asText()};
    }
}
