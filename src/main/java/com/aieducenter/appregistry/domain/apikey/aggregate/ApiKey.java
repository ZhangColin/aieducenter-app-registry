package com.aieducenter.appregistry.domain.apikey.aggregate;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.aieducenter.appregistry.domain.apikey.enums.ApiKeyStatus;
import com.aieducenter.appregistry.domain.apikey.error.OpenApiMessage;
import com.cartisan.core.domain.AggregateRoot;
import com.cartisan.core.stereotype.Aggregate;
import com.cartisan.core.util.Assertions;
import com.cartisan.data.jpa.domain.AuditableSoftDeletable;
import com.cartisan.data.jpa.id.TsidGenerator;
import jakarta.persistence.*;
import lombok.Getter;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "oas_api_keys")
@Aggregate
public class ApiKey extends AuditableSoftDeletable implements AggregateRoot<ApiKey, Long> {

    @Getter
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Getter
    @Column(name = "api_key", nullable = false, unique = true, length = 64)
    private String apiKey;

    @Getter
    @Column(name = "api_secret", nullable = false)
    private String apiSecret;

    @Getter
    @Column(name = "business_system_name", nullable = false, length = 128)
    private String businessSystemName;

    @Getter
    @Column(name = "status", nullable = false)
    private ApiKeyStatus status;

    @Getter
    @Column(name = "permissions", columnDefinition = "TEXT")
    private String permissions;

    @Getter
    @Column(name = "description")
    private String description;

    @PrePersist
    void prePersist() {
        if (id == null) {
            this.id = TsidGenerator.newInstance().generate();
        }
    }

    protected ApiKey() {}

    public ApiKey(String businessSystemName, String description, Set<String> permissions) {
        Assertions.require(StrUtil.isNotBlank(businessSystemName),
            OpenApiMessage.API_KEY_INVALID);

        this.businessSystemName = businessSystemName;
        this.description = description;
        this.status = ApiKeyStatus.ACTIVE;
        this.apiKey = generateApiKey();
        this.apiSecret = generateApiSecret();

        if (permissions != null && !permissions.isEmpty()) {
            this.permissions = String.join(",", permissions);
        }
    }

    public Set<String> getPermissionSet() {
        if (StrUtil.isBlank(permissions)) {
            return Set.of();
        }
        return Arrays.stream(permissions.split(","))
            .map(String::trim)
            .filter(StrUtil::isNotBlank)
            .collect(Collectors.toSet());
    }

    public void disable() {
        this.status = ApiKeyStatus.DISABLED;
    }

    public void enable() {
        this.status = ApiKeyStatus.ACTIVE;
    }

    private String generateApiKey() {
        return RandomUtil.randomString("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789", 32);
    }

    private String generateApiSecret() {
        byte[] secretBytes = new byte[32];
        new SecureRandom().nextBytes(secretBytes);
        return Base64.getEncoder().encodeToString(secretBytes);
    }
}
