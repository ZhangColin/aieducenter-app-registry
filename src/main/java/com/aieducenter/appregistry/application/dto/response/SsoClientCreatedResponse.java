package com.aieducenter.appregistry.application.dto.response;

import com.aieducenter.appregistry.domain.sso.enums.SsoClientStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * SsoClient 创建/轮换响应——<strong>一次性</strong>返回明文 {@code client_secret}。
 *
 * <p>消费方必须在此次响应里捕获并妥善保管 {@code client_secret}：之后任何接口都不可再取回明文
 * （DB 只存 argon2 hash，不可逆；管理 GET 仅返 {@link SsoClientResponse}）。</p>
 *
 * @param id            SsoClient id
 * @param appId         所属应用 id
 * @param clientId      OIDC client_id
 * @param clientSecret  明文 secret（仅此一次返回）
 * @param redirectUris  回调地址列表
 * @param postLogoutRedirectUris 登出回跳白名单（OIDC RP-Initiated Logout）
 * @param scopes        授权范围
 * @param grants        授权类型
 * @param status        凭证状态
 * @param statusName    状态中文名
 * @param createdAt     创建时间
 * @param updatedAt     更新时间
 *
 * @since 0.1.0
 */
public record SsoClientCreatedResponse(
        Long id,
        Long appId,
        String clientId,
        String clientSecret,
        List<String> redirectUris,
        List<String> postLogoutRedirectUris,
        Set<String> scopes,
        Set<String> grants,
        SsoClientStatus status,
        String statusName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
