package com.aieducenter.appregistry.application.dto.response;

import com.aieducenter.appregistry.domain.apikey.enums.ApiKeyStatus;

import java.time.LocalDateTime;

public record ApiKeyResponse(
    Long id,
    String apiKey,
    String apiSecret,
    String businessSystemName,
    ApiKeyStatus status,
    String statusName,
    String permissions,
    String description,
    LocalDateTime createdAt
) {
}
