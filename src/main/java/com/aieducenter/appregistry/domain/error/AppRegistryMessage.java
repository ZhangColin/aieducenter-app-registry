package com.aieducenter.appregistry.domain.error;

import com.cartisan.core.exception.CodeMessage;

/**
 * AppRegistry 上下文错误码。
 *
 * <p>本上下文（应用登记处）跨 app / api-key / sso-client 三个聚合的领域错误码集中于此，
 * 随各 ticket 逐步扩充：
 * <ul>
 *   <li>#3 app：app_code 撞名、状态转换冲突等（撞名主路径走
 *       {@link com.cartisan.core.exception.BaseCodeMessage#DUPLICATE}）</li>
 *   <li>#4 api-key：api_key 撞名、签名 facet 缺失等</li>
 *   <li>#5 sso-client：client_id 撞名、SSO facet 缺失等</li>
 * </ul>
 *
 * <p>通用语义优先复用 {@link com.cartisan.core.exception.BaseCodeMessage}：
 * 资源不存在用 {@code RESOURCE_NOT_FOUND}、重复冲突用 {@code DUPLICATE}；
 * 本枚举只承载领域专属、语义更精确的错误码。
 *
 * @since 0.1.0
 */
public enum AppRegistryMessage implements CodeMessage {

    /**
     * 占位错误码——骨架阶段占位；首个真实错误码随 #3 落入后移除。
     */
    PLACEHOLDER(500, "AR_PLACEHOLDER", "AppRegistry placeholder error"),

    ;

    private final int httpStatus;
    private final String code;
    private final String message;

    AppRegistryMessage(int httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return this.code;
    }

    @Override
    public String message() {
        return this.message;
    }

    @Override
    public int httpStatus() {
        return this.httpStatus;
    }
}
