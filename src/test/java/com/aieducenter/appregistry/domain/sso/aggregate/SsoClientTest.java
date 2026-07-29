package com.aieducenter.appregistry.domain.sso.aggregate;

import com.aieducenter.appregistry.domain.error.AppRegistryMessage;
import com.aieducenter.appregistry.domain.sso.enums.SsoClientStatus;
import com.cartisan.core.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SsoClient} 聚合根领域逻辑单元测试。
 *
 * @since 0.1.0
 */
class SsoClientTest {

    private static final long APP_ID = 1001L;
    private static final List<String> REDIRECT_URIS = List.of("https://a.example.com/cb", "https://b.example.com/cb");
    private static final Set<String> SCOPES = Set.of("openid", "profile");
    private static final Set<String> GRANTS = Set.of("authorization_code");

    @Test
    void givenValidInput_whenCreate_thenActiveWithIdAndFields() {
        SsoClient client = SsoClient.create(APP_ID, "cid_abc", "hash_xyz", REDIRECT_URIS, SCOPES, GRANTS);

        assertThat(client.getId()).isNotNull();
        assertThat(client.getAppId()).isEqualTo(APP_ID);
        assertThat(client.getClientId()).isEqualTo("cid_abc");
        assertThat(client.getClientSecret()).isEqualTo("hash_xyz");
        assertThat(client.getRedirectUris()).containsExactlyElementsOf(REDIRECT_URIS);
        assertThat(client.getScopes()).containsExactlyInAnyOrderElementsOf(SCOPES);
        assertThat(client.getGrants()).containsExactlyInAnyOrderElementsOf(GRANTS);
        assertThat(client.getStatus()).isEqualTo(SsoClientStatus.ACTIVE);
    }

    @Test
    void givenNullScopesAndGrants_whenCreate_thenEmptySetsNotNull() {
        SsoClient client = SsoClient.create(APP_ID, "cid_abc", "hash_xyz", REDIRECT_URIS, null, null);

        assertThat(client.getScopes()).isEmpty();
        assertThat(client.getGrants()).isEmpty();
    }

    @Test
    void givenEmptyRedirectUris_whenCreate_thenThrowsRequired() {
        assertThatThrownBy(() -> SsoClient.create(APP_ID, "cid_abc", "hash_xyz", List.of(), SCOPES, GRANTS))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCodeMessage())
                .isEqualTo(AppRegistryMessage.SSO_REDIRECT_URI_REQUIRED);
    }

    @Test
    void givenNullRedirectUris_whenCreate_thenThrowsRequired() {
        assertThatThrownBy(() -> SsoClient.create(APP_ID, "cid_abc", "hash_xyz", null, SCOPES, GRANTS))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCodeMessage())
                .isEqualTo(AppRegistryMessage.SSO_REDIRECT_URI_REQUIRED);
    }

    @Test
    void givenExistingClient_whenRotate_thenCredentialsAndMetadataChangeAndStatusActive() {
        SsoClient client = SsoClient.create(APP_ID, "cid_old", "hash_old", REDIRECT_URIS, SCOPES, GRANTS);
        client.disable();

        List<String> newUris = List.of("https://new.example.com/cb");
        Set<String> newScopes = Set.of("openid");
        client.rotate("cid_new", "hash_new", newUris, newScopes, null);

        assertThat(client.getClientId()).isEqualTo("cid_new");
        assertThat(client.getClientSecret()).isEqualTo("hash_new");
        assertThat(client.getRedirectUris()).containsExactlyElementsOf(newUris);
        assertThat(client.getScopes()).containsExactlyInAnyOrderElementsOf(newScopes);
        assertThat(client.getGrants()).isEmpty();
        assertThat(client.getStatus()).isEqualTo(SsoClientStatus.ACTIVE);
    }

    @Test
    void givenActiveClient_whenDisable_thenDisabled() {
        SsoClient client = SsoClient.create(APP_ID, "cid", "hash", REDIRECT_URIS, SCOPES, GRANTS);

        client.disable();

        assertThat(client.getStatus()).isEqualTo(SsoClientStatus.DISABLED);
    }

    @Test
    void givenDisabledClient_whenDisable_thenThrowsAlreadyDisabled() {
        SsoClient client = SsoClient.create(APP_ID, "cid", "hash", REDIRECT_URIS, SCOPES, GRANTS);
        client.disable();

        assertThatThrownBy(client::disable)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCodeMessage())
                .isEqualTo(AppRegistryMessage.SSO_CLIENT_ALREADY_DISABLED);
    }

    @Test
    void givenDisabledClient_whenEnable_thenActive() {
        SsoClient client = SsoClient.create(APP_ID, "cid", "hash", REDIRECT_URIS, SCOPES, GRANTS);
        client.disable();

        client.enable();

        assertThat(client.getStatus()).isEqualTo(SsoClientStatus.ACTIVE);
    }

    @Test
    void givenActiveClient_whenEnable_thenThrowsAlreadyEnabled() {
        SsoClient client = SsoClient.create(APP_ID, "cid", "hash", REDIRECT_URIS, SCOPES, GRANTS);

        assertThatThrownBy(client::enable)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCodeMessage())
                .isEqualTo(AppRegistryMessage.SSO_CLIENT_ALREADY_ENABLED);
    }
}
