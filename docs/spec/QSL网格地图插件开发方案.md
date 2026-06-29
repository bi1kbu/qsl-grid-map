# QSL 网格地图插件开发方案

## 1. 方案状态

本文档定义 `qsl-grid-map` 插件第一版开发边界，作为开发、联调与验收基线。

核验日期：2026-06-28

实现状态：第一版已实现并通过两个插件的联动调试。

最近验证日期：2026-06-29

目标：开发一个基于 Halo 2 与 `qsl-management` 的业余无线电通联网格前台显示插件，地图展示效果参考 `stephenhouser/qso-mapper`，但数据来源直接使用 `qsl-management` 已提供的公开网格接口。

## 2. 官方与参考来源

官方来源核验日期：2026-06-28。

1. Halo 插件入门：`https://docs.halo.run/developer-guide/plugin/hello-world`
2. Halo API 权限控制：`https://docs.halo.run/developer-guide/plugin/security/role-template`
3. Halo 插件依赖：`https://docs.halo.run/developer-guide/plugin/interaction/dependency`
4. Halo Web 过滤器：`https://docs.halo.run/developer-guide/plugin/extension-points/server/additional-webfilter`
5. Halo 认证安全过滤器：`https://docs.halo.run/developer-guide/plugin/extension-points/server/authentication-webfilter`
6. Halo 主题端文章内容处理：`https://docs.halo.run/developer-guide/plugin/extension-points/server/post-content`
7. Halo 主题端自定义页面内容处理：`https://docs.halo.run/developer-guide/plugin/extension-points/server/singlepage-content`
8. Halo 插件 API 变更记录：`https://docs.halo.run/developer-guide/plugin/api-changelog`
9. 天地图服务授权说明：`http://lbs.tianditu.gov.cn/authorization/authorization.html`
10. Halo 2.25 `ReactiveSettingFetcher` 源码：`https://raw.githubusercontent.com/halo-dev/halo/v2.25.0/api/src/main/java/run/halo/app/plugin/ReactiveSettingFetcher.java`
11. Halo 2.25 插件上下文源码：`https://raw.githubusercontent.com/halo-dev/halo/v2.25.0/application/src/main/java/run/halo/app/plugin/DefaultPluginApplicationContextFactory.java`
12. Halo 2.25 默认响应式设置读取实现：`https://raw.githubusercontent.com/halo-dev/halo/v2.25.0/application/src/main/java/run/halo/app/plugin/DefaultReactiveSettingFetcher.java`
13. 天地图服务授权说明：`http://lbs.tianditu.gov.cn/authorization/authorization.html`
14. `qsl-management` 项目：`https://github.com/bi1kbu/qsl-management`
15. `qso-mapper` 项目：`https://github.com/stephenhouser/qso-mapper`

官方一致性结论：

1. Halo 2.25 官方文档推荐使用插件脚手架与 DevTools 进行插件创建、构建和运行。
2. Halo 插件强依赖应通过 `plugin.yaml` 的 `spec.pluginDependencies` 声明；本插件应声明 `qsl-management >=2.3.21`。
3. Halo 自定义 Controller API 默认只允许超级管理员访问；若使用 Controller 承载公开页面，需要额外处理匿名访问策略。
4. 当前需求明确“不创建角色模板”，因此公开页面接口不应依赖匿名聚合 Role 模板作为常规方案。
5. 短码替换应基于 Halo 主题端文章内容处理与主题端自定义页面内容处理扩展点完成。
6. Halo 2.25 插件上下文会注册 `reactiveSettingFetcher（响应式设置读取器）` 与 `settingFetcher（同步设置读取器）`；请求处理链路使用 `ReactiveSettingFetcher（响应式设置读取器）`，避免在 Reactor HTTP 线程中阻塞。
7. `DefaultReactiveSettingFetcher（默认响应式设置读取实现）` 通过插件 `configMapName（配置名称）` 对应的 ConfigMap 读取设置数据，因此本插件不直接读取 ConfigMap，也不维护额外配置接口。

## 3. 项目定位

插件标识：`qsl-grid-map`

插件显示名称：`QSL 通联网格地图`

当前版本：`0.0.13`

插件类型：Halo 2 前台展示插件。

本插件只负责展示通联网格地图，不负责管理 QSO、QSL 卡片、换卡申请、收卡记录或统计报表。所有业务数据以 `qsl-management` 为事实来源。

第一版不实现后台管理页，不实现配置写入，不实现独立数据模型，不实现数据缓存写入。

## 4. 强依赖关系

`qsl-management` 作为强依赖。

实施时需在 `src/main/resources/plugin.yaml` 中声明插件依赖，确保 `qsl-management` 未安装或未启用时，本插件不进入可用状态。

依赖原因：

1. 本插件不持久化通联数据。
2. 本插件不自行聚合通联网格。
3. 前台地图数据直接读取 `qsl-management` 的公开网格接口。

## 5. API 合同

本插件第一版只提供一个 API 地址：

| 方法 | 路径 | 用途 | 认证要求 |
| --- | --- | --- | --- |
| `GET` | `/apis/qsl-grid-map.bi1kbu.com/v1alpha1/map/page` | 返回公开通联网格地图页面 | 匿名可访问 |

路径约束：

1. API group 固定为 `qsl-grid-map.bi1kbu.com`，不改为 `api.qsl-grid-map.bi1kbu.com`。
2. version 固定为 `v1alpha1`。
3. resource 路径固定为 `map/page`。
4. 当前阶段只允许 `GET` 方法。

不得新增以下接口：

1. 不新增 `/grids` 代理接口。
2. 不新增 `/settings` 配置接口。
3. 不新增 `/cache/refresh` 缓存接口。
4. 不新增任何 `POST`、`PUT`、`PATCH`、`DELETE` 写入接口。

页面参数约束：

| 参数 | 含义 | 说明 |
| --- | --- | --- |
| `embed` | 嵌入模式 | 可选，短码嵌入时使用 |
| `eid` | 嵌入实例标识 | 可选，短码 iframe 高度回传时使用 |

页面不再接收或透传 `sceneType（场景类型）`、`dateFrom（开始日期）`、`dateTo（结束日期）`、`grid（网格）`、`limit（明细数量上限）` 等筛选参数。前台固定请求 QSO 网格数据，不限制时间、数量或网格。

页面内数据读取地址：

```text
/apis/api.qsl-management.bi1kbu.com/v1alpha1/qso-public/grids
```

页面加载后由浏览器直接请求上述 `qsl-management` 公开接口。本插件不在服务端维护额外的只读聚合接口。

## 6. 短码设计

第一版提供短码和页面链接两个前台入口。

页面链接：

```text
/apis/qsl-grid-map.bi1kbu.com/v1alpha1/map/page
```

短码：

```text
[qsl-grid-map]
```

短码渲染方式：

1. 短码输出一个嵌入页面容器，建议使用 `iframe` 或等价嵌入方式。
2. 嵌入目标复用 `/apis/qsl-grid-map.bi1kbu.com/v1alpha1/map/page?embed=true`。
3. 短码不单独实现地图渲染逻辑。
4. 短码仅拼接 `embed（嵌入模式）` 与 `eid（嵌入实例标识）`，不接收筛选参数，不直接访问业务数据。

短码参数约束：

```text
[qsl-grid-map]
```

短码不再解析筛选属性；即使内容中保留旧属性，也不会拼接到 iframe 地址或影响地图数据请求。

## 7. 页面功能

地图渲染参考 `qso-mapper` 的交互风格，采用 Leaflet + 天地图矢量瓦片作为第一版实现方向。

核心功能：

1. 显示四位 Maidenhead 网格。
2. 在地图上绘制网格范围或网格中心标记。
3. 点击网格后显示呼号集合、通联数量、模式、频率、频段、日期等公开字段。
4. 点击网格弹窗展示公开明细摘要。
5. 不提供右侧清单、筛选面板、时间过滤、数量限制或网格过滤。
6. 支持嵌入模式，适配 Halo 内容页宽度。
7. 空数据、接口错误、限流错误需要有中文提示。
8. 初始中心点和空数据回退中心点默认为 `OM89（中国北京）`，默认缩放级别为 `3`，并支持后台配置。
9. 后台可配置 `minZoom（最小缩放）` 与 `maxZoom（最大缩放）`，默认 `3~8`；前台不再提供缩放范围测试控件。
10. 页面主体仅保留 `qsl-map-panel（地图面板）` 与地图相关 JavaScript/CSS，地图填满浏览器窗口。

第一版默认地图瓦片：

```text
天地图矢量底图 vec_w + 天地图矢量注记 cva_w
```

天地图服务配置：

1. `appKey（应用 key）`：必填，由站点管理员在 Halo 后台插件设置中填写。
2. `secretKey（密钥）`：可选，由站点管理员在 Halo 后台插件设置中填写。
3. 页面瓦片请求只使用 `appKey（应用 key）` 作为天地图 `tk` 参数。
4. `secretKey（密钥）` 不输出到前台页面。
5. 未配置 `appKey（应用 key）` 时，地图区域显示中文提示，不请求天地图瓦片。
6. 服务端通过 Halo 官方 `ReactiveSettingFetcher.fetch("tianditu", TiandituSettings.class)` 读取插件设置，不直接读取 ConfigMap。
7. 依据 Leaflet 官方 `Control.Attribution.prefix（署名前缀）` 选项，前台关闭 Leaflet 默认前缀，仅保留天地图署名。

地图显示配置：

1. `defaultCenterGrid（默认中心网格）`：必填，四位 Maidenhead 网格，默认 `OM89（中国北京）`。
2. `defaultZoom（默认缩放级别）`：必填，范围 `1` 到 `18`，默认 `3`。
3. `minZoom（最小缩放级别）`：必填，范围 `1` 到 `18`，默认 `3`。
4. `maxZoom（最大缩放级别）`：必填，范围 `1` 到 `18`，默认 `8`。
5. 配置值由 Halo 插件设置表单保存，服务端通过官方 `ReactiveSettingFetcher.fetch("map", MapViewSettings.class)` 读取。
6. 服务端对中心网格和缩放级别做兜底规范化，非法中心网格回退到 `OM89`，非法缩放限制在 `1` 到 `18`；若最小缩放大于最大缩放，则回退默认 `3~8`。

## 8. 数据来源

数据唯一来源为 `qsl-management` 公开接口：

```text
GET /apis/api.qsl-management.bi1kbu.com/v1alpha1/qso-public/grids
```

已确认的接口约束：

1. 匿名可访问。
2. 支持 `sceneType/dateFrom/dateTo/grid/limit` 查询参数，但本插件当前固定只使用 `sceneType=QSO`。
3. 按对方四位 Maidenhead 网格聚合。
4. 从 `QsoRecord.spec.qth` 读取对方 QTH。
5. 仅当 QTH 整体为 4、6、8 位 Maidenhead 网格时纳入清单。
6. 6 位或 8 位网格统一截取前四位。
7. 返回去重呼号集合与通联明细。
8. 明细字段仅包含呼号、日期、时间、时区、模式、频率、频段等公开字段。
9. 不返回地址、备注、本台信息等敏感字段。
10. `limit` 默认 `500`，最大 `2000`，但本插件当前不传入 `limit（明细数量上限）`。

本插件不得绕过该公开接口直接读取 `qsl-management` 的 Extension 数据，除非后续重新评审并更新本文档。

## 9. 安全设计

第一版安全边界：

1. 本插件不创建角色模板。
2. 本插件不创建自定义写入权限。
3. 本插件不创建自定义后台管理接口。
4. 本插件只提供公开只读页面和 Halo 插件设置表单。
5. 页面只读取 `qsl-management` 已公开的网格数据。
6. 页面不得把 URL 或短码筛选参数透传给数据接口。
7. 页面展示字段必须限制在 `qsl-management` 公开接口返回字段内。
8. 不展示地址、电话、邮箱、通信备注、本台配置、卡片局地址、系统参数等敏感信息。

公开页面接口实现约束：

1. 官方文档说明自定义 Controller API 默认仅超级管理员可访问，因此单纯注册 Controller 不能作为匿名公开验收通过标准。
2. 在“不创建角色模板”的约束下，匿名公开页面优先通过只匹配 `GET /apis/qsl-grid-map.bi1kbu.com/v1alpha1/map/page` 的服务端 Web 过滤器实现。
3. 该过滤器只返回静态地图页面 HTML，不读取私有数据，不写入数据，不代理外部请求。
4. 过滤器不得扩大匹配范围，不得匹配 `/apis/qsl-grid-map.bi1kbu.com/v1alpha1/**` 全路径。
5. 若后续改用 Controller + RBAC 匿名聚合 Role 模板实现公开访问，必须先更新本文档并重新确认“无需角色模板”的需求。

攻击面控制：

1. 不提供服务端代理，降低 SSRF 风险。
2. `secretKey（密钥）` 只保存在 Halo 插件设置中，不输出到前台页面。
3. 不提供自定义写接口，配置写入复用 Halo Console 插件设置能力和其既有鉴权。
4. 嵌入页面不允许执行用户输入的脚本内容。
5. 错误信息使用中文业务提示，不输出服务端堆栈。

## 10. 权限与 RBAC

第一版不创建角色模板。

原因：

1. 无自定义后台管理页。
2. 无私有查询接口。
3. 无自定义写入接口。
4. 展示数据来自 `qsl-management` 已授权公开的匿名接口。
5. 天地图配置使用 Halo 插件 `Setting + ConfigMap` 机制，后台保存动作复用 Halo Console 既有插件设置权限。

若后续增加自定义后台页面、缓存刷新、私有统计或数据导出能力，必须重新设计权限节点，至少区分 `view` 与 `edit`，并补充 Role 模板与服务端鉴权。

权限节点映射：

| 能力 | 权限节点 | 角色模板 | 说明 |
| --- | --- | --- | --- |
| 公开地图页面 | 无 | 无 | 匿名只读页面，不进入后台权限分配 |
| 短码嵌入 | 无 | 无 | 通过主题端内容处理替换为 iframe |
| 数据读取 | 复用 `qsl-management` 公开接口权限 | 不由本插件维护 | 本插件不自行维护数据接口 |
| 天地图配置 | 复用 Halo Console 插件设置权限 | 本插件不创建 | `appKey（应用 key）` 必填，`secretKey（密钥）` 可选 |
| 写入能力 | 不提供自定义权限节点 | 暂不创建 | 当前阶段禁止自定义写入接口 |

## 11. 持久化约定

第一版不新增本插件自有 Extension 数据模型。

原因：

1. 天地图服务配置由 Halo 插件 `Setting + ConfigMap` 机制持久化。
2. 通联网格数据由 `qsl-management` 持久化和聚合；本插件当前不持久化、传入或保存展示筛选参数。
3. 通联网格数据由 `qsl-management` 持久化和聚合。

若后续需要站点级默认中心点、默认缩放、默认场景或嵌入高度，必须新增配置字段并同步更新本文档。

## 12. 技术实施计划

### 12.1 工程初始化

1. 使用 Halo 官方推荐方式创建插件工程。
2. 插件标识设置为 `qsl-grid-map`。
3. 插件元数据声明依赖 `qsl-management`。
4. 目标 Halo 版本按当前官方文档目标版本实现，不新增旧版兼容层。

### 12.2 页面接口

1. 实现 `GET /apis/qsl-grid-map.bi1kbu.com/v1alpha1/map/page`。
2. 返回完整公开地图页面。
3. 页面静态资源随插件打包。
4. 页面脚本直接请求 `qsl-management` 公开网格接口。
5. 匿名访问必须在无登录态下验证通过，不能只验证 Basic Auth 或管理员会话。
6. 若 Controller 方式被 Halo 安全链拦截，应改为符合第 9 节约束的只读 Web 过滤器方式。

### 12.3 地图渲染

1. 引入 Leaflet。
2. 读取 Halo 插件设置中的天地图 `appKey（应用 key）`。
3. 使用天地图 `vec_w（矢量底图）` 和 `cva_w（矢量注记）` 瓦片。
4. 实现 Maidenhead 四位网格到经纬度边界的转换。
5. 绘制网格多边形或中心标记。
6. 根据通联数量设置视觉强度。
7. 点击网格展示明细。
8. 不提供右侧清单与列表联动。
9. 未配置 `appKey（应用 key）` 或瓦片加载失败时显示中文提示。
10. 后台可配置 `defaultCenterGrid（默认中心网格）` 与 `defaultZoom（默认缩放级别）`，默认分别为 `OM89` 与 `3`。
11. 后台可配置 `minZoom（最小缩放）` 与 `maxZoom（最大缩放）`，默认 `3~8`；前台不提供临时测试控件。
12. 前台不显示当前缩放字段，缩放行为由 Leaflet 控件与后台缩放范围配置共同决定。

### 12.4 短码

1. 注册 `[qsl-grid-map]` 短码。
2. 短码输出嵌入页面。
3. 短码只映射 `embed（嵌入模式）` 与 `eid（嵌入实例标识）` 查询参数。
4. 短码不复制地图业务逻辑。

### 12.5 文案与界面

1. 注释、页面标题、按钮、提示信息使用中文。
2. 字段展示使用 `字段（字段释义）` 形式。
3. 第三方协议字段保留英文原文，并在附近提供中文说明。

### 12.6 构建与校验

1. 执行后端构建。
2. 执行前端构建。
3. 核对插件依赖声明。
4. 核对 API 路径只包含 `map/page`。
5. 核对没有自定义写入接口和角色模板。
6. 构建通过后按约定执行插件热重载。

## 13. 验收标准

1. 访问 `/apis/qsl-grid-map.bi1kbu.com/v1alpha1/map/page` 可打开公开地图页面。
2. 页面能读取 `qsl-management` 的 `/qso-public/grids` 数据并完成地图展示。
3. `[qsl-grid-map]` 短码能在 Halo 内容中嵌入同一地图页面。
4. 插件未创建角色模板。
5. 插件未创建任何自定义写入接口。
6. 插件未创建 `/grids`、`/settings`、`/cache/refresh` 等额外接口。
7. 插件声明 `qsl-management` 强依赖。
8. 页面不展示敏感字段。
9. 空数据、接口失败、限流失败、天地图未配置或瓦片失败均有中文提示。
10. 构建和热重载完成后再次核对与 Halo 官方文档一致。
11. 匿名访问 `/apis/qsl-grid-map.bi1kbu.com/v1alpha1/map/page?embed=1` 返回地图页面，而不是登录页。
12. 带认证访问成功不能替代匿名公开访问验收。

## 14. 暂不实现内容

1. 自定义后台配置页。
2. 角色模板。
3. 自定义写入接口。
4. 本插件自有网格数据接口。
5. 本插件自有数据缓存。
6. MapBox Token 配置。
7. 私有统计接口。
8. 独立 QSO、ADIF 或 QSL 数据管理。

上述能力如需增加，必须先更新本文档并重新确认设计基线。

## 15. 当前验证记录

验证日期：2026-06-29

构建验证：

| 命令 | 结果 |
| --- | --- |
| `.\gradlew.bat test` | 通过 |
| `.\gradlew.bat build` | 通过 |
| `.\gradlew.bat reloadPlugin` | 通过，插件已就绪 |

联动验证：

| 项目 | 结果 |
| --- | --- |
| `qsl-management` 插件状态 | `STARTED` |
| `qsl-grid-map` 插件状态 | `STARTED` |
| `qsl-management` 公开网格接口 | 匿名访问通过，返回成功响应 |
| 本插件页面接口，匿名访问 | 通过，返回地图页面，不进入登录页 |
| 浏览器页面联动 | 通过，页面实际请求 `qsl-management` 公开网格接口并返回 200 |
| 浏览器控制台 | 无错误消息 |
| 天地图未配置状态 | 通过，显示后台配置提示，不请求天地图瓦片 |
| 天地图已配置状态 | 通过，`vec_w（矢量底图）` 与 `cva_w（矢量注记）` 瓦片请求返回 200 |
| 浏览器截图 | 通过，`tmp/qsl-grid-map-tianditu-key-configured.png` 确认地图底图与中文注记正常显示 |
| 默认地图视图 | 通过，页面输出 `defaultCenterGrid（默认中心网格）= OM89` 与 `defaultZoom（默认缩放级别）= 3`，旧硬编码坐标已移除 |
| 默认视图截图 | 通过，`tmp/qsl-grid-map-default-view-om89.png` 确认页面加载完成 |
| 纯地图窗口 | 通过，仅保留 `qsl-map-panel（地图面板）` 与 `#qsl-map（地图容器）`，面板 `position=fixed` 且 `inset=0`，填满浏览器窗口 |
| 旧面板移除 | 通过，未发现筛选表单、右侧清单、数量限制、当前缩放显示或前台缩放范围测试控件 |
| 纯地图截图 | 通过，`tmp/qsl-grid-map-pure-full-window.png` 确认天地图底图、中文注记与网格覆盖层正常显示 |
| 后台缩放范围默认值 | 通过，页面从后台配置输出 `minZoom（最小缩放）= 3` 与 `maxZoom（最大缩放）= 8` |
| 插件设置元数据 | 通过，已注册 `settingName（设置名称）` 与 `configMapName（配置名称）` |

已验证的 `qsl-management` 数据接口：

```text
GET /apis/api.qsl-management.bi1kbu.com/v1alpha1/qso-public/grids?sceneType=QSO
```

## 16. 偏差表

| 偏差项 | 偏差原因 | 影响与风险 | 回退或修正方案 |
| --- | --- | --- | --- |
| 当前无未解决偏差 | 不适用 | 不适用 | 不适用 |

已关闭偏差：

| 偏差项 | 修正方式 | 修正结果 |
| --- | --- | --- |
| Controller 页面接口带认证可访问，但匿名访问被 Halo 拦截 | 新增只匹配 `GET /apis/qsl-grid-map.bi1kbu.com/v1alpha1/map/page` 的 `BeforeSecurityWebFilter` | 匿名访问返回地图页面，不进入登录页 |
| 请求链路曾使用同步 `SettingFetcher（设置读取器）` 读取配置 | 改为 Halo 官方 `ReactiveSettingFetcher（响应式设置读取器）` | 避免 Reactor HTTP 线程阻塞，天地图配置可在匿名页面正常生效 |
