package com.aieducenter.appregistry.application;

import com.aieducenter.appregistry.application.dto.command.CreateAppCommand;
import com.aieducenter.appregistry.application.dto.response.AppResponse;
import com.aieducenter.appregistry.application.mapper.RegisteredAppMapper;
import com.aieducenter.appregistry.domain.app.aggregate.RegisteredApp;
import com.aieducenter.appregistry.domain.app.repository.RegisteredAppRepository;
import com.aieducenter.appregistry.domain.error.AppRegistryMessage;
import com.cartisan.core.exception.BaseCodeMessage;
import com.cartisan.core.exception.DomainException;
import com.cartisan.core.util.Assertions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 应用登记应用服务——应用的创建 / 查询 / 禁用 / 启用。
 *
 * <p>撞名主路径：应用层 {@code existsByAppCode}（看含软删全行）先查 + 抛 {@link DomainException}(409)；
 * DB 普通唯一约束作并发兜底（{@code DuplicateKeyException → 409}）。</p>
 *
 * @since 0.1.0
 */
@Service
public class RegisteredAppAppService {

    private final RegisteredAppRepository appRepository;
    private final RegisteredAppMapper appMapper;

    public RegisteredAppAppService(RegisteredAppRepository appRepository, RegisteredAppMapper appMapper) {
        this.appRepository = appRepository;
        this.appMapper = appMapper;
    }

    /**
     * 创建应用。
     *
     * <p>app_code 撞名（含软删行）返 409。创建成功后 flush 以回填审计时间戳。</p>
     */
    @Transactional
    public AppResponse create(CreateAppCommand command) {
        Assertions.require(
                !appRepository.existsByAppCode(command.appCode()),
                BaseCodeMessage.DUPLICATE, command.appCode());

        RegisteredApp app = RegisteredApp.create(command.appCode(), command.name(), command.description());
        appRepository.saveAndFlush(app);
        return appMapper.convert(app);
    }

    /**
     * 查询应用详情。不存在返 404。
     */
    @Transactional(readOnly = true)
    public AppResponse findById(Long id) {
        return appMapper.convert(loadApp(id));
    }

    /**
     * 禁用应用。重复禁用返 409。admin-console 不可禁用。
     */
    @Transactional
    public AppResponse disable(Long id) {
        RegisteredApp app = loadApp(id);
        if (PlatformSeedAppService.PLATFORM_APP_CODE.equals(app.getAppCode())) {
            throw new DomainException(AppRegistryMessage.ADMIN_CONSOLE_CANNOT_DISABLE, app.getAppCode());
        }
        app.disable();
        appRepository.saveAndFlush(app);
        return appMapper.convert(app);
    }

    /**
     * 启用应用。重复启用返 409。
     */
    @Transactional
    public AppResponse enable(Long id) {
        RegisteredApp app = loadApp(id);
        app.enable();
        appRepository.saveAndFlush(app);
        return appMapper.convert(app);
    }

    private RegisteredApp loadApp(Long id) {
        return appRepository.findById(id)
                .orElseThrow(() -> new DomainException(BaseCodeMessage.RESOURCE_NOT_FOUND, id));
    }
}
