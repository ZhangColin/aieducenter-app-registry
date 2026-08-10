package com.aieducenter.appregistry.application;

import com.aieducenter.appregistry.application.dto.command.CreateSsoClientCommand;
import com.aieducenter.appregistry.application.dto.response.SsoClientCreatedResponse;
import com.aieducenter.appregistry.application.dto.response.SsoClientInfo;
import com.aieducenter.appregistry.application.dto.response.SsoClientResponse;
import com.aieducenter.appregistry.application.mapper.SsoClientMapper;
import com.aieducenter.appregistry.domain.app.aggregate.RegisteredApp;
import com.aieducenter.appregistry.domain.app.enums.RegisteredAppStatus;
import com.aieducenter.appregistry.domain.app.repository.RegisteredAppRepository;
import com.aieducenter.appregistry.domain.sso.aggregate.SsoClient;
import com.aieducenter.appregistry.domain.sso.aggregate.SsoCredentials;
import com.aieducenter.appregistry.domain.sso.aggregate.SsoCredentials.Generated;
import com.aieducenter.appregistry.domain.sso.enums.SsoClientStatus;
import com.aieducenter.appregistry.domain.sso.port.ClientSecretHasher;
import com.aieducenter.appregistry.domain.sso.repository.SsoClientRepository;
import com.cartisan.core.exception.BaseCodeMessage;
import com.cartisan.core.exception.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * SsoClient 应用服务——创建/轮换、查询、禁用/启用，以及解析为 identity 消费契约 {@link SsoClientInfo}。
 *
 * <p>明文 {@code client_secret} 仅在创建/轮换响应里返一次：生成（SecureRandom）→ 哈希（argon2）→
 * 入库 hash → 明文进响应。之后任何接口都不再返回明文（hash 不可逆）。</p>
 *
 * <p>{@link #resolveSsoClientInfo(String)} 服务 bootstrap 端点 {@code GET /sso-clients/{clientId}}：
 * 按 {@code client_id} 查、join app 算组合状态（{@code client.status && app.status}）、active 时返 hash、
 * 否则 hash=null（不返有效元数据，ADR-0003 §4）。</p>
 *
 * @since 0.1.0
 */
@Service
public class SsoClientAppService {

    /** 随机 client_id 自撞名重试上限（碰撞概率可忽略，仅作安全网）。*/
    private static final int GENERATE_MAX_ATTEMPTS = 10;

    private final SsoClientRepository ssoClientRepository;
    private final RegisteredAppRepository appRepository;
    private final ClientSecretHasher hasher;
    private final SsoClientMapper mapper;

    public SsoClientAppService(SsoClientRepository ssoClientRepository, RegisteredAppRepository appRepository,
                               ClientSecretHasher hasher, SsoClientMapper mapper) {
        this.ssoClientRepository = ssoClientRepository;
        this.appRepository = appRepository;
        this.hasher = hasher;
        this.mapper = mapper;
    }

    /**
     * 创建或轮换应用的 SsoClient（1:1，同一端点两用）。
     *
     * <p>已有活跃 SsoClient → 原地轮换（换新 client_id + hash + 元数据，重置 ACTIVE）；否则新建。
     * 响应一次性返回明文 {@code client_secret}。</p>
     */
    @Transactional
    public SsoClientCreatedResponse createOrRotate(Long appId, CreateSsoClientCommand command) {
        loadApp(appId);
        Generated generated = generateUnique();

        Optional<SsoClient> existing = ssoClientRepository.findByAppId(appId);
        SsoClient client;
        if (existing.isPresent()) {
            client = existing.get();
            client.rotate(generated.clientId(), hasher.hash(generated.clientSecret()),
                    command.redirectUris(), command.postLogoutRedirectUris(), command.scopes(), command.grants());
        } else {
            client = SsoClient.create(appId, generated.clientId(), hasher.hash(generated.clientSecret()),
                    command.redirectUris(), command.postLogoutRedirectUris(), command.scopes(), command.grants());
        }
        ssoClientRepository.saveAndFlush(client);
        return mapper.toCreated(client, generated.clientSecret());
    }

    /**
     * 查询应用的 SsoClient（不含明文 secret）。应用或 SsoClient 不存在返 404。
     */
    @Transactional(readOnly = true)
    public SsoClientResponse getByAppId(Long appId) {
        loadApp(appId);
        return mapper.convert(loadClientByApp(appId));
    }

    /**
     * 禁用 SsoClient。重复禁用返 409。
     */
    @Transactional
    public SsoClientResponse disable(Long appId) {
        loadApp(appId);
        SsoClient client = loadClientByApp(appId);
        client.disable();
        ssoClientRepository.saveAndFlush(client);
        return mapper.convert(client);
    }

    /**
     * 启用 SsoClient。重复启用返 409。
     */
    @Transactional
    public SsoClientResponse enable(Long appId) {
        loadApp(appId);
        SsoClient client = loadClientByApp(appId);
        client.enable();
        ssoClientRepository.saveAndFlush(client);
        return mapper.convert(client);
    }

    /**
     * 解析 {@code client_id} → identity 消费契约 {@link SsoClientInfo}（组合状态 = SsoClient.status && app.status）。
     *
     * <p>供 bootstrap 端点 {@code GET /sso-clients/{clientId}}。app / client 任一禁用 → {@code active=false} 且
     * {@code clientSecretHash=null}（不返有效元数据）。active 时返 hash，identity 缓存 + 本地比对。</p>
     *
     * @param clientId OIDC client_id
     * @return SsoClientInfo；不存在返 empty
     */
    @Transactional(readOnly = true)
    public Optional<SsoClientInfo> resolveSsoClientInfo(String clientId) {
        return ssoClientRepository.findByClientId(clientId).map(client -> {
            Optional<RegisteredApp> app = appRepository.findById(client.getAppId());
            String clientName = app.map(RegisteredApp::getName).orElse("");
            boolean active = client.getStatus() == SsoClientStatus.ACTIVE
                    && app.map(a -> a.getStatus() == RegisteredAppStatus.ACTIVE).orElse(false);
            String secretHash = active ? client.getClientSecret() : null;
            return new SsoClientInfo(client.getClientId(), client.getAppId(), clientName, secretHash,
                    client.getRedirectUris(), client.getPostLogoutRedirectUris(),
                    client.getScopes(), client.getGrants(), active);
        });
    }

    private Generated generateUnique() {
        for (int i = 0; i < GENERATE_MAX_ATTEMPTS; i++) {
            Generated candidate = SsoCredentials.generate();
            if (!ssoClientRepository.existsByClientId(candidate.clientId())) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate unique client_id after "
                + GENERATE_MAX_ATTEMPTS + " attempts");
    }

    private RegisteredApp loadApp(Long appId) {
        return appRepository.findById(appId)
                .orElseThrow(() -> new DomainException(BaseCodeMessage.RESOURCE_NOT_FOUND, appId));
    }

    private SsoClient loadClientByApp(Long appId) {
        return ssoClientRepository.findByAppId(appId)
                .orElseThrow(() -> new DomainException(BaseCodeMessage.RESOURCE_NOT_FOUND, appId));
    }
}
