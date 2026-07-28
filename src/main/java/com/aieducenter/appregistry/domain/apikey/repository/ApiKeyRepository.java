package com.aieducenter.appregistry.domain.apikey.repository;

import com.aieducenter.appregistry.domain.apikey.aggregate.ApiKey;
import com.cartisan.core.stereotype.Port;
import com.cartisan.core.stereotype.PortType;
import com.cartisan.data.jpa.repository.BaseRepository;

import java.util.Optional;

@Port(PortType.REPOSITORY)
public interface ApiKeyRepository extends BaseRepository<ApiKey, Long> {
    Optional<ApiKey> findByApiKey(String apiKey);
    boolean existsByApiKey(String apiKey);
}
