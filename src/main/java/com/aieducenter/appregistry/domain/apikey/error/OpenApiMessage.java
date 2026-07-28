package com.aieducenter.appregistry.domain.apikey.error;

import com.cartisan.core.exception.CodeMessage;

public enum OpenApiMessage implements CodeMessage {
    // ========== 格式校验错误 (400) ==========
    API_KEY_INVALID(400, "OAS_001", "API Key 格式不正确"),

    // ========== 资源不存在 (404) ==========
    API_KEY_NOT_FOUND(404, "OAS_020", "API Key 不存在"),

    // ========== 业务限制 (403) ==========
    API_KEY_DISABLED(403, "OAS_030", "API Key 已禁用");

    private final int httpStatus;
    private final String code;
    private final String message;

    OpenApiMessage(int httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
