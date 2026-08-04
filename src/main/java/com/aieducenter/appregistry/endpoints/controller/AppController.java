package com.aieducenter.appregistry.endpoints.controller;

import com.aieducenter.appregistry.application.RegisteredAppAppService;
import com.aieducenter.appregistry.application.dto.command.CreateAppCommand;
import com.aieducenter.appregistry.application.dto.command.UpdateAppCommand;
import com.aieducenter.appregistry.application.dto.query.AppQuery;
import com.aieducenter.appregistry.application.dto.response.AppResponse;
import com.cartisan.openapi.annotation.RequireSignature;
import com.cartisan.web.response.ApiResponse;
import com.cartisan.web.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用登记管理 REST API。
 *
 * <p>加固说明：app-registry 只对内可达，网络边界即信任边界，管理端点不再叠鉴权（ADR-0001/0002 §9）。
 * 签名验签过滤器对无 {@code X-App-Id} 的请求直接放行。</p>
 *
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/app-registry/apps")
@RequireSignature
@Validated
@Tag(name = "Apps", description = "应用登记管理")
public class AppController {

    private final RegisteredAppAppService appService;

    public AppController(RegisteredAppAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    @Operation(summary = "创建应用")
    public ApiResponse<AppResponse> create(@Valid @RequestBody CreateAppCommand command) {
        return ApiResponse.ok(appService.create(command));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询应用详情")
    public ApiResponse<AppResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(appService.findById(id));
    }

    @GetMapping
    @Operation(summary = "分页查询应用列表")
    public PageResponse<AppResponse> list(AppQuery query,
                                          @PageableDefault(size = 20) Pageable pageable) {
        return appService.list(query, pageable);
    }

    @PutMapping("/{id}/disable")
    @Operation(summary = "禁用应用")
    public ApiResponse<AppResponse> disable(@PathVariable Long id) {
        return ApiResponse.ok(appService.disable(id));
    }

    @PutMapping("/{id}/enable")
    @Operation(summary = "启用应用")
    public ApiResponse<AppResponse> enable(@PathVariable Long id) {
        return ApiResponse.ok(appService.enable(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新应用信息")
    public ApiResponse<AppResponse> update(@PathVariable Long id,
                                           @Valid @RequestBody UpdateAppCommand command) {
        return ApiResponse.ok(appService.update(id, command));
    }
}
