package com.aieducenter.appregistry.application;

import com.aieducenter.appregistry.application.dto.response.ApiKeyCreatedResponse;
import com.aieducenter.appregistry.application.dto.response.ApiKeyResponse;
import com.aieducenter.appregistry.application.mapper.ApiKeyMapper;
import com.aieducenter.appregistry.domain.app.aggregate.RegisteredApp;
import com.aieducenter.appregistry.domain.app.enums.RegisteredAppStatus;
import com.aieducenter.appregistry.domain.app.repository.RegisteredAppRepository;
import com.aieducenter.appregistry.domain.signature.aggregate.ApiCredentials;
import com.aieducenter.appregistry.domain.signature.aggregate.ApiCredentials.Generated;
import com.aieducenter.appregistry.domain.signature.aggregate.ApiKey;
import com.aieducenter.appregistry.domain.signature.enums.ApiKeyStatus;
import com.aieducenter.appregistry.domain.signature.port.ApiSecretEncrypter;
import com.aieducenter.appregistry.domain.signature.repository.ApiKeyRepository;
import com.cartisan.core.exception.BaseCodeMessage;
import com.cartisan.core.exception.DomainException;
import com.cartisan.openapi.provider.ApiKeyInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 签名 facet 应用服务——创建/轮换、查询、禁用/启用，以及解析为框架 {@link ApiKeyInfo}。
 *
 * <p>明文 {@code apiSecret} 仅在创建/轮换响应里返一次：生成（SecureRandom）→ 加密（AES-GCM）→
 * 入库密文 → 明文进响应。之后任何接口都不再返回明文。</p>
 *
 * <p>{@link #resolveApiKeyInfo(String)} 同时服务 bootstrap 端点 {@code GET /api-keys/{apiKey}}
 * 与 {@link LocalApiKeyProvider}：按 {@code api_key} 查、join app 算组合状态
 * ({@code key.status && app.status})、内存解密组 {@code ApiKeyInfo(appId=api_key, appName, apiSecret=明文, status)}。</p>
 *
 * @since 0.1.0
 */
@Service
public class ApiKeyAppService {

    /** 随机 apiKey 自撞名重试上限（碰撞概率可忽略，仅作安全网）。*/
    private static final int GENERATE_MAX_ATTEMPTS = 10;

    private final ApiKeyRepository apiKeyRepository;
    private final RegisteredAppRepository appRepository;
    private final ApiSecretEncrypter encrypter;
    private final ApiKeyMapper mapper;

    public ApiKeyAppService(ApiKeyRepository apiKeyRepository, RegisteredAppRepository appRepository,
                            ApiSecretEncrypter encrypter, ApiKeyMapper mapper) {
        this.apiKeyRepository = apiKeyRepository;
        this.appRepository = appRepository;
        this.encrypter = encrypter;
        this.mapper = mapper;
    }

    /**
     * 创建或轮换应用的签名 facet（1:1，同一端点两用）。
     *
     * <p>已有活跃 facet → 原地轮换（换新 apiKey + 密文，重置 ACTIVE）；否则新建。
     * 响应一次性返回明文 {@code apiSecret}。</p>
     */
    @Transactional
    public ApiKeyCreatedResponse createOrRotate(Long appId) {
        loadApp(appId);
        Generated generated = generateUnique();

        Optional<ApiKey> existing = apiKeyRepository.findByAppId(appId);
        ApiKey key;
        if (existing.isPresent()) {
            key = existing.get();
            key.rotate(generated.apiKey(), encrypter.encrypt(generated.apiSecret()));
        } else {
            key = ApiKey.create(appId, generated.apiKey(), encrypter.encrypt(generated.apiSecret()));
        }
        apiKeyRepository.saveAndFlush(key);
        return mapper.toCreated(key, generated.apiSecret());
    }

    /**
     * 查询应用的签名 facet（不含明文 secret）。应用或 facet 不存在返 404。
     */
    @Transactional(readOnly = true)
    public ApiKeyResponse getByAppId(Long appId) {
        loadApp(appId);
        return mapper.convert(loadKeyByApp(appId));
    }

    /**
     * 禁用签名 facet。重复禁用返 409。
     */
    @Transactional
    public ApiKeyResponse disable(Long appId) {
        loadApp(appId);
        ApiKey key = loadKeyByApp(appId);
        key.disable();
        apiKeyRepository.saveAndFlush(key);
        return mapper.convert(key);
    }

    /**
     * 启用签名 facet。重复启用返 409。
     */
    @Transactional
    public ApiKeyResponse enable(Long appId) {
        loadApp(appId);
        ApiKey key = loadKeyByApp(appId);
        key.enable();
        apiKeyRepository.saveAndFlush(key);
        return mapper.convert(key);
    }

    /**
     * 解析 {@code apiKey} → 框架 {@link ApiKeyInfo}（组合状态 = facet.status && app.status）。
     *
     * <p>供 bootstrap 端点 {@code GET /api-keys/{apiKey}} 与 {@link LocalApiKeyProvider} 共用。
     * app 禁用 / 不存在 → status=DISABLED（框架拒签）。明文 secret 内存解密，仅活跃时被框架使用。</p>
     *
     * @param apiKey 凭证标识（= 框架 X-App-Id）
     * @return ApiKeyInfo；不存在返 empty
     */
    @Transactional(readOnly = true)
    public Optional<ApiKeyInfo> resolveApiKeyInfo(String apiKey) {
        return apiKeyRepository.findByApiKey(apiKey).map(key -> {
            Optional<RegisteredApp> app = appRepository.findById(key.getAppId());
            String appName = app.map(RegisteredApp::getName).orElse("");
            boolean active = key.getStatus() == ApiKeyStatus.ACTIVE
                    && app.map(a -> a.getStatus() == RegisteredAppStatus.ACTIVE).orElse(false);
            String plaintextSecret = encrypter.decrypt(key.getApiSecret());
            return new ApiKeyInfo(key.getApiKey(), appName, plaintextSecret, active ? "ACTIVE" : "DISABLED");
        });
    }

    private Generated generateUnique() {
        for (int i = 0; i < GENERATE_MAX_ATTEMPTS; i++) {
            Generated candidate = ApiCredentials.generate();
            if (!apiKeyRepository.existsByApiKey(candidate.apiKey())) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate unique api_key after "
                + GENERATE_MAX_ATTEMPTS + " attempts");
    }

    private RegisteredApp loadApp(Long appId) {
        return appRepository.findById(appId)
                .orElseThrow(() -> new DomainException(BaseCodeMessage.RESOURCE_NOT_FOUND, appId));
    }

    private ApiKey loadKeyByApp(Long appId) {
        return apiKeyRepository.findByAppId(appId)
                .orElseThrow(() -> new DomainException(BaseCodeMessage.RESOURCE_NOT_FOUND, appId));
    }
}
