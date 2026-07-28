package com.aieducenter.appregistry.endpoints.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.aieducenter.appregistry.application.ApiKeyManagementAppService;
import com.aieducenter.appregistry.application.ApiKeyQueryAppService;
import com.aieducenter.appregistry.application.dto.command.CreateApiKeyCommand;
import com.aieducenter.appregistry.application.dto.response.ApiKeyInfoResponse;
import com.aieducenter.appregistry.application.dto.response.ApiKeyResponse;
import com.cartisan.web.response.ApiResponse;

@RestController
@RequestMapping("/api/openapi/api-keys")
@RequiredArgsConstructor
@Validated
@Tag(name = "API Key 管理", description = "API Key 管理接口")
public class ApiKeyController {

    private final ApiKeyManagementAppService apiKeyManagementAppService;
    private final ApiKeyQueryAppService apiKeyQueryAppService;

    @PostMapping
    @Operation(summary = "创建 API Key")
    public ApiResponse<ApiKeyResponse> createApiKey(
            @Valid @RequestBody CreateApiKeyCommand command
    ) {
        ApiKeyResponse response = apiKeyManagementAppService.createApiKey(command);
        return ApiResponse.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询 API Key")
    public ApiResponse<ApiKeyResponse> getApiKey(
            @PathVariable Long id
    ) {
        ApiKeyResponse response = apiKeyManagementAppService.getApiKey(id);
        return ApiResponse.ok(response);
    }

    @PutMapping("/{id}/disable")
    @Operation(summary = "禁用 API Key")
    public ApiResponse<Void> disableApiKey(
            @PathVariable Long id
    ) {
        apiKeyManagementAppService.disableApiKey(id);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/enable")
    @Operation(summary = "启用 API Key")
    public ApiResponse<Void> enableApiKey(
            @PathVariable Long id
    ) {
        apiKeyManagementAppService.enableApiKey(id);
        return ApiResponse.ok();
    }

    @GetMapping("/by-appId")
    @Operation(summary = "通过 appId 获取 API Key 信息（供远程服务调用）")
    public ApiResponse<ApiKeyInfoResponse> getApiKeyByAppId(
            @RequestParam String appId
    ) {
        ApiKeyInfoResponse response = apiKeyQueryAppService.getApiKeyInfo(appId);
        return ApiResponse.ok(response);
    }
}
