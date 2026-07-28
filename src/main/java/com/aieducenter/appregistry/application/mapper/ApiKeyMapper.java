package com.aieducenter.appregistry.application.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.aieducenter.appregistry.application.dto.response.ApiKeyResponse;
import com.aieducenter.appregistry.domain.apikey.aggregate.ApiKey;
import com.cartisan.web.mapper.DomainMapper;

@Mapper(componentModel = "spring")
public interface ApiKeyMapper extends DomainMapper<ApiKey, ApiKeyResponse> {
    @Mapping(target = "statusName", source = "status.name")
    @Mapping(target = "apiSecret", ignore = true)
    @Override
    ApiKeyResponse convert(ApiKey apiKey);

    @Mapping(target = "statusName", source = "status.name")
    ApiKeyResponse convertWithSecret(ApiKey apiKey);
}
