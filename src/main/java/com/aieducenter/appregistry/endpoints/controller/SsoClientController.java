package com.aieducenter.appregistry.endpoints.controller;

import com.aieducenter.appregistry.application.SsoClientAppService;
import com.aieducenter.appregistry.application.dto.command.CreateSsoClientCommand;
import com.aieducenter.appregistry.application.dto.response.SsoClientCreatedResponse;
import com.aieducenter.appregistry.application.dto.response.SsoClientResponse;
import com.cartisan.web.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SSO facet 管理 REST API（挂在应用路径下，1:1）。
 *
 * <p>加固说明：app-registry 只对内可达，网络边界即信任边界（ADR-0003 §9）。</p>
 *
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/app-registry/apps/{appId}/sso-clients")
@Tag(name = "SsoClients", description = "SSO facet 管理（OIDC client 元数据）")
public class SsoClientController {

    private final SsoClientAppService ssoClientAppService;

    public SsoClientController(SsoClientAppService ssoClientAppService) {
        this.ssoClientAppService = ssoClientAppService;
    }

    @PostMapping
    @Operation(summary = "创建或轮换 SSO facet", description = "已有则轮换、否则新建；响应一次性返回明文 client_secret")
    public ApiResponse<SsoClientCreatedResponse> createOrRotate(
            @PathVariable Long appId, @Valid @RequestBody CreateSsoClientCommand command) {
        return ApiResponse.ok(ssoClientAppService.createOrRotate(appId, command));
    }

    @GetMapping
    @Operation(summary = "查询应用的 SSO facet", description = "不返回明文 client_secret")
    public ApiResponse<SsoClientResponse> get(@PathVariable Long appId) {
        return ApiResponse.ok(ssoClientAppService.getByAppId(appId));
    }

    @PutMapping("/disable")
    @Operation(summary = "禁用 SSO facet")
    public ApiResponse<SsoClientResponse> disable(@PathVariable Long appId) {
        return ApiResponse.ok(ssoClientAppService.disable(appId));
    }

    @PutMapping("/enable")
    @Operation(summary = "启用 SSO facet")
    public ApiResponse<SsoClientResponse> enable(@PathVariable Long appId) {
        return ApiResponse.ok(ssoClientAppService.enable(appId));
    }
}
