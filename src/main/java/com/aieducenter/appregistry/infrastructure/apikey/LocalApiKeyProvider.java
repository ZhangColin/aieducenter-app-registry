package com.aieducenter.appregistry.infrastructure.apikey;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import com.aieducenter.appregistry.application.ApiKeyQueryAppService;
import com.aieducenter.appregistry.application.dto.response.ApiKeyInfoResponse;
import com.cartisan.openapi.provider.ApiKeyInfo;
import com.cartisan.openapi.provider.ApiKeyProvider;

@Component
@RequiredArgsConstructor
public class LocalApiKeyProvider implements ApiKeyProvider {

    private final ApiKeyQueryAppService queryService;

    @Override
    public ApiKeyInfo getByAppId(String appId) {
        ApiKeyInfoResponse response = queryService.getApiKeyInfo(appId);

        return new ApiKeyInfo(
            response.appId(),
            response.appName(),
            response.apiSecret(),
            response.permissions(),
            response.active() ? "ACTIVE" : "DISABLED"
        );
    }
}
