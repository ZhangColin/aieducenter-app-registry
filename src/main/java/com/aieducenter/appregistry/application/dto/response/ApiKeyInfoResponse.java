package com.aieducenter.appregistry.application.dto.response;

import java.util.Set;

public record ApiKeyInfoResponse(
    String appId,
    String appName,
    String apiSecret,
    Set<String> permissions,
    boolean active
) {
}
