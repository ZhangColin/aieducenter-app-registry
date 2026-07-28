/**
 * OpenAPI Context。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>API Key 生命周期管理（创建、启用、禁用、查询）</li>
 *   <li>服务间签名验证的 API Key Provider</li>
 * </ul>
 *
 * <h3>限界上下文</h3>
 * <p>管理服务间通信的 API Key，为其他服务提供签名验证所需的密钥信息</p>
 *
 * <h3>包结构</h3>
 * <ul>
 *   <li>domain - 领域层：聚合根（ApiKey）、仓储接口、枚举、错误定义</li>
 *   <li>application - 应用层：应用服务、DTO、Mapper</li>
 *   <li>infrastructure - 基础设施层：ApiKeyProvider 适配器实现</li>
 *   <li>endpoints - 北向接口适配器层：REST API</li>
 * </ul>
 *
 * @since 0.1.0
 */
@BoundedContext(name = "OpenApi", subDomain = SubDomain.GENERIC)
package com.aieducenter.appregistry;

import com.cartisan.core.stereotype.BoundedContext;
import com.cartisan.core.stereotype.SubDomain;
