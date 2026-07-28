package com.aieducenter.appregistry.application.dto.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateApiKeyCommand(
    @NotBlank(message = "业务系统名称不能为空")
    @Size(max = 128, message = "业务系统名称长度不能超过128")
    String businessSystemName,

    @Size(max = 255, message = "描述长度不能超过255")
    String description,

    Set<String> permissions
) {
}
