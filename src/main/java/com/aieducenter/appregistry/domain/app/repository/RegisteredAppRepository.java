package com.aieducenter.appregistry.domain.app.repository;

import com.aieducenter.appregistry.domain.app.aggregate.RegisteredApp;
import com.cartisan.data.jpa.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 已登记应用仓储。
 *
 * @since 0.1.0
 */
public interface RegisteredAppRepository extends BaseRepository<RegisteredApp, Long> {

    /**
     * app_code 是否已存在（含软删行）。
     *
     * <p>用原生查询绕过 {@code @SQLRestriction}，看到含软删的全行——满足「app_code 全局唯一、
     * 含软删行不可复用」（ADR-0001 §6）。DB 普通唯一约束作并发兜底。</p>
     *
     * @param appCode 应用编码
     * @return 任一行（含已软删）占用该 app_code 则 true
     */
    @Query(value = "SELECT EXISTS(SELECT 1 FROM ar_registered_apps WHERE app_code = :appCode)", nativeQuery = true)
    boolean existsByAppCode(@Param("appCode") String appCode);
}
