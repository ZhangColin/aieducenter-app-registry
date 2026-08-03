package com.aieducenter.appregistry.endpoints.controller;

import com.aieducenter.appregistry.application.PlatformSeedAppService;
import com.aieducenter.appregistry.application.dto.command.CreateAppCommand;
import com.aieducenter.appregistry.domain.signature.port.ApiSecretEncrypter;
import com.cartisan.test.base.ApiTestAssertions;
import com.cartisan.test.base.ApiTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 签名 facet 端到端测试——MockMvc + 真实 PG，@Transactional 每用例回滚。
 *
 * @since 0.1.0
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = NONE)
@ActiveProfiles("test")
@Transactional
class ApiKeyControllerTest extends ApiTestBase {

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ApiSecretEncrypter encrypter;
    private final PlatformSeedAppService seedService;

    ApiKeyControllerTest(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate,
                         ApiSecretEncrypter encrypter, PlatformSeedAppService seedService) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.encrypter = encrypter;
        this.seedService = seedService;
    }

    @Test
    void givenAppWithoutFacet_whenCreate_thenReturnsPlaintextOnceAndStoresCiphertext() throws Exception {
        long appId = createApp("payment-service");

        String body = mvc.perform(post("/api/app-registry/apps/{appId}/api-keys", appId))
                .andExpect(status().isOk())
                .andExpect(ApiTestAssertions.assertOk())
                .andExpect(jsonPath("$.data.appId").value(appId))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.apiKey").isString())
                .andExpect(jsonPath("$.data.apiSecret").isString())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).path("data");
        String apiKey = data.path("apiKey").asText();
        String plaintextSecret = data.path("apiSecret").asText();
        assertThat(apiKey).isEqualTo("payment-service");
        assertThat(plaintextSecret).isNotBlank();
        assertThat(plaintextSecret).isNotEqualTo(apiKey);

        // DB 断言：存的是密文，非明文；且密文能解密回明文（端到端加密往返）
        String stored = jdbcTemplate.queryForObject(
                "SELECT api_secret FROM ar_api_keys WHERE app_id = ? AND deleted = false",
                String.class, appId);
        assertThat(stored).isNotEqualTo(plaintextSecret);
        assertThat(encrypter.decrypt(stored)).isEqualTo(plaintextSecret);
    }

    @Test
    void givenExistingFacet_whenGet_thenNoPlaintextSecret() throws Exception {
        long appId = createApp("payment-service");
        String apiKey = createKey(appId);

        mvc.perform(get("/api/app-registry/apps/{appId}/api-keys", appId))
                .andExpect(status().isOk())
                .andExpect(ApiTestAssertions.assertOk())
                .andExpect(jsonPath("$.data.apiKey").value(apiKey))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.apiSecret").doesNotExist());
    }

    @Test
    void givenActiveKey_whenBootstrap_thenReturnsPlaintextAndActive() throws Exception {
        long appId = createApp("payment-service");
        String[] created = createKeyWithSecret(appId);
        String apiKey = created[0];
        String plaintext = created[1];

        mvc.perform(get("/api/app-registry/api-keys/{apiKey}", apiKey))
                .andExpect(status().isOk())
                .andExpect(ApiTestAssertions.assertOk())
                .andExpect(jsonPath("$.data.appId").value(apiKey))
                .andExpect(jsonPath("$.data.appName").value("n"))
                .andExpect(jsonPath("$.data.apiSecret").value(plaintext))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void givenAppDisabled_whenBootstrap_thenStatusDisabledCascade() throws Exception {
        long appId = createApp("payment-service");
        String apiKey = createKey(appId);

        // 禁用 app → facet 组合状态应级联为 DISABLED
        mvc.perform(put("/api/app-registry/apps/{appId}/disable", appId))
                .andExpect(status().isOk());

        mvc.perform(get("/api/app-registry/api-keys/{apiKey}", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        // 重新启用 app → 恢复 ACTIVE
        mvc.perform(put("/api/app-registry/apps/{appId}/enable", appId))
                .andExpect(status().isOk());

        mvc.perform(get("/api/app-registry/api-keys/{apiKey}", apiKey))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void givenFacetDisabled_whenBootstrap_thenStatusDisabled() throws Exception {
        long appId = createApp("payment-service");
        String apiKey = createKey(appId);

        mvc.perform(put("/api/app-registry/apps/{appId}/api-keys/disable", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0));

        mvc.perform(get("/api/app-registry/api-keys/{apiKey}", apiKey))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
    }

    @Test
    void givenAdminConsole_whenDisableApiKey_then403() throws Exception {
        seedService.seed();
        long appId = jdbcTemplate.queryForObject(
                "SELECT id FROM ar_registered_apps WHERE app_code = 'admin-console' AND deleted = false",
                Long.class);

        mvc.perform(put("/api/app-registry/apps/{appId}/api-keys/disable", appId))
                .andExpect(status().isForbidden())
                .andExpect(ApiTestAssertions.assertError(403));
    }

    @Test
    void givenExistingFacet_whenRotate_thenSecretChangesAndApiKeyUnchanged() throws Exception {
        long appId = createApp("payment-service");
        String[] first = createKeyWithSecret(appId);
        String oldApiKey = first[0];
        String oldPlaintext = first[1];

        String body = mvc.perform(post("/api/app-registry/apps/{appId}/api-keys", appId))
                .andExpect(status().isOk())
                .andExpect(ApiTestAssertions.assertOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        String newApiKey = data.path("apiKey").asText();
        String newPlaintext = data.path("apiSecret").asText();

        // apiKey 不变（= appCode），secret 已换
        assertThat(newApiKey).isEqualTo(oldApiKey);
        assertThat(newPlaintext).isNotEqualTo(oldPlaintext);

        // apiKey 不变 → 仍可按原 apiKey 查询，返回新 secret
        mvc.perform(get("/api/app-registry/api-keys/{apiKey}", oldApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.apiSecret").value(newPlaintext));
    }

    @Test
    void givenMissingApp_whenCreate_then404() throws Exception {
        mvc.perform(post("/api/app-registry/apps/{appId}/api-keys", 77777777777L))
                .andExpect(status().isNotFound())
                .andExpect(ApiTestAssertions.assertError(404));
    }

    @Test
    void givenSeed_whenBootstrapAdminConsole_thenReturnsValidCredentials() throws Exception {
        seedService.seed();

        mvc.perform(get("/api/app-registry/api-keys/{apiKey}", "admin-console"))
                .andExpect(status().isOk())
                .andExpect(ApiTestAssertions.assertOk())
                .andExpect(jsonPath("$.data.appId").value("admin-console"))
                .andExpect(jsonPath("$.data.appName").value("管理后台"))
                .andExpect(jsonPath("$.data.apiSecret").isString())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void givenUnknownApiKey_whenBootstrap_then404() throws Exception {
        mvc.perform(get("/api/app-registry/api-keys/{apiKey}", "nonexistent-key"))
                .andExpect(status().isNotFound())
                .andExpect(ApiTestAssertions.assertError(404));
    }

    // ---- helpers ----

    private long createApp(String appCode) throws Exception {
        String body = mvc.perform(post("/api/app-registry/apps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ApiTestAssertions.toJson(new CreateAppCommand(appCode, "n", null))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private String createKey(long appId) throws Exception {
        return createKeyWithSecret(appId)[0];
    }

    private String[] createKeyWithSecret(long appId) throws Exception {
        String body = mvc.perform(post("/api/app-registry/apps/{appId}/api-keys", appId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        return new String[]{data.path("apiKey").asText(), data.path("apiSecret").asText()};
    }
}
