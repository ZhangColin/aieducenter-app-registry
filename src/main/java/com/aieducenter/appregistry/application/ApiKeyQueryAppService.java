package com.aieducenter.appregistry.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.appregistry.application.dto.response.ApiKeyInfoResponse;
import com.aieducenter.appregistry.domain.apikey.aggregate.ApiKey;
import com.aieducenter.appregistry.domain.apikey.enums.ApiKeyStatus;
import com.aieducenter.appregistry.domain.apikey.error.OpenApiMessage;
import com.aieducenter.appregistry.domain.apikey.repository.ApiKeyRepository;
import com.cartisan.core.exception.ApplicationException;

@Service
@RequiredArgsConstructor
public class ApiKeyQueryAppService {

    private final ApiKeyRepository apiKeyRepository;

    @Transactional(readOnly = true)
    public ApiKeyInfoResponse getApiKeyInfo(String appId) {
        ApiKey apiKey = apiKeyRepository.findByApiKey(appId)
            .filter(key -> key.getStatus() == ApiKeyStatus.ACTIVE)
            .orElseThrow(() -> new ApplicationException(OpenApiMessage.API_KEY_NOT_FOUND));

        return new ApiKeyInfoResponse(
            apiKey.getApiKey(),
            apiKey.getBusinessSystemName(),
            apiKey.getApiSecret(),
            apiKey.getPermissionSet(),
            apiKey.getStatus() == ApiKeyStatus.ACTIVE
        );
    }
}
