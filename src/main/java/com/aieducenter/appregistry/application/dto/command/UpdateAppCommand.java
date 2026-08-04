package com.aieducenter.appregistry.application.dto.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新应用命令。
 *
 * @param name        新应用名
 * @param description 新描述（可空）
 */
public record UpdateAppCommand(

        @NotBlank(message = "应用名称不能为空")
        @Size(max = 128, message = "应用名称长度不能超过 128")
        String name,

        @Size(max = 512, message = "应用描述长度不能超过 512")
        String description
) {
}
