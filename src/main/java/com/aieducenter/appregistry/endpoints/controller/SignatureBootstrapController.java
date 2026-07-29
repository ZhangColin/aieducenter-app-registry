package com.aieducenter.appregistry.endpoints.controller;

import com.aieducenter.appregistry.application.ApiKeyAppService;
import com.cartisan.core.exception.BaseCodeMessage;
import com.cartisan.core.exception.DomainException;
import com.cartisan.openapi.provider.ApiKeyInfo;
import com.cartisan.web.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 签名 facet <strong>bootstrap 端点</strong>——无验签的源端点（验签机制依赖它，鸡生蛋）。
 *
 * <p>{@code GET /api/app-registry/api-keys/{apiKey}} 返 {@link ApiKeyInfo}（含明文 {@code apiSecret}，
 * 供消费方 HMAC 验签）。<strong>只返签名 facet，不返 SSO 字段</strong>（CONTEXT 不变式 4）。</p>
 *
 * <p><strong>加固 = 网络隔离（只对内可达）</strong>：本端点无验签、无 bootstrap token / mTLS / IP allowlist——
 * app-registry 部署为只对内可达，网络边界即信任边界（ADR-0002 §9）。调用方全是可信的自有 provider 服务。</p>
 *
 * <p>组合状态级联：app 禁用或 facet 禁用 → 返 {@code status=DISABLED}（消费方拒签）。</p>
 *
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/app-registry/api-keys")
@Tag(name = "Bootstrap", description = "签名 facet bootstrap 端点（内部，无验签）")
public class SignatureBootstrapController {

    private final ApiKeyAppService apiKeyAppService;

    public SignatureBootstrapController(ApiKeyAppService apiKeyAppService) {
        this.apiKeyAppService = apiKeyAppService;
    }

    @GetMapping("/{apiKey}")
    @Operation(summary = "按 apiKey 取签名 facet（bootstrap）", description = "返 ApiKeyInfo（含明文 apiSecret）；只返签名 facet")
    public ApiResponse<ApiKeyInfo> getByApiKey(@PathVariable String apiKey) {
        ApiKeyInfo info = apiKeyAppService.resolveApiKeyInfo(apiKey)
                .orElseThrow(() -> new DomainException(BaseCodeMessage.RESOURCE_NOT_FOUND, apiKey));
        return ApiResponse.ok(info);
    }
}
