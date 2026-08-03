package com.aieducenter.appregistry.endpoints.controller;

import com.aieducenter.appregistry.application.dto.command.CreateAppCommand;
import com.cartisan.test.base.ApiTestAssertions;
import com.cartisan.test.base.ApiTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
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
 * 应用登记端到端测试——MockMvc + 真实 PostgreSQL，@Transactional 每用例回滚。
 *
 * @since 0.1.0
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = NONE)
@ActiveProfiles("test")
@Transactional
class AppControllerTest extends ApiTestBase {

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    AppControllerTest(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void givenValidCommand_whenCreate_thenCreatedAndPersisted() throws Exception {
        String body = mvc.perform(post("/api/app-registry/apps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ApiTestAssertions.toJson(new CreateAppCommand("payment-service", "支付服务", "desc"))))
                .andExpect(status().isOk())
                .andExpect(ApiTestAssertions.assertOk())
                .andExpect(jsonPath("$.data.appCode").value("payment-service"))
                .andExpect(jsonPath("$.data.name").value("支付服务"))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.statusName").value("启用"))
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(body).path("data").path("id").asLong();
        assertThat(id).isNotNull();

        // DB 断言：行已落库（saveAndFlush 在同事务内 flush）
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ar_registered_apps WHERE id = ? AND app_code = ? AND deleted = false",
                Integer.class, id, "payment-service");
        assertThat(count).isEqualTo(1);
    }

    @Test
    void givenExistingApp_whenGetById_thenReturned() throws Exception {
        long id = createApp("payment-service");

        mvc.perform(get("/api/app-registry/apps/{id}", id))
                .andExpect(status().isOk())
                .andExpect(ApiTestAssertions.assertOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.appCode").value("payment-service"));
    }

    @Test
    void givenAppCodeTaken_whenCreate_then409() throws Exception {
        createApp("payment-service");

        mvc.perform(post("/api/app-registry/apps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ApiTestAssertions.toJson(new CreateAppCommand("payment-service", "另一个", null))))
                .andExpect(status().isConflict())
                .andExpect(ApiTestAssertions.assertError(409));
    }

    @Test
    void givenAppCodeTakenBySoftDeletedRow_whenCreate_then409() throws Exception {
        // 直接插入一行软删记录——native 撞名查询必须看到它（绕过 @SQLRestriction）
        jdbcTemplate.update(
                "INSERT INTO ar_registered_apps (id, app_code, name, status, created_at, updated_at, deleted) " +
                        "VALUES (?, ?, ?, ?, now(), now(), true)",
                99001L, "ghost-app", "幽灵", 1);

        mvc.perform(post("/api/app-registry/apps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ApiTestAssertions.toJson(new CreateAppCommand("ghost-app", "新幽灵", null))))
                .andExpect(status().isConflict())
                .andExpect(ApiTestAssertions.assertError(409));
    }

    @Test
    void givenInvalidAppCode_whenCreate_then400() throws Exception {
        mvc.perform(post("/api/app-registry/apps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ApiTestAssertions.toJson(new CreateAppCommand("Bad_Code!", "名", null))))
                .andExpect(status().isBadRequest())
                .andExpect(ApiTestAssertions.assertError(400));
    }

    @Test
    void givenActiveApp_whenDisableThenEnable_thenStatusTogglesAndAppCodeUnchanged() throws Exception {
        long id = createApp("payment-service");

        mvc.perform(put("/api/app-registry/apps/{id}/disable", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.statusName").value("禁用"))
                .andExpect(jsonPath("$.data.appCode").value("payment-service"));

        mvc.perform(put("/api/app-registry/apps/{id}/enable", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.appCode").value("payment-service"));
    }

    @Test
    void givenDisabledApp_whenDisableAgain_then409() throws Exception {
        long id = createApp("payment-service");

        mvc.perform(put("/api/app-registry/apps/{id}/disable", id))
                .andExpect(status().isOk());

        mvc.perform(put("/api/app-registry/apps/{id}/disable", id))
                .andExpect(status().isConflict())
                .andExpect(ApiTestAssertions.assertError(409));
    }

    @Test
    void givenAdminConsoleApp_whenDisable_then403() throws Exception {
        long id = createApp("admin-console");

        mvc.perform(put("/api/app-registry/apps/{id}/disable", id))
                .andExpect(status().isForbidden())
                .andExpect(ApiTestAssertions.assertError(403));
    }

    @Test
    void givenMissingApp_whenGet_then404() throws Exception {
        mvc.perform(get("/api/app-registry/apps/{id}", 88888888888L))
                .andExpect(status().isNotFound())
                .andExpect(ApiTestAssertions.assertError(404));
    }

    private long createApp(String appCode) throws Exception {
        String body = mvc.perform(post("/api/app-registry/apps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ApiTestAssertions.toJson(new CreateAppCommand(appCode, "n", null))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(body);
        return node.path("data").path("id").asLong();
    }
}
