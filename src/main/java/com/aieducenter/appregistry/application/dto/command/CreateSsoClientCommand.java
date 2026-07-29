package com.aieducenter.appregistry.application.dto.command;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

/**
 * 创建 SSO facet 命令（也用于轮换：整份替换 client_id/client_secret + 元数据）。
 *
 * @param redirectUris 回调地址列表（至少一个）；OIDC redirect_uri
 * @param scopes       授权范围（可空 → 空 set）
 * @param grants       授权类型（可空 → 空 set）
 */
public record CreateSsoClientCommand(

        @NotEmpty(message = "回调地址不能为空")
        List<@Size(max = 512) String> redirectUris,

        Set<String> scopes,

        Set<String> grants
) {
}
