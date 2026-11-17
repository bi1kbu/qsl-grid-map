# 业余无线电 GardMap 通联网格地图项目文档索引

2026-11-17／0.0.25：[QSL 2.x／3.0适配基线](qsl-v3-compatibility.md)已实现，自动化与隔离构建通过；真实Halo双版本联调由主任务继续，尚不记为部署通过。56个JUnit全部通过，其中1个执行43个Node页面用例，两组不可相加。保留0.0.24地图服务及独立缩放。构建输出临时使用tmp/build-v3-compat，避开既有dev服务器锁住的class文件；日志tmp/qsl-v3-build-final.log。

本文档是 `qsl-grid-map` 插件的结构化文档入口。

## 约束性文档

1. `agents.md`：仓库协作、Halo2 插件开发、安全、构建与文档同步约定。
2. `docs/spec/QSL网格地图插件开发方案.md`：产品边界、API 合同、安全设计、短码方案与验收标准，包含 2026-11-17 用户确认的 OpenFreeMap 兜底、地图短链接和 OFM 独立缩放参数基线。

## 输出性文档

1. `docs/spec/项目信息结构化清单.md`：当前工程、接口、短码、依赖与验证状态清单。

## 当前实现摘要

1. 插件标识：`qsl-grid-map`
2. 插件显示名称：`业余无线电 GardMap 通联网格地图`
3. 当前版本：`0.0.24`
4. Halo 目标版本：`>=2.25.0`
5. 强依赖插件：`qsl-management >=2.3.21`
6. 唯一插件 API：`GET /apis/qsl-grid-map.bi1kbu.com/v1alpha1/map/page`
7. 数据来源：`GET /apis/api.qsl-management.bi1kbu.com/v1alpha1/qso-public/grids`
8. 短码：`[qsl-grid-map]`
9. 编辑器小组件：默认编辑器输入 `/` 后选择 `QSL 通联网格地图`，编辑态显示预览卡片，保存层保留 `[qsl-grid-map]`
10. CI/CD：复用 `halo-sigs/reusable-workflows`；CI 在 `main` 分支推送与 Pull Request 触发，CD 在 GitHub Release 发布时触发并发布到 Halo 应用市场 `app-rftgoicv`
11. 地图服务：`map.provider（首选地图服务）` 由 Halo 持久化，默认天地图，可直接选择 OpenFreeMap；天地图底图或注记报错、每轮加载超时 15 秒后单向切换 OpenFreeMap，缺少应用令牌时直接使用 OpenFreeMap。
12. 地图切换：保留当前中心点与通联网格，缩放超出 OFM 范围时调整到最近边界，本台网格标记与显示样式保留；地图服务与数据接口提示独立管理。OpenFreeMap 使用官方 Liberty 样式及 MapLibre GL Leaflet，需要 WebGL。
13. 地图短链接：匿名 `GET /gridmap` 返回 302 至固定完整地图页；保留部署上下文前缀，不透传任何查询参数，不读取设置或私有数据。唯一插件 API 的 group/version 保持不变。
14. OpenFreeMap 状态：首次绘制超过 20 秒提示加载较慢，完成后恢复；组件资源仍每项限时 15 秒。地图资源错误独立记录并按对应资源恢复，避免已显示地图仍残留通用失败提示，也避免无关成功掩盖真实错误。

15. 设置菜单依次为“地图显示”“天地图服务”“OpenFreeMap 服务”：`map（地图显示）` 保存首选服务、共用中心网格和本台网格开关/颜色；`tianditu（天地图服务）` 保存凭据与天地图缩放；`openfreemap（OpenFreeMap 服务）` 保存 OFM 缩放。两种服务的默认/最小/最大缩放各自为 `3 / 3 / 8`，不跨组回退，停止读取旧 `map（地图显示）` 缩放字段。

## 最近验证：独立缩放（0.0.24）

验证日期：2026-11-17，版本 `0.0.24`。三个设置菜单与“地图显示共用、缩放按服务独立”的结构已实现。沿用 Halo `Setting + ConfigMap（设置与配置）` 和 `ReactiveSettingFetcher（响应式设置读取器）`，已复核 API 合同、查看/编辑鉴权与角色映射保持不变。

`.\gradlew.bat test` 与 `reloadPlugin（插件热重载）` 成功，正式产物为 `build/libs/plugin-qsl-grid-map-0.0.24.jar`，插件已就绪，热重载日志为 `tmp/reload-ofm-zoom.log`。共 35 个 JUnit 用例通过，包括 2 个真实 `JsonMapper（JSON 映射器）` 用例；其中 1 个 JUnit 用例执行 35 个 Node 页面行为用例，两组计数存在包含关系，不重复累计。

浏览器复用已有后台与地图页，确认菜单顺序、字段归属和共用四项设置正确，首选服务保持 OFM。临时将 OFM 默认/最小/最大缩放保存为 `5 / 4 / 6`，刷新后仍保留，天地图仍为 `3 / 3 / 8`；地图从默认级别放大一次达到上界后加号禁用，缩小两次达到下界后减号禁用。验证后已恢复 OFM `3 / 3 / 8` 并保存，重新打开设置与地图确认生效，地图正常显示且无失败提示。截图为 `tmp/ofm-zoom-settings.jpg`。

真实网络验证仅覆盖直接 OFM；自动兜底的不同缩放范围、迟到/空数据与网格保留由 Node 自动化验证。当前开发实例无真实 QSO 业务数据。2026-11-17 用户已恢复 Git 提交、推送及版本发布；本次发布标签为 `v0.0.24`，产物与自动发布状态以 [GitHub Release](https://github.com/bi1kbu/qsl-grid-map/releases/tag/v0.0.24) 及关联工作流为准。

## 上一轮加载状态验证（0.0.23）

验证日期：2026-11-17，版本 `0.0.23`。修复前浏览器出现“地图绘制完成但旧失败提示残留”：地图加载状态已完成，主要资源返回 200，部分地形瓦片耗时约 16～42 秒或记录状态为 0。本次修正加载与错误提示状态，不表示修复外部网络。

`.\gradlew.bat test` 通过 27 个 JUnit 用例，其中 1 个执行 26 个 Node 页面行为用例，两组计数存在包含关系。生产代码与官方事件合同已复核，API、持久化和权限边界保持不变；构建与 `0.0.23` JAR 打包成功。恢复本地开发服务器后，`reloadPlugin -x build（重载已构建版本）` 成功，插件已就绪。

复用原浏览器页面刷新后，真实 OpenFreeMap 地图正常绘制，显示“未配置天地图应用 key，已切换至 OpenFreeMap（开放地图）。”，旧通用失败提示不再残留。另以临时模拟事件验证瓦片错误后，同源同规范坐标的新瓦片对象成功可解除对应提示，通联数据空态仍保留；临时观察器已移除并再次正常刷新复核。截图为 `tmp/openfreemap-recovery.jpg`。正常联网加载与模拟恢复分别验证，不据此认定外网故障已修复。

## 上一轮短链接验证（0.0.22）

验证日期：2026-11-17，版本 `0.0.22`。`.\gradlew.bat test` 通过 27 个 JUnit 用例，包括 16 个过滤器用例；其中 1 个 JUnit 用例执行 16 个 Node 页面行为用例，两组计数存在包含关系。标准 `reloadPlugin（插件热重载）` 成功，其依赖的构建与 JAR 打包通过，插件已就绪。

本地匿名访问 `/gridmap` 返回 302，目标固定为原地图 API；附带跳转地址或筛选查询参数仍返回相同目标且不透传参数，跟随跳转最终返回 200 地图 HTML，无登录跳转。部署上下文前缀、非 GET 方法、相似路径和原页面 API 回归通过自动化测试。已再次核对官方文档与两个精确 GET 路径的安全边界，日志为 `tmp/reload-gridmap-shortlink.log`。完整结果见 `docs/spec/项目信息结构化清单.md`。

## 上一轮 OpenFreeMap 验证（0.0.21）

验证日期：2026-11-17。该版本最终代码修复后 `.\gradlew.bat test` 通过 13 个 JUnit 用例，其中 1 个用例执行了 16 个 Node 页面行为用例，两组计数存在包含关系。`haloServer（开发服务器）` 所依赖的构建已成功生成 `build/libs/plugin-qsl-grid-map-0.0.21.jar`。

浏览器验证使用 Java 实际页面渲染结果与本地合成 QSO 数据：无效天地图应用令牌触发自动切换，真实 OpenFreeMap 底图和署名正常显示，继续缩放、点击 `OM89（本台网格）` 弹窗通过；直接首选 OpenFreeMap 时正常显示且无失败提示，浏览器控制台无错误。截图为 `tmp/openfreemap-fallback.jpg`。这项验证不代表真实业务数据联动。

独立 Halo 2.25 环境已通过官方固定插件路径加载依赖；`reloadPlugin（插件热重载）` 成功，插件已就绪。热重载后公开地图页与 QSO 网格接口均匿名返回 200；浏览器确认未配置天地图应用令牌时直接加载 OpenFreeMap，空数据提示与地图成功提示同时保留。该新建实例没有真实通联记录，弹窗明细交互使用上述合成数据验证。完整结果见 `docs/spec/项目信息结构化清单.md`。

## 历史验证

以下为 2026-06-29 的天地图版本验证记录，不代表本次 OpenFreeMap 兜底已通过验收。

```powershell
.\gradlew.bat test
.\gradlew.bat build
.\gradlew.bat reloadPlugin
```

三项均已通过。

当时的联动状态：`qsl-management` 公开网格接口匿名访问通过；本插件页面接口匿名公开访问通过，浏览器页面固定请求 `sceneType=QSO` 的数据接口并返回 200。配置天地图应用 key 后，天地图矢量底图与注记瓦片均返回 200，默认中心网格为 `OM89`、默认缩放级别为 `3`，缩放范围由后台配置控制，数据加载后不再自动 `fitBounds（适配范围）` 覆盖后台默认视图；本台网格可通过后台开关控制是否显示，开启时使用后台可配置颜色的虚线边框标识，点击后弹窗显示本台网格名称。当时记录的默认视图截图文件为 `tmp/qsl-grid-map-default-view-om89-no-fitbounds.png`，确认页面仅保留全窗口地图面板，无筛选面板、右侧清单或前台缩放测试控件。编辑器 UI 扩展生成 `src/main/resources/ui/main.js` 与 `src/main/resources/ui/style.css`，用于在 `/` 菜单中插入 QSL 通联网格地图预览卡片，当时记录的截图文件为 `tmp/qsl-grid-map-editor-preview-card.png`。这些临时截图不随仓库交付。
