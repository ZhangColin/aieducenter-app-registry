package com.aieducenter.appregistry.domain.apikey.enums;

import com.cartisan.core.domain.BaseEnum;
import com.cartisan.data.jpa.converter.BaseEnumConverter;
import jakarta.persistence.Converter;

public enum ApiKeyStatus implements BaseEnum<ApiKeyStatus> {
    ACTIVE(1, "启用"),
    DISABLED(0, "禁用");

    private final Integer code;
    private final String name;

    ApiKeyStatus(Integer code, String name) {
        this.code = code;
        this.name = name;
    }

    @Override
    public Integer getCode() {
        return code;
    }

    @Override
    public String getName() {
        return name;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends BaseEnumConverter<ApiKeyStatus> {
        public JpaConverter() {
            super(ApiKeyStatus.class);
        }
    }
}
