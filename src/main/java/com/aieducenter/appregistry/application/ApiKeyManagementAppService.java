package com.aieducenter.appregistry.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.appregistry.application.dto.command.CreateApiKeyCommand;
import com.aieducenter.appregistry.application.dto.response.ApiKeyResponse;
import com.aieducenter.appregistry.application.mapper.ApiKeyMapper;
import com.aieducenter.appregistry.domain.apikey.aggregate.ApiKey;
import com.aieducenter.appregistry.domain.apikey.error.OpenApiMessage;
import com.aieducenter.appregistry.domain.apikey.repository.ApiKeyRepository;
import com.cartisan.core.exception.ApplicationException;

@Service
@RequiredArgsConstructor
public class ApiKeyManagementAppService {

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyMapper apiKeyMapper;

    @Transactional
    public ApiKeyResponse createApiKey(CreateApiKeyCommand command) {
        ApiKey apiKey = new ApiKey(
            command.businessSystemName(),
            command.description(),
            command.permissions()
        );

        return apiKeyMapper.convertWithSecret(apiKeyRepository.save(apiKey));
    }

    @Transactional(readOnly = true)
    public ApiKeyResponse getApiKey(Long id) {
        ApiKey apiKey = apiKeyRepository.findById(id)
            .orElseThrow(() -> new ApplicationException(OpenApiMessage.API_KEY_NOT_FOUND));

        return apiKeyMapper.convert(apiKey);
    }

    @Transactional(readOnly = true)
    public ApiKeyResponse getApiKeyByKey(String apiKeyStr) {
        ApiKey apiKey = apiKeyRepository.findByApiKey(apiKeyStr)
            .orElseThrow(() -> new ApplicationException(OpenApiMessage.API_KEY_NOT_FOUND));

        return apiKeyMapper.convert(apiKey);
    }

    @Transactional
    public void disableApiKey(Long id) {
        ApiKey apiKey = apiKeyRepository.findById(id)
            .orElseThrow(() -> new ApplicationException(OpenApiMessage.API_KEY_NOT_FOUND));
        apiKey.disable();
        apiKeyRepository.save(apiKey);
    }

    @Transactional
    public void enableApiKey(Long id) {
        ApiKey apiKey = apiKeyRepository.findById(id)
            .orElseThrow(() -> new ApplicationException(OpenApiMessage.API_KEY_NOT_FOUND));
        apiKey.enable();
        apiKeyRepository.save(apiKey);
    }
}
