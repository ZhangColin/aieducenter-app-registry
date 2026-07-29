package com.aieducenter.appregistry.domain.signature.aggregate;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 签名 facet 凭证生成（{@code apiKey} + {@code apiSecret} 明文）。
 *
 * <p>用 {@link SecureRandom} 生成 32 字节随机数 → Base64URL（无填充）——修旧 hutool
 * {@code RandomUtil} 的非密码学安全实现（ADR-0002 §3）。明文 {@code apiSecret} 由应用层
 * 加密后入库、仅在创建/轮换响应里返一次。</p>
 *
 * <p>纯 JDK、无 Spring 依赖，可在领域层使用。</p>
 *
 * @since 0.1.0
 */
public final class ApiCredentials {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int BYTE_LENGTH = 32;

    private ApiCredentials() {
    }

    /**
     * 生成一对新的凭证（apiKey + 明文 apiSecret）。
     *
     * @return 生成的凭证对
     */
    public static Generated generate() {
        byte[] apiKeyBytes = new byte[BYTE_LENGTH];
        byte[] secretBytes = new byte[BYTE_LENGTH];
        RANDOM.nextBytes(apiKeyBytes);
        RANDOM.nextBytes(secretBytes);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return new Generated(encoder.encodeToString(apiKeyBytes), encoder.encodeToString(secretBytes));
    }

    /** 生成结果：apiKey（凭证标识）+ apiSecret（明文，待加密）。*/
    public record Generated(String apiKey, String apiSecret) {
    }
}
