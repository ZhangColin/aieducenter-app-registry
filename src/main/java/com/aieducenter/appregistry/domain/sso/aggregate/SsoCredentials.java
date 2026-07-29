package com.aieducenter.appregistry.domain.sso.aggregate;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * SSO facet 凭证生成（{@code client_id} + {@code client_secret} 明文）。
 *
 * <p>用 {@link SecureRandom} 生成 32 字节随机数 → Base64URL（无填充），与签名 facet {@code ApiCredentials}
 * 同款（ADR-0003 §6）。{@code client_id} 经撞名检查后入库；明文 {@code client_secret} 由应用层哈希后入库、
 * 仅在创建/轮换响应里返一次。</p>
 *
 * <p>纯 JDK、无 Spring 依赖，可在领域层使用。</p>
 *
 * @since 0.1.0
 */
public final class SsoCredentials {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int BYTE_LENGTH = 32;

    private SsoCredentials() {
    }

    /**
     * 生成一对新的凭证（client_id + 明文 client_secret）。
     *
     * @return 生成的凭证对
     */
    public static Generated generate() {
        byte[] clientIdBytes = new byte[BYTE_LENGTH];
        byte[] secretBytes = new byte[BYTE_LENGTH];
        RANDOM.nextBytes(clientIdBytes);
        RANDOM.nextBytes(secretBytes);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return new Generated(encoder.encodeToString(clientIdBytes), encoder.encodeToString(secretBytes));
    }

    /** 生成结果：client_id（OIDC 凭证标识）+ client_secret（明文，待哈希）。*/
    public record Generated(String clientId, String clientSecret) {
    }
}
