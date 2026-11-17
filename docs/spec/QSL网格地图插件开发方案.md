# QSL 网格地图插件开发方案

## 1. 方案状态

本文档定义 `qsl-grid-map` 插件开发边界，作为开发、联调与验收基线。

本次基线确认日期：2026-11-17。用户已确认“默认天地图，失败自动切换 OpenFreeMap，并在后台提供可持久化的首选服务选项”。本次修订依据该确认扩展地图服务范围，不以代码反向覆盖约束。

追加基线日期：2026-11-17。用户明确要求增加 `/gridmap` 短链接，沿用已确认的公开只读安全边界，精确 `GET` 请求返回 302 至固定完整地图页面；原 API 分组、版本和数据访问权限保持不变。

缺陷修复基线日期：2026-11-17。用户反馈 OpenFreeMap 通用失败提示，现场已见地图绘制完成后旧提示仍残留。本次在已确认地图功能内修正状态语义：20 秒首次绘制等待改为慢加载提示并允许后续恢复，资源错误独立记录并按对应恢复事件解除；组件资源仍保留每项 15 秒的失败超时。API、持久化字段和 RBAC 边界保持不变。

独立缩放基线日期：2026-11-17。用户确认后台按“地图显示”“天地图服务”“OpenFreeMap 服务”排序。`map（地图显示）` 共用首选服务、中心网格、本台网格开关和颜色；`tianditu（天地图服务）` 保存凭据及天地图缩放；`openfreemap（OpenFreeMap 服务）` 独立保存 OFM 缩放。运行中兜底保留当前中心点、通联网格与本台网格，仅将缩放限制到 OFM 有效范围。

实现状态：独立缩放 `0.0.24` 已完成自动化、构建、打包、热重载及浏览器验证。实际浏览器验证配置持久化、直接 OFM 缩放上下界和恢复默认配置；自动兜底与迟到数据等通过 Node 自动化验证。加载状态修复 `0.0.23`、短链接 `0.0.22` 和 OpenFreeMap `0.0.21` 的历史记录分别保留。2026-11-17 用户已恢复 Git 提交、推送及版本发布；本次发布标签为 `v0.0.24`，产物与自动发布状态以 [GitHub Release](https://github.com/bi1kbu/qsl-grid-map/releases/tag/v0.0.24) 及关联工作流为准。

历史联动验证日期：2026-06-29。

目标：开发一个基于 Halo 2 与 `qsl-management` 的业余无线电通联网格前台显示插件，地图展示效果参考 `stephenhouser/qso-mapper`，但数据来源直接使用 `qsl-management` 已提供的公开网格接口。

## 2. 官方与参考来源

第一版来源核验日期：2026-06-28。本次重新核验日期：2026-11-17，重点核对 Halo 2.25 设置读取接口、API 变更记录、权限规范和 OpenFreeMap 官方接入方式。

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
16. Halo 插件配置读取：[官方文档](https://docs.halo.run/developer-guide/plugin/api-reference/server/setting-fetcher)。
17. OpenFreeMap 接入：[官方快速开始](https://openfreemap.org/quick_start/)，推荐通过 MapLibre GL Leaflet 在 Leaflet 中使用矢量地图。
18. MapLibre GL Leaflet：[官方项目](https://github.com/maplibre/maplibre-gl-leaflet)、[图层 API](https://github.com/maplibre/maplibre-gl-leaflet/blob/main/API.md)。
19. MapLibre GL JS：[地图 API](https://maplibre.org/maplibre-gl-js/docs/API/classes/Map/)。
20. Leaflet 1.9.4：[官方 API](https://leafletjs.com/reference-1.9.4.html)，用于瓦片加载事件和图层管理。
21. MapLibre GL Leaflet：[官方变更记录](https://github.com/maplibre/maplibre-gl-leaflet/blob/main/CHANGELOG.md)，核验时 WebGL 构造失败后的移除异常修复仍位于 `Unreleased（尚未发布）` 部分。
22. 短链接专项来源（2026-11-17 核验）：[认证安全过滤器](https://docs.halo.run/developer-guide/plugin/extension-points/server/authentication-webfilter)、[Web 过滤器与重定向示例](https://docs.halo.run/developer-guide/plugin/extension-points/server/additional-webfilter)、[插件 API 变更记录](https://docs.halo.run/developer-guide/plugin/api-changelog)。
23. 加载状态修复来源（2026-11-17 核验）：MapLibre GL JS `5.24.0` 的[地图事件定义](https://raw.githubusercontent.com/maplibre/maplibre-gl-js/v5.24.0/src/ui/events.ts)、[瓦片管理器](https://raw.githubusercontent.com/maplibre/maplibre-gl-js/v5.24.0/src/tile/tile_manager.ts)与[事件传播实现](https://raw.githubusercontent.com/maplibre/maplibre-gl-js/v5.24.0/src/util/evented.ts)，并再次核对 Halo 插件 API 变更记录。

24. 独立缩放来源（2026-11-17 核验）：再次核对 Halo [配置读取](https://docs.halo.run/developer-guide/plugin/api-reference/server/setting-fetcher)与[API 变更记录](https://docs.halo.run/developer-guide/plugin/api-changelog)、Leaflet 1.9.4 [缩放范围方法](https://leafletjs.com/reference-1.9.4.html#map-setminzoom)及 MapLibre GL Leaflet 0.1.4 [固定版本分发源码](https://cdn.jsdelivr.net/npm/@maplibre/maplibre-gl-leaflet@0.1.4/leaflet-maplibre-gl.js)。

官方一致性结论：

1. Halo 2.25 官方文档推荐使用插件脚手架与 DevTools 进行插件创建、构建和运行。
2. Halo 插件强依赖应通过 `plugin.yaml` 的 `spec.pluginDependencies` 声明；本插件应声明 `qsl-management >=2.3.21`。
3. Halo 自定义 Controller API 默认只允许超级管理员访问；若使用 Controller 承载公开页面，需要额外处理匿名访问策略。
4. 当前需求明确“不创建角色模板”，因此公开页面接口不应依赖匿名聚合 Role 模板作为常规方案。
5. 短码替换应基于 Halo 主题端文章内容处理与主题端自定义页面内容处理扩展点完成。
6. 文章和页面编辑器 `/` 菜单入口应基于 Halo 默认编辑器 UI 扩展点 `default:editor:extension:create` 注册，菜单动作插入 `[qsl-grid-map]` 短码，并在编辑器内渲染为预览卡片。
6. Halo 2.25 插件上下文会注册 `reactiveSettingFetcher（响应式设置读取器）` 与 `settingFetcher（同步设置读取器）`；请求处理链路使用 `ReactiveSettingFetcher（响应式设置读取器）`，避免在 Reactor HTTP 线程中阻塞。
7. `DefaultReactiveSettingFetcher（默认响应式设置读取实现）` 通过插件 `configMapName（配置名称）` 对应的 ConfigMap 读取设置数据，因此本插件不直接读取 ConfigMap，也不维护额外配置接口。
8. 本次继续面向 Halo 2.25，不引入 2.26 专属接口。`map（地图显示）` 保存共用显示参数，`tianditu（天地图服务）` 保存凭据与天地图缩放，`openfreemap（OpenFreeMap 服务）` 保存 OFM 缩放，均通过 `ReactiveSettingFetcher.fetch（响应式配置读取）` 读取。
9. OpenFreeMap 使用官方推荐的 Leaflet 桥接方式与固定 Liberty 样式；MapLibre GL JS 固定 `5.24.0`、MapLibre GL Leaflet 固定 `0.1.4`，沿用页面脚本加载方式并启用 SRI 完整性校验。新依赖仅在使用 OpenFreeMap 时加载。
10. 短链接使用 `BeforeSecurityWebFilter（安全链前置过滤器）`，仅对精确 `GET /gridmap` 返回固定目标重定向；原公开页面仍通过同一过滤器输出 HTML，其他请求继续交给 Halo 安全链。
11. MapLibre 的完成绘制和空闲事件不能替代每个失败资源的恢复证据。加载状态分别记录慢加载、资源错误和 WebGL 上下文；收到同一瓦片成功或同一数据源元数据成功时，只解除对应错误。

12. 缩放限制由 Leaflet 地图实例统一执行。固定版本桥接库使用 `MapLibreZoom = LeafletZoom - 1（底层缩放级别比 Leaflet 小一级）`，不把相同上下界直接传给 MapLibre，避免一级偏差。

## 3. 项目定位

插件标识：`qsl-grid-map`

插件显示名称：`业余无线电 GardMap 通联网格地图`

当前版本：`0.0.24`

插件类型：Halo 2 前台展示插件。

本插件只负责展示通联网格地图，不负责管理 QSO、QSL 卡片、换卡申请、收卡记录或统计报表。所有业务数据以 `qsl-management` 为事实来源。

当前不实现自定义后台管理页、自定义配置写入接口、独立业务数据模型或数据缓存写入；插件配置通过 Halo 现有设置表单持久化。

## 4. 强依赖关系

`qsl-management` 作为强依赖。

实施时需在 `src/main/resources/plugin.yaml` 中声明插件依赖，确保 `qsl-management` 未安装或未启用时，本插件不进入可用状态。

依赖原因：

1. 本插件不持久化通联数据。
2. 本插件不自行聚合通联网格。
3. 前台地图数据直接读取 `qsl-management` 的公开网格接口。

## 5. API 合同

本插件只注册一个 `CustomEndpoint（自定义端点）` API，另提供一个固定导航短链接：

| 方法 | 路径 | 用途 | 认证要求 |
| --- | --- | --- | --- |
| `GET` | `/apis/qsl-grid-map.bi1kbu.com/v1alpha1/map/page` | 返回公开通联网格地图页面 | 匿名可访问 |
| `GET` | `/gridmap` | 302 重定向到同站点完整地图页面，不是新增数据 API | 匿名可访问 |

路径约束：

1. API group 固定为 `qsl-grid-map.bi1kbu.com`，不改为 `api.qsl-grid-map.bi1kbu.com`。
2. version 固定为 `v1alpha1`。
3. resource 路径固定为 `map/page`。
4. 当前阶段只允许 `GET` 方法。
5. `/gridmap` 不注册新的 API group/version。短链接的固定目标为 `/apis/qsl-grid-map.bi1kbu.com/v1alpha1/map/page`，部署存在 `contextPath（上下文路径）` 时，入口与重定向目标保留该前缀。
6. `/gridmap` 不透传任何查询参数，包括 `url（目标地址）`、`redirect（跳转地址）`、`embed（嵌入模式）`、`eid（嵌入实例标识）` 和所有筛选参数。目标不可由用户输入改变，不接受外部跳转地址。
7. 仅精确 `GET /gridmap` 命中短链接；非 GET 方法、尾随斜杠或相似路径均交给 Halo 处理。

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

提供短码、编辑器小组件、完整页面链接和 `/gridmap` 导航短链接四个前台入口。

公开导航短链接：

```text
/gridmap
```

短链接匿名返回 302 至固定完整地图页面；有部署前缀时保留前缀，不透传查询参数。短码 iframe 与编辑器入口继续直接引用完整页面 API。

完整页面链接：

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

编辑器小组件：

1. 在 Halo 默认编辑器中输入 `/` 后，提供 `QSL 通联网格地图` 菜单项。
2. 菜单项通过 UI 扩展点 `default:editor:extension:create` 注册 Tiptap 扩展。
3. 点击菜单项向编辑器当前位置插入 `[qsl-grid-map]`，编辑器界面通过 ProseMirror Decoration 隐藏原始短码并显示预览卡片。
4. 预览卡片只用于编辑体验，保存内容仍为 `[qsl-grid-map]`，前台渲染继续走短码替换和 iframe 嵌入逻辑。
5. 小组件不新增服务端接口，不读取业务数据，不绕过短码渲染逻辑。

## 7. 页面功能

地图渲染参考 `qso-mapper` 的交互风格，使用 Leaflet 绘制通联网格；底图支持天地图与 OpenFreeMap。默认天地图优先，失败后自动使用 OpenFreeMap，也可在后台直接首选 OpenFreeMap。

核心功能：

1. 显示四位 Maidenhead 网格。
2. 在地图上绘制网格范围或网格中心标记。
3. 点击网格后显示呼号集合、通联数量、模式、频率、频段、日期等公开字段。
4. 点击网格弹窗展示公开明细摘要。
5. 不提供右侧清单、筛选面板、时间过滤、数量限制或网格过滤。
6. 支持嵌入模式，适配 Halo 内容页宽度。
7. 空数据、接口错误、限流错误需要有中文提示。
8. 天地图与 OFM 共用初始中心网格，默认 `OM89（中国北京）`，各自独立保存默认缩放，均默认 `3`。初始化使用共用中心和实际服务的缩放参数；数据成功、空数据或迟到响应均不得重置用户或服务切换后的当前视图。
9. 后台分别配置两种服务的 `minZoom（最小缩放）` 与 `maxZoom（最大缩放）`，默认均为 `3~8`；前台不提供缩放范围测试控件。
10. 页面主体仅保留 `qsl-map-panel（地图面板）` 与地图相关 JavaScript/CSS，地图填满浏览器窗口。
11. 本台网格位置显示由后台开关控制；开启时按 `defaultCenterGrid（默认中心网格）` 绘制独立虚线边框，边框颜色由后台配置，点击后才显示“本台网格”提示。

默认地图瓦片：

```text
天地图矢量底图 vec_w + 天地图矢量注记 cva_w
```

地图服务切换约束：

1. `provider（首选地图服务）` 保存在 `map（地图显示）` 设置组，仅允许 `tianditu（天地图）` 和 `openfreemap（OpenFreeMap）`；缺省或非法值回退到天地图。
2. 首选天地图且已配置应用令牌时，加载 `vec_w（矢量底图）` 与 `cva_w（矢量注记）`。任一图层触发 `tileerror（瓦片错误）`，或任一轮瓦片加载超过 15 秒，自动切换到 OpenFreeMap。
3. 首选 OpenFreeMap 或未配置天地图应用令牌时，初始视图使用共用中心网格、本台网格显示设置及 OFM 独立默认缩放和范围，不请求天地图瓦片。
4. OpenFreeMap 样式固定为 `https://tiles.openfreemap.org/styles/liberty`，不允许通过页面参数、短码或插件配置任意指定外部地图地址。
5. 运行中兜底先移除天地图底图和注记，保留 Leaflet 地图实例、当前中心点、通联网格与本台网格；立即将缩放限制到 OFM 独立范围，超界时调整到最近边界，再加载 OFM 组件。本台网格位置和样式使用共用设置，切换不改变标记。当前页面不反复切换服务；刷新后按后台持久化首选服务重新加载。
6. OpenFreeMap 使用 MapLibre GL Leaflet，需要 WebGL。每项组件脚本或 CSS 加载限时 15 秒，失败或超时显示组件资源错误；请求地图样式后 20 秒仍未完成首次绘制时，只显示加载较慢并继续等待，完成绘制后清除慢加载提示，不锁定为终局失败，不触发循环兜底。
7. 地图服务状态与通联网格数据状态独立维护，任一侧成功不得清除另一侧错误提示。
8. 外部脚本与样式固定版本并校验 SRI，只在需要 OpenFreeMap 时加载。保留 OpenFreeMap、OpenMapTiles 和 OpenStreetMap 的完整署名。
9. 单个瓦片、数据源与其他资源错误独立保存，不能由无关瓦片成功、`load（首次绘制）` 或 `idle（空闲）` 事件统一清除。相同瓦片加载成功、相同数据源元数据成功时，分别解除对应错误；缺少对应恢复证据的错误保留提示。
10. 地图初始化、样式、瓦片和 WebGL 使用分类中文提示；WebGL 上下文丢失后，收到恢复事件先提示正在加载，重新完成绘制后才进入成功状态。通联网格数据提示继续独立维护。
11. 状态修复只反映实际加载进展和资源恢复，不承诺消除外部网络延迟或地图服务故障；OpenFreeMap Liberty 样式地址和持久化首选服务不变。

天地图服务配置：

1. `appKey（应用 key）`：可选；使用天地图时由站点管理员在 Halo 后台插件设置中填写，未填写时直接使用 OpenFreeMap。
2. `secretKey（密钥）`：可选，由站点管理员在 Halo 后台插件设置中填写。
3. 页面瓦片请求只使用 `appKey（应用 key）` 作为天地图 `tk` 参数。
4. `secretKey（密钥）` 不输出到前台页面。
5. 未配置 `appKey（应用 key）` 时，不请求天地图瓦片，直接使用 OpenFreeMap。
6. 服务端通过 Halo 官方 `ReactiveSettingFetcher.fetch("tianditu", TiandituSettings.class)` 读取插件设置，不直接读取 ConfigMap。
7. 依据 Leaflet 官方 `Control.Attribution.prefix（署名前缀）` 选项，前台关闭 Leaflet 默认前缀，按实际地图服务显示对应署名。

设置菜单与参数分组：

后台按以下顺序显示三个菜单，参数由 Halo 插件设置表单持久化：

| 菜单与设置组 | 字段 | 默认值与说明 |
| --- | --- | --- |
| 地图显示：`map（地图显示）` | `provider（首选地图服务）` | 默认 `tianditu（天地图）`，可选 `openfreemap（OpenFreeMap）` |
| 地图显示：`map（地图显示）` | `defaultCenterGrid（默认中心网格）` | 两种服务共用，四位 Maidenhead 网格，默认 `OM89（中国北京）` |
| 地图显示：`map（地图显示）` | `showHomeGrid（显示本台网格位置）` | 两种服务共用，默认开启 |
| 地图显示：`map（地图显示）` | `homeGridBorderColor（本台网格边框颜色）` | 两种服务共用，`#RRGGBB` 格式，默认 `#dc2626` |
| 天地图服务：`tianditu（天地图服务）` | `appKey（应用 key）`、`secretKey（密钥）` | 凭据规则见前文 |
| 天地图服务：`tianditu（天地图服务）` | `defaultZoom（默认缩放级别）`、`minZoom（最小缩放级别）`、`maxZoom（最大缩放级别）` | 天地图独立缩放，默认依次为 `3 / 3 / 8` |
| OpenFreeMap 服务：`openfreemap（OpenFreeMap 服务）` | `defaultZoom（默认缩放级别）`、`minZoom（最小缩放级别）`、`maxZoom（最大缩放级别）` | OFM 独立缩放，默认依次为 `3 / 3 / 8`，无需密钥 |

读取与校验规则：

1. 服务端通过官方 `ReactiveSettingFetcher.fetch（响应式配置读取）` 分别读取三个组。`MapViewSettings（地图显示配置）` 仅承载共用四字段，`TiandituSettings（天地图配置）` 在 `ZoomSettings（缩放配置）` 上增加凭据，OFM 读取为 `ZoomSettings（缩放配置）`；不直接读取 ConfigMap，不增加自定义设置接口。
2. 共用设置独立规范化：非法首选服务回退到 `tianditu（天地图）`，未设置本台网格开关时默认开启，非法中心网格回退到 `OM89`，非法边框颜色回退到 `#dc2626`。
3. 两种服务的缩放各自规范化，范围为 `1～18`；最小值大于最大值时仅该服务回退 `3～8`，默认缩放限制在本服务有效范围内。缺省值独立，不读取或复制另一服务参数。
4. 停止读取旧 `map.defaultZoom（默认缩放级别）`、`map.minZoom（最小缩放级别）` 与 `map.maxZoom（最大缩放级别）`，不新增兼容迁移层。已有自定义缩放需在对应服务菜单重新保存；共用地图显示字段保留原位置。
5. 缩放配置单位为 Leaflet 级别，OFM 通过官方桥接层同步底层缩放；切换范围后重新建立有效视图，不把同值范围传给 MapLibre。

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
4. 本插件只提供公开只读页面、固定导航短链接和 Halo 插件设置表单。
5. 页面只读取 `qsl-management` 已公开的网格数据。
6. 页面不得把 URL 或短码筛选参数透传给数据接口。
7. 页面展示字段必须限制在 `qsl-management` 公开接口返回字段内。
8. 不展示地址、电话、邮箱、通信备注、本台配置、卡片局地址、系统参数等敏感信息。

公开页面接口实现约束：

1. 官方文档说明自定义 Controller API 默认仅超级管理员可访问，因此单纯注册 Controller 不能作为匿名公开验收通过标准。
2. 在“不创建角色模板”的约束下，`BeforeSecurityWebFilter（安全链前置过滤器）` 仅精确处理 `GET /apis/qsl-grid-map.bi1kbu.com/v1alpha1/map/page` 和 `GET /gridmap` 两个路径。
3. 地图 API 分支返回地图页面 HTML；短链接分支只返回固定目标的 302 重定向，不读取插件设置或私有数据。两个分支均不写入数据、不代理外部请求。
4. 过滤器不得匹配 `/apis/qsl-grid-map.bi1kbu.com/v1alpha1/**` 或 `/gridmap/**` 通配路径，其他路径和方法全部交给 Halo 处理。
5. 若后续改用 Controller + RBAC 匿名聚合 Role 模板实现公开访问，必须先更新本文档并重新确认“无需角色模板”的需求。

攻击面控制：

1. 不提供服务端代理，降低 SSRF 风险。
2. `secretKey（密钥）` 只保存在 Halo 插件设置中，不输出到前台页面。
3. 不提供自定义写接口，配置写入复用 Halo Console 插件设置能力和其既有鉴权。
4. 嵌入页面不允许执行用户输入的脚本内容。
5. 错误信息使用中文业务提示，不输出服务端堆栈。
6. `provider（首选地图服务）` 通过服务端枚举白名单规范化；OpenFreeMap 地址固定为 HTTPS，不新增服务器出站请求或代理。
7. 天地图 `appKey（应用 key）` 仅用于天地图请求，`secretKey（密钥）` 不进入前台页面或 OpenFreeMap 请求。OpenFreeMap 无需应用令牌。
8. MapLibre 脚本与样式使用固定 CDN 版本和 SRI；自定义内容安全策略的站点需允许相应静态资源、OpenFreeMap 样式/瓦片/字体与 WebGL 工作线程。
9. 短链接仅使用服务端固定页面路径与部署上下文前缀生成 `Location（跳转地址）`，不拼接查询参数或接受任意目的地址，避免开放重定向。
10. 地图诊断使用 `console.warn（控制台警告）` 输出阶段、白名单来源标识和状态码，禁止记录请求 URL、应用令牌、异常堆栈或私有数据；瓦片身份等详细资源标识仅用于页面内关联错误与恢复。

## 10. 权限与 RBAC

第一版不创建角色模板。

原因：

1. 无自定义后台管理页。
2. 无私有查询接口。
3. 无自定义写入接口。
4. 展示数据来自 `qsl-management` 已授权公开的匿名接口。
5. 地图服务配置使用 Halo 插件 `Setting + ConfigMap` 机制；配置查看与编辑继续使用 Halo Console 既有权限映射，由服务端鉴权，页面不提供配置写入能力。

若后续增加自定义后台页面、缓存刷新、私有统计或数据导出能力，必须重新设计权限节点，至少区分 `view` 与 `edit`，并补充 Role 模板与服务端鉴权。

权限节点映射：

| 能力 | 权限节点 | 角色模板 | 说明 |
| --- | --- | --- | --- |
| 公开地图页面 | 无 | 无 | 匿名只读页面，不进入后台权限分配 |
| `/gridmap` 导航短链接 | 无 | 无 | 匿名 GET 仅跳转固定公开页面，不读取私有数据或配置、不写入数据 |
| 短码嵌入 | 无 | 无 | 通过主题端内容处理替换为 iframe |
| 编辑器小组件 | 无 | 无 | 通过 Halo 默认编辑器 UI 扩展插入 `[qsl-grid-map]`，编辑态显示预览卡片 |
| 数据读取 | 复用 `qsl-management` 公开接口权限 | 不由本插件维护 | 本插件不自行维护数据接口 |
| 地图配置查看 | 复用 Halo Console 插件设置查看权限 | 复用 Halo 既有角色，本插件不创建 | 查看天地图凭据、共用显示与两套缩放配置，受 Halo 服务端鉴权保护 |
| 地图配置编辑 | 复用 Halo Console 插件设置管理权限 | 复用 Halo 既有角色，本插件不创建 | 持久化共用地图显示、天地图凭据与两套独立缩放字段，沿用 Halo 既有查看与管理依赖关系 |
| 写入能力 | 不提供自定义权限节点 | 暂不创建 | 当前阶段禁止自定义写入接口 |

## 11. 持久化约定

第一版不新增本插件自有 Extension 数据模型。

原因：

1. `map（地图显示）` 共用四项参数、`tianditu（天地图服务）` 凭据/缩放以及 `openfreemap（OpenFreeMap 服务）` 缩放均由 Halo 插件 `Setting + ConfigMap` 机制持久化。
2. 通联网格数据由 `qsl-management` 持久化和聚合；本插件当前不持久化、传入或保存展示筛选参数。
3. 故障后切换到 OpenFreeMap 是当前页面的运行状态，不覆盖管理员持久化的首选地图服务，也不创建服务端缓存。
4. `/gridmap` 是随插件 JAR 交付的固定服务端路由，重启后仍由插件注册；不新增设置字段或数据模型，也不依赖前端内存状态。

共用地图显示、天地图凭据与两套独立缩放均使用持久化设置；后续新增默认场景或嵌入高度等配置时，必须同步更新本文档。

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
7. 同一过滤器增加精确 `GET /gridmap` 分支，返回固定完整地图路径的 302，保留部署上下文前缀，忽略所有查询参数；其他请求继续执行 Halo 过滤器链。

### 12.3 地图渲染

1. 使用 Leaflet `1.9.4`，需要 OpenFreeMap 时按需加载固定版本的 MapLibre GL JS 与 MapLibre GL Leaflet。
2. 读取 Halo 插件设置中的 `provider（首选地图服务）` 与天地图 `appKey（应用 key）`。
3. 按第 7 节选择天地图 `vec_w（矢量底图）`、`cva_w（矢量注记）` 或 OpenFreeMap Liberty 样式。
4. 实现 Maidenhead 四位网格到经纬度边界的转换。
5. 绘制网格多边形或中心标记。
6. 根据通联数量设置视觉强度。
7. 点击网格展示明细。
8. 不提供右侧清单与列表联动。
9. 天地图缺少应用令牌、底图/注记加载失败或每轮加载超过 15 秒时使用 OpenFreeMap；切换、加载失败和数据接口状态使用独立中文提示。
10. 后台共用 `map.defaultCenterGrid（默认中心网格）`，默认 `OM89`，按服务分别配置 `defaultZoom（默认缩放级别）`，各自默认 `3`；初始化完成后，数据成功、空数据或迟到响应都不得用 `fitBounds（适配范围）` 或 `setView（设置视图）` 重置当前视图。
11. 后台分别配置两种服务的 `minZoom（最小缩放）` 与 `maxZoom（最大缩放）`，默认均为 `3~8`；服务切换立即应用新范围且保留当前中心点，前台不提供临时测试控件。
12. 前台不显示当前缩放字段，缩放行为由 Leaflet 控件与后台缩放范围配置共同决定。
13. 前台按 `showHomeGrid（显示本台网格位置）` 决定是否显示本台网格虚线边框；开启时颜色由 `homeGridBorderColor（本台网格边框颜色）` 控制，点击边框后弹窗显示“本台网格”。
14. OpenFreeMap 桥接图层以 `style: null（暂不加载样式）` 初始化，取得底层 MapLibre 实例并注册加载、数据源、错误与 WebGL 状态监听后，再通过 `setStyle（设置样式）` 请求固定样式地址，避免事件监听晚于加载。首次绘制等待超过 20 秒只进入慢加载状态；后续绘制成功可恢复该状态，但不清除未恢复的资源错误。
15. 完整地图数据署名通过 `attributionControl.customAttribution（署名控件的自定义署名）` 传给桥接库，由其接入 Leaflet 署名控件。
16. 移除天地图图层时先调用 `remove（移除图层）`，让 Leaflet 完成地图事件、图层注册表和旧服务署名清理，再调用 `off（解绑图层事件）`，不得提前清除 Leaflet 的移除监听。
17. 固定版本桥接库在 WebGL 构造失败、MapLibre 实例未建立时可能出现二次移除异常。此时仅为失败图层实例替换安全的 `onRemove（移除钩子）`，再走 Leaflet 正常移除流程，释放图层注册表、事件和容器；若清理仍抛错，再解除残留地图事件并移除容器。该处理属于已知缺陷的异常清理，不是旧版兼容分支，维护条件见第 16 节。
18. 瓦片错误按 `sourceId（数据源标识）` 与 `canonical.z/x/y（规范瓦片级别与坐标）` 关联；同一数据源同坐标重新创建的瓦片对象成功后，也能解除旧错误，其他坐标或其他数据源的同坐标成功均不得误清。数据源元数据错误按数据源关联；对无法获得对应恢复证据的资源错误保留分类提示。
19. WebGL 上下文恢复后仍需等待地图完成绘制；组件加载失败、构造异常、样式错误与瓦片错误分别报告，并只输出经过限制的诊断字段。

### 12.4 短码

1. 注册 `[qsl-grid-map]` 短码。
2. 短码输出嵌入页面。
3. 短码只映射 `embed（嵌入模式）` 与 `eid（嵌入实例标识）` 查询参数。
4. 短码不复制地图业务逻辑。

### 12.5 编辑器小组件

1. 新增 `ui` 子项目，使用 Halo 官方 `@halo-dev/ui-plugin-bundler-kit` 构建 UI 扩展。
2. UI 入口注册 `default:editor:extension:create`，返回用于 Slash Command 的 Tiptap 扩展。
3. `getCommandMenuItems（获取命令菜单项）` 返回 `QSL 通联网格地图` 菜单项。
4. 菜单命令删除 `/` 输入范围并插入 `[qsl-grid-map]`。
5. 预览插件匹配独立段落中的 `[qsl-grid-map]`，隐藏原始短码文本并显示 `QSL 通联网格地图` 卡片。
6. 编辑器扩展必须从 `@halo-dev/richtext-editor` 导入 `Extension（扩展）`、`Plugin（插件）`、`Decoration（装饰）` 等对象，不得直接打包独立 `@tiptap/*` 或 ProseMirror 运行时。
5. 构建产物输出到 `src/main/resources/ui/main.js`，随插件 JAR 打包。

### 12.6 文案与界面

1. 注释、页面标题、按钮、提示信息使用中文。
2. 字段展示使用 `字段（字段释义）` 形式。
3. 第三方协议字段保留英文原文，并在附近提供中文说明。

### 12.7 构建与校验

1. 执行后端构建。
2. 执行前端 UI 构建，生成 `src/main/resources/ui/main.js`。
3. 核对插件依赖声明。
4. 核对 `CustomEndpoint（自定义端点）` API 路径只包含 `map/page`，固定短链接 `/gridmap` 仅由过滤器返回 302。
5. 核对没有自定义写入接口和角色模板。
6. 构建通过后按约定执行插件热重载。

## 13. 验收标准

1. 访问 `/apis/qsl-grid-map.bi1kbu.com/v1alpha1/map/page` 可打开公开地图页面。
2. 页面能读取 `qsl-management` 的 `/qso-public/grids` 数据并完成地图展示。
3. `[qsl-grid-map]` 短码能在 Halo 内容中嵌入同一地图页面。
4. 文章和页面编辑器输入 `/` 后能看到 `QSL 通联网格地图` 小组件，点击后显示预览卡片，保存层仍保留 `[qsl-grid-map]`。
5. 插件未创建角色模板。
6. 插件未创建任何自定义写入接口。
7. 插件未创建 `/grids`、`/settings`、`/cache/refresh` 等额外接口。
8. 插件声明 `qsl-management` 强依赖。
9. 页面不展示敏感字段。
10. 空数据、接口失败、限流失败和地图服务失败均有中文提示；天地图缺少配置、瓦片失败或超时会启用 OpenFreeMap，切换后保留当前中心点、通联网格和明细交互，缩放限制到 OFM 范围，本台网格标记与共用显示样式保持不变。
11. 构建和热重载完成后再次核对与 Halo 官方文档一致。
12. 匿名访问 `/apis/qsl-grid-map.bi1kbu.com/v1alpha1/map/page?embed=1` 返回地图页面，而不是登录页。
13. 带认证访问成功不能替代匿名公开访问验收。
14. `provider（首选地图服务）` 保存后重新打开页面仍然生效；直接选择 OpenFreeMap 时无需填写天地图应用令牌。
15. 分别验证天地图底图报错、注记报错、瓦片超时和多次并发错误；只创建一个 OpenFreeMap 图层，停止旧图层请求及超时监听。
16. 验证 OpenFreeMap 资源失败、地图失败和不支持 WebGL 的提示；数据成功不覆盖地图错误，地图成功也不覆盖数据错误。
17. 核对地图数据署名完整显示，外部地图请求不携带天地图密钥或站点登录凭据。
18. 匿名 `GET /gridmap` 返回 302，`Location（跳转地址）` 为同站点固定地图页；携带任意查询参数仍返回相同目标，部署上下文前缀正确保留。
19. 非 GET 方法、尾随斜杠和相似短链接路径不被插件过滤器放行；原页面 API、短码和 OpenFreeMap 兜底行为通过回归验证。
20. 模拟首次绘制超过 20 秒后成功，慢加载提示能恢复；模拟真实资源错误后触发无关成功或空闲事件，错误提示仍保留，只有对应瓦片或数据源恢复后解除。瓦片回归覆盖同源同坐标的新对象可恢复、不同坐标不可恢复、不同源同坐标不可恢复。
21. 验证组件资源 15 秒超时、地图初始化、样式、瓦片和 WebGL 分类提示；上下文恢复后先等待重新绘制，完成后进入成功状态。控制台诊断不得泄露 URL、应用令牌或堆栈。

22. 验证后台菜单顺序为地图显示、天地图服务、OpenFreeMap 服务；地图显示仅共用四字段，天地图凭据和自身缩放位于天地图服务，OFM 服务仅包含独立缩放。保存后重新打开仍保留，各组互不覆盖，缩放缺省值不跨组继承。
23. 使用共用中心、不同默认缩放和上下界验证直接首选 OFM、缺少天地图令牌和正常天地图各自读取正确参数；覆盖共用字段与两套缩放的非法值规范化，并确认旧 map 组缩放字段不再读取。
24. 天地图运行中失败切换时保留当前中心点，当前缩放在 OFM 范围内时保留，低于或高于范围时分别调整至最近边界；两组范围完全不相交时仍正确切换。
25. OFM 缩放控件在上下界正确禁用；切换时共用本台网格标记保留，中心网格、开关、颜色不随服务变化，通联网格及明细交互不丢失。
26. 人工移动或兜底切换后，延迟返回的空数据与正常数据均不重置当前中心点和缩放。

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

## 15. 验证记录

### 本次 OpenFreeMap 独立缩放参数（0.0.24）

日期：2026-11-17。用户确认三个菜单及“地图显示共用、缩放按服务独立”的结构，按第 7 节读取各组持久化设置并执行服务切换时的缩放规则。设计阶段已核对 Halo 2.25 配置读取与 API 变更记录、Leaflet 缩放范围方法和固定版本桥接源码；API、鉴权与角色模板边界不变。

`.\gradlew.bat test` 与 `reloadPlugin（插件热重载）` 均成功，插件已就绪，正式产物为 `build/libs/plugin-qsl-grid-map-0.0.24.jar`，日志为 `tmp/reload-ofm-zoom.log`。共 35 个 JUnit 用例通过，包括 2 个真实 JsonMapper 用例；其中 1 个 JUnit 用例执行 35 个 Node 页面行为用例，两组计数存在包含关系，不重复累计。

复用现有浏览器后台与地图页，确认三个菜单顺序与字段归属正确、地图显示共用四项、首选服务保持 OFM。将 OFM 默认/最小/最大缩放临时保存为 `5 / 4 / 6`，重新打开设置页仍保留，天地图参数仍为 `3 / 3 / 8`；地图首屏放大一次达到最大级别后加号禁用，再缩小两次达到最小级别后减号禁用。验证后 OFM 已恢复 `3 / 3 / 8` 并保存、刷新确认，地图正常显示且无失败提示，默认最小级别的减号禁用。截图为 `tmp/ofm-zoom-settings.jpg`。

真实联网验证仅覆盖直接 OFM，自动兜底的不同缩放范围、网格保留和迟到/空数据等通过 Node 自动化验证；当前开发实例无真实 QSO 业务数据。实现后已对照 Halo 2.25 设置读取、API 变更记录、Leaflet 与固定桥接版本缩放合同复核，服务端鉴权及 API/RBAC 边界保持不变。完整分项结果见 `docs/spec/项目信息结构化清单.md`。2026-11-17 用户已恢复 Git 提交、推送及版本发布；本次发布标签为 `v0.0.24`，产物与自动发布状态以 [GitHub Release](https://github.com/bi1kbu/qsl-grid-map/releases/tag/v0.0.24) 及关联工作流为准。

### 上一轮 OpenFreeMap 加载状态修复（0.0.23）

日期：2026-11-17。修复前观察到 OpenFreeMap 已显示，地图、样式与瓦片加载状态均完成，旧失败提示仍保留；主要 JavaScript、CSS 和样式请求返回 200，部分地形瓦片耗时约 16～42 秒或记录状态为 0。该事实支持修正永久失败锁和笼统错误处理，不说明网络已恢复或不存在单个失败资源。

`.\gradlew.bat test` 通过 27 个 JUnit 用例，其中 1 个执行 26 个 Node 页面行为用例，两组计数存在包含关系。慢加载恢复、资源错误精确解除、无关事件保护、WebGL 恢复、诊断脱敏与原短链接回归通过自动化验证；生产代码与 MapLibre GL JS `5.24.0` 官方事件合同已复核，API、持久化和 RBAC 边界不变。

首次 `reloadPlugin（插件热重载）` 所依赖的构建与打包成功，生成 `build/libs/plugin-qsl-grid-map-0.0.23.jar`，热重载请求因本地 `8091` 服务停止而连接被拒绝。启动 `haloServer（开发服务器）` 后，`reloadPlugin -x build（重载已构建版本）` 成功，插件已就绪。

复用原浏览器页面刷新，真实 OpenFreeMap 地图正常绘制，显示“未配置天地图应用 key，已切换至 OpenFreeMap（开放地图）。”，旧通用失败提示不再残留。另在浏览器临时模拟瓦片错误与同源同规范坐标的新对象成功事件，确认先出现局部瓦片提示、后解除对应提示，通联数据空态仍保留；临时观察器已移除，并正常刷新复核。截图为 `tmp/openfreemap-recovery.jpg`。实际正常加载与模拟事件恢复分别验证，不把后者表述为实际外网故障已修复；完整分项结果见 `docs/spec/项目信息结构化清单.md`。

### 上一轮地图短链接变更（0.0.22）

日期：2026-11-17。`.\gradlew.bat test` 通过 27 个 JUnit 用例，其中 16 个为过滤器用例，覆盖固定重定向、查询参数隔离、上下文前缀、非 GET 与相似路径处理及原页面回归；1 个 JUnit 用例内执行 16 个 Node 页面行为用例，两组计数存在包含关系。

标准 `reloadPlugin（插件热重载）` 成功，其构建与 JAR 打包依赖通过，插件已就绪，产物为 `build/libs/plugin-qsl-grid-map-0.0.22.jar`。本地匿名 `GET /gridmap` 返回 302 至固定原地图 API；附带 `url（目标地址）`、`redirect（跳转地址）` 和 `sceneType（场景类型）` 参数时仍返回相同目标且不透传参数。跟随重定向最终返回 200 地图 HTML，包含地图面板和 OpenFreeMap，无登录跳转。

实现后再次对照官方文档，保持两个精确 GET 路径，不新增自定义 API、角色模板或写入权限偏差。热重载日志为 `tmp/reload-gridmap-shortlink.log`，完整分项结果见 `docs/spec/项目信息结构化清单.md`。

### 上一轮 OpenFreeMap 变更（0.0.21）

日期：2026-11-17。最终代码修复后 `.\gradlew.bat test` 通过 13 个 JUnit 用例，其中 1 个用例执行 16 个 Node 页面行为用例，两组计数存在包含关系。`haloServer（开发服务器）` 的构建依赖已成功生成 `build/libs/plugin-qsl-grid-map-0.0.21.jar`。

浏览器验证使用 Java 实际渲染页面与本地合成 QSO 数据，使用 `invalid-test-key（测试用无效应用令牌）` 触发天地图失败，确认自动切换后真实 OpenFreeMap 底图与署名正常显示；继续缩放、点击 `OM89（本台网格）` 弹窗通过。直接首选 OpenFreeMap 时正常显示，无失败提示；浏览器控制台无错误。截图记录为 `tmp/openfreemap-fallback.jpg`。该验证不代表真实业务数据联动。

独立 Halo 2.25 环境通过官方固定插件路径加载依赖，标准 `reloadPlugin（插件热重载）` 成功，插件已就绪。热重载后公开地图页和 QSO 网格接口均匿名返回 200；浏览器确认未配置天地图应用令牌时 OpenFreeMap 正常显示。新建实例无真实通联记录，明细交互采用上述合成数据验证。实现后已复核官方设置接口、API/权限边界、地图桥接与署名要求。完整分项结果见 `docs/spec/项目信息结构化清单.md`。

### 历史验证

验证日期：2026-06-29

以下仅为当时的天地图版本验证记录，不代表本次 OpenFreeMap 兜底已通过验收；其中临时截图不随仓库交付。

构建验证：

| 命令 | 结果 |
| --- | --- |
| `.\gradlew.bat test` | 通过 |
| `.\gradlew.bat build` | 通过 |
| `.\gradlew.bat reloadPlugin` | 通过，插件已就绪 |
| `pnpm build`（`ui` 子项目） | 通过，生成 `src/main/resources/ui/main.js` |

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
| 默认地图视图 | 通过，页面输出 `defaultCenterGrid（默认中心网格）= OM89` 与 `defaultZoom（默认缩放级别）= 3`，旧硬编码坐标已移除，数据加载后不再自动 `fitBounds（适配范围）` 覆盖后台默认视图 |
| 默认视图截图 | 通过，`tmp/qsl-grid-map-default-view-om89-no-fitbounds.png` 确认数据加载后仍保留 OM89 默认视图 |
| 纯地图窗口 | 通过，仅保留 `qsl-map-panel（地图面板）` 与 `#qsl-map（地图容器）`，面板 `position=fixed` 且 `inset=0`，填满浏览器窗口 |
| 旧面板移除 | 通过，未发现筛选表单、右侧清单、数量限制、当前缩放显示或前台缩放范围测试控件 |
| 纯地图截图 | 通过，`tmp/qsl-grid-map-pure-full-window.png` 确认天地图底图、中文注记与网格覆盖层正常显示 |
| 后台缩放范围默认值 | 通过，页面从后台配置输出 `minZoom（最小缩放）= 3` 与 `maxZoom（最大缩放）= 8` |
| 插件设置元数据 | 通过，已注册 `settingName（设置名称）` 与 `configMapName（配置名称）` |
| 编辑器小组件资源 | 通过，已生成 `src/main/resources/ui/main.js` 并接入 Gradle `processResources` |
| 编辑器 `/` 菜单 | 通过，文章编辑器输入 `/` 后出现 `QSL 通联网格地图` |
| 编辑器预览卡片 | 通过，点击菜单项后显示 `QSL 通联网格地图` 预览卡片；DOM 验证 `cardCount（卡片数量）= 1`、`hiddenCount（隐藏短码数量）= 1`、保存层仍包含 `[qsl-grid-map]` |
| 编辑器小组件截图 | 通过，`tmp/qsl-grid-map-editor-slash-widget.png` 确认菜单项显示；`tmp/qsl-grid-map-editor-preview-card.png` 确认预览卡片显示 |

已验证的 `qsl-management` 数据接口：

```text
GET /apis/api.qsl-management.bi1kbu.com/v1alpha1/qso-public/grids?sceneType=QSO
```

## 16. 偏差表

| 偏差项 | 偏差原因 | 影响与风险 | 回退或修正方案 |
| --- | --- | --- | --- |
| 当前无未解决偏差 | 不适用 | 不适用 | 不适用 |

本次需求变更记录：2026-11-17 用户批准了 OpenFreeMap 第二选项、自动兜底和持久化首选服务。本文件已按该批准修订原“仅天地图”及“缺少应用令牌时仅提示”的规则；不属于实现超出已确认范围。API 合同、服务端鉴权和角色模板边界无新增偏差。

追加需求变更记录：同日用户明确新增 `/gridmap`。本文件据此将安全链前置过滤器的授权范围修订为原地图页面与固定短链接两个精确 GET 路径；原 `CustomEndpoint（自定义端点）` API 的分组、版本和路径保持不变。短链接不透传参数、不读取设置或私有数据，不新增角色模板或自定义写入权限，无超出本次已确认基线的偏差。

缺陷修复记录：同日用户反馈地图失败提示。依据实际“地图完成绘制但旧错误残留”的现象，将原“首次绘制 20 秒即失败”修订为“20 秒提示较慢，继续等待并允许成功恢复”，同时独立追踪真实资源错误。此项属于已确认地图功能的状态语义修正，不扩大 API、持久化或权限范围，也不把外部网络故障记为已修复。

独立缩放需求记录：同日用户确认三个设置菜单：地图显示保留共用字段，天地图服务和 OpenFreeMap 服务分别持久化缩放参数。运行中切换保留中心与网格，缩放调整到 OFM 范围，数据响应不得重置视图；按版本兼容约定停止读取原 map 组缩放字段，管理员通过对应服务菜单重新保存自定义值。这是用户确认范围内的基线修订，不新增 API、角色模板或配置写入通道。

第三方缺陷防护记录：

| 防护项 | 原因 | 影响与风险 | 回退或修正方案 |
| --- | --- | --- | --- |
| WebGL 初始化失败后的图层异常清理 | MapLibre GL Leaflet `0.1.4` 在 MapLibre 实例未建立时移除图层可能再次抛错；官方修复尚未发布 | 残留注册表和事件可能影响 Leaflet 平移、缩放和通联网格交互；仅在初始化异常且 MapLibre 实例不存在时，为该失败图层安装安全移除钩子，让 Leaflet 完成正常释放；清理异常另有事件与容器兜底 | 官方修复版本发布后升级固定版本和 SRI，回归验证 WebGL 不可用、正常加载及图层注册表、事件和署名清理，再删除补偿清理；不直接移除防护而恢复已知错误 |

已关闭偏差：

| 偏差项 | 修正方式 | 修正结果 |
| --- | --- | --- |
| Controller 页面接口带认证可访问，但匿名访问被 Halo 拦截 | 新增只匹配 `GET /apis/qsl-grid-map.bi1kbu.com/v1alpha1/map/page` 的 `BeforeSecurityWebFilter` | 匿名访问返回地图页面，不进入登录页 |
| 请求链路曾使用同步 `SettingFetcher（设置读取器）` 读取配置 | 改为 Halo 官方 `ReactiveSettingFetcher（响应式设置读取器）` | 避免 Reactor HTTP 线程阻塞，天地图配置可在匿名页面正常生效 |
| 数据加载完成后自动 `fitBounds（适配范围）` 到全量通联网格，导致默认中心点被覆盖 | 移除有数据时的自动视图移动，只绘制网格图层 | 后台 `defaultCenterGrid（默认中心网格）` 与 `defaultZoom（默认缩放级别）` 在刷新后保持生效 |

## 17. CI/CD

CI/CD 复用 `halo-sigs/reusable-workflows`：

| 文件 | 触发条件 | 说明 |
| --- | --- | --- |
| `.github/workflows/ci.yaml` | `push（推送）` 与 `pull_request（拉取请求）` 到 `main` | 执行 Halo 插件 CI 构建检查 |
| `.github/workflows/cd.yaml` | GitHub Release `published（已发布）` | 使用仓库密钥 `HALO_PAT` 发布插件 Release 产物，并提交到 Halo 应用市场 |

CD 参数：

1. `app-id（应用市场应用标识）= app-rftgoicv`
2. `ui-path（前端目录）= ui`
3. `pnpm-version（pnpm 版本）= 10`
4. `node-version（Node.js 版本）= 24`
5. `java-version（Java 版本）= 21`
