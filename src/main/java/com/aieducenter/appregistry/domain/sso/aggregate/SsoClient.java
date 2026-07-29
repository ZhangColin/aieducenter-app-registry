package com.aieducenter.appregistry.domain.sso.aggregate;

import com.aieducenter.appregistry.domain.error.AppRegistryMessage;
import com.aieducenter.appregistry.domain.sso.enums.SsoClientStatus;
import com.cartisan.core.domain.AggregateRoot;
import com.cartisan.core.stereotype.Aggregate;
import com.cartisan.core.util.Assertions;
import com.cartisan.data.jpa.domain.AuditableSoftDeletable;
import com.cartisan.data.jpa.id.TsidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * SSO facet 聚合根——OIDC client 元数据（供 identity IdP）。
 *
 * <p>1:1 挂在 {@code RegisteredApp} 上、可空。{@code client_id} = SecureRandom 生成、全局唯一、可完全轮换；
 * {@code client_secret} 以 <strong>argon2 hash</strong> 入库（hash-only，不可逆、永不返回明文）——
 * 与签名 facet 的 AES-GCM 可逆密文存储<strong>相反</strong>（ADR-0003 §2）。明文 {@code client_secret} 仅由
 * 应用层在创建/轮换时生成、哈希、返响应一次。</p>
 *
 * <p>{@code redirect_uri}（列表，保序）/ {@code scopes}（去重）/ {@code grants}（去重）以 JSONB 列存储，
 * 应用不多、不建关联表（ADR-0003 §8）。</p>
 *
 * <h3>状态机</h3>
 * <ul>
 *   <li>create → {@link SsoClientStatus#ACTIVE}；rotate → 重置 ACTIVE 并换新凭证 + 元数据。</li>
 *   <li>{@link #disable()} ACTIVE → DISABLED；{@link #enable()} DISABLED → ACTIVE。重复转换抛 409。</li>
 * </ul>
 *
 * <h3>组合生效</h3>
 * <p>本聚合 status 仅是 facet 自身状态；SSO 是否放行由 {@code SsoClient.status && RegisteredApp.status}
 * 联合决定（app 禁用则 facet 失效）——该级联在 {@code SsoClientAppService}/bootstrap 端点查询时 join app 计算，
 * 不在本聚合。</p>
 *
 * @since 0.1.0
 */
@Entity
@Table(name = "ar_sso_clients")
@Aggregate
// DATA-004：@SQLRestriction 在 @MappedSuperclass 上继承不可靠，实体类重复声明确保软删过滤生效。
@SQLRestriction("deleted = false")
@Getter
public class SsoClient extends AuditableSoftDeletable implements AggregateRoot<SsoClient, Long> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "app_id", nullable = false, updatable = false)
    private Long appId;

    @Column(name = "client_id", nullable = false, length = 128)
    private String clientId;

    /** argon2 hash（自描述字符串，含算法参数 + salt）。明文绝不落库。*/
    @Column(name = "client_secret", nullable = false, length = 1024)
    private String clientSecret;

    /** OIDC 回调地址（保序、可重复，jsonb array）。*/
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "redirect_uri", nullable = false)
    private List<String> redirectUris = new ArrayList<>();

    /** 授权范围（去重，jsonb array）。*/
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scopes", nullable = false)
    private Set<String> scopes = new LinkedHashSet<>();

    /** 授权类型（去重，jsonb array）。*/
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "grants", nullable = false)
    private Set<String> grants = new LinkedHashSet<>();

    @Column(name = "status", nullable = false)
    private SsoClientStatus status;

    protected SsoClient() {
    }

    private SsoClient(Long appId, String clientId, String hashedSecret,
                      List<String> redirectUris, Set<String> scopes, Set<String> grants) {
        this.id = TsidGenerator.newInstance().generate();
        this.appId = appId;
        this.clientId = clientId;
        this.clientSecret = hashedSecret;
        this.redirectUris = new ArrayList<>(redirectUris);
        this.scopes = new LinkedHashSet<>(scopes);
        this.grants = new LinkedHashSet<>(grants);
        this.status = SsoClientStatus.ACTIVE;
    }

    /**
     * 创建 SSO facet（工厂）。{@code client_id}/hash/元数据由应用层生成（SecureRandom + argon2）后传入。
     *
     * @param appId         所属应用 id
     * @param clientId      OIDC client_id（SecureRandom 生成）
     * @param hashedSecret  client_secret 的 argon2 hash
     * @param redirectUris  回调地址列表（至少一个）
     * @param scopes        授权范围（可空 → 空 set）
     * @param grants        授权类型（可空 → 空 set）
     * @return 新建的、尚未持久化的 SSO facet
     */
    public static SsoClient create(Long appId, String clientId, String hashedSecret,
                                   List<String> redirectUris, Set<String> scopes, Set<String> grants) {
        Assertions.require(redirectUris != null && !redirectUris.isEmpty(),
                AppRegistryMessage.SSO_REDIRECT_URI_REQUIRED);
        return new SsoClient(appId, clientId, hashedSecret, redirectUris,
                scopes == null ? Set.of() : scopes, grants == null ? Set.of() : grants);
    }

    /**
     * 轮换：换新 {@code client_id} + 新 hash + 新元数据，重置 ACTIVE（应用层先生成+哈希再传入）。
     *
     * @param newClientId     新 client_id
     * @param newHashedSecret 新 client_secret 的 argon2 hash
     * @param redirectUris    回调地址列表（至少一个）
     * @param scopes          授权范围（可空 → 空 set）
     * @param grants          授权类型（可空 → 空 set）
     */
    public void rotate(String newClientId, String newHashedSecret,
                       List<String> redirectUris, Set<String> scopes, Set<String> grants) {
        Assertions.require(redirectUris != null && !redirectUris.isEmpty(),
                AppRegistryMessage.SSO_REDIRECT_URI_REQUIRED);
        // 入参由应用层生成（SecureRandom + argon2），按构造保证非空，无需领域断言。
        this.clientId = newClientId;
        this.clientSecret = newHashedSecret;
        this.redirectUris = new ArrayList<>(redirectUris);
        this.scopes = new LinkedHashSet<>(scopes == null ? Set.of() : scopes);
        this.grants = new LinkedHashSet<>(grants == null ? Set.of() : grants);
        this.status = SsoClientStatus.ACTIVE;
    }

    /**
     * 禁用 SSO facet。仅 ACTIVE 可禁用，重复禁用抛 409。
     */
    public void disable() {
        Assertions.require(status == SsoClientStatus.ACTIVE, AppRegistryMessage.SSO_CLIENT_ALREADY_DISABLED);
        this.status = SsoClientStatus.DISABLED;
    }

    /**
     * 启用 SSO facet。仅 DISABLED 可启用，重复启用抛 409。
     */
    public void enable() {
        Assertions.require(status == SsoClientStatus.DISABLED, AppRegistryMessage.SSO_CLIENT_ALREADY_ENABLED);
        this.status = SsoClientStatus.ACTIVE;
    }
}
