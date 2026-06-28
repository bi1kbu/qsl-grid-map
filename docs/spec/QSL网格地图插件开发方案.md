# QSL 网格地图插件开发方案

## 1. 方案状态

本文档定义 `qsl-grid-map` 插件第一版开发边界，作为进入编码前的设计基线。

核验日期：2026-06-26

目标：开发一个基于 Halo 2 与 `qsl-management` 的业余无线电通联网格前台显示插件，地图展示效果参考 `stephenhouser/qso-mapper`，但数据来源直接使用 `qsl-management` 已提供的公开网格接口。

## 2. 官方与参考来源

1. Halo 插件入门：`https://docs.halo.run/developer-guide/plugin/hello-world`
2. Halo UI 入口：`https://docs.halo.run/developer-guide/plugin/basics/ui/entry`
3. Halo Console 路由定义：`https://docs.halo.run/developer-guide/plugin/api-reference/ui/route`
4. Halo UI API 请求：`https://docs.halo.run/developer-guide/plugin/api-reference/ui/api-request`
5. Halo 角色模板与权限控制：`https://docs.halo.run/developer-guide/plugin/security/role-template`
6. Halo 插件依赖：`https://docs.halo.run/developer-guide/plugin/interaction/dependency`
7. Halo 插件 API 变更记录：`https://docs.halo.run/developer-guide/plugin/api-changelog`
8. `qsl-management` 项目：`https://github.com/bi1kbu/qsl-management`
9. `qso-mapper` 项目：`https://github.com/stephenhouser/qso-mapper`

## 3. 项目定位

插件标识：`qsl-grid-map`

插件显示名称：`QSL 通联网格地图`

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

不得新增以下接口：

1. 不新增 `/grids` 代理接口。
2. 不新增 `/settings` 配置接口。
3. 不新增 `/cache/refresh` 缓存接口。
4. 不新增任何 `POST`、`PUT`、`PATCH`、`DELETE` 写入接口。

页面参数建议：

| 参数 | 含义 | 说明 |
| --- | --- | --- |
| `sceneType` | 场景类型 | 可选，传递给 `qsl-management`，仅允许 `QSO`、`SWL` |
| `dateFrom` | 开始日期 | 可选，格式为 `yyyy-MM-dd` |
| `dateTo` | 结束日期 | 可选，格式为 `yyyy-MM-dd` |
| `grid` | 网格筛选 | 可选，四位 Maidenhead 网格 |
| `limit` | 明细数量上限 | 可选，默认 `500`，最大 `2000` |
| `embed` | 嵌入模式 | 可选，短码嵌入时使用 |

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
4. 短码参数只拼接为页面查询参数，不直接访问业务数据。

短码参数建议：

```text
[qsl-grid-map sceneType="QSO" dateFrom="2026-01-01" dateTo="2026-12-31" limit="500"]
```

## 7. 页面功能

地图渲染参考 `qso-mapper` 的交互风格，采用 Leaflet + OpenStreetMap 作为第一版实现方向。

核心功能：

1. 显示四位 Maidenhead 网格。
2. 在地图上绘制网格范围或网格中心标记。
3. 点击网格后显示呼号集合、通联数量、模式、频率、频段、日期等公开字段。
4. 提供通联明细列表。
5. 支持 URL 查询参数筛选。
6. 支持嵌入模式，适配 Halo 内容页宽度。
7. 空数据、接口错误、限流错误需要有中文提示。

第一版默认地图瓦片：

```text
OpenStreetMap
```

第一版不提供 MapBox Token 配置，避免产生配置持久化和写入接口。

## 8. 数据来源

数据唯一来源为 `qsl-management` 公开接口：

```text
GET /apis/api.qsl-management.bi1kbu.com/v1alpha1/qso-public/grids
```

已确认的接口约束：

1. 匿名可访问。
2. 支持 `sceneType/dateFrom/dateTo/grid/limit` 查询参数。
3. 按对方四位 Maidenhead 网格聚合。
4. 从 `QsoRecord.spec.qth` 读取对方 QTH。
5. 仅当 QTH 整体为 4、6、8 位 Maidenhead 网格时纳入清单。
6. 6 位或 8 位网格统一截取前四位。
7. 返回去重呼号集合与通联明细。
8. 明细字段仅包含呼号、日期、时间、时区、模式、频率、频段等公开字段。
9. 不返回地址、备注、本台信息等敏感字段。
10. `limit` 默认 `500`，最大 `2000`。

本插件不得绕过该公开接口直接读取 `qsl-management` 的 Extension 数据，除非后续重新评审并更新本文档。

## 9. 安全设计

第一版安全边界：

1. 本插件不创建角色模板。
2. 本插件不创建写入权限。
3. 本插件不创建后台管理接口。
4. 本插件只提供公开只读页面。
5. 页面只读取 `qsl-management` 已公开的网格数据。
6. 页面参数必须做前端白名单处理，避免把无关参数透传给数据接口。
7. 页面展示字段必须限制在 `qsl-management` 公开接口返回字段内。
8. 不展示地址、电话、邮箱、通信备注、本台配置、卡片局地址、系统参数等敏感信息。

攻击面控制：

1. 不提供服务端代理，降低 SSRF 风险。
2. 不持久化第三方地图 Token，降低凭据泄露风险。
3. 不提供写接口，避免越权写入风险。
4. 嵌入页面不允许执行用户输入的脚本内容。
5. 错误信息使用中文业务提示，不输出服务端堆栈。

## 10. 权限与 RBAC

第一版不创建角色模板。

原因：

1. 无后台管理页。
2. 无私有查询接口。
3. 无写入接口。
4. 展示数据来自 `qsl-management` 已授权公开的匿名接口。

若后续增加后台配置、缓存刷新、私有统计或数据导出能力，必须重新设计权限节点，至少区分 `view` 与 `edit`，并补充 Role 模板与服务端鉴权。

## 11. 持久化约定

第一版不新增本插件自有持久化模型。

原因：

1. 地图配置使用代码默认值。
2. 展示参数通过 URL 或短码参数传入。
3. 通联网格数据由 `qsl-management` 持久化和聚合。

若后续需要站点级默认配置，例如默认中心点、默认缩放、默认场景、地图瓦片来源或嵌入高度，必须新增持久化设计，并同步增加只读/写入 API、权限节点和文档。

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

### 12.3 地图渲染

1. 引入 Leaflet。
2. 实现 Maidenhead 四位网格到经纬度边界的转换。
3. 绘制网格多边形或中心标记。
4. 根据通联数量设置视觉强度。
5. 点击网格展示明细。
6. 提供列表与地图联动。

### 12.4 短码

1. 注册 `[qsl-grid-map]` 短码。
2. 短码输出嵌入页面。
3. 短码参数映射为 `map/page` 查询参数。
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
5. 核对没有写入接口和角色模板。
6. 构建通过后按约定执行插件热重载。

## 13. 验收标准

1. 访问 `/apis/qsl-grid-map.bi1kbu.com/v1alpha1/map/page` 可打开公开地图页面。
2. 页面能读取 `qsl-management` 的 `/qso-public/grids` 数据并完成地图展示。
3. `[qsl-grid-map]` 短码能在 Halo 内容中嵌入同一地图页面。
4. 插件未创建角色模板。
5. 插件未创建任何写入接口。
6. 插件未创建 `/grids`、`/settings`、`/cache/refresh` 等额外接口。
7. 插件声明 `qsl-management` 强依赖。
8. 页面不展示敏感字段。
9. 空数据、接口失败、限流失败均有中文提示。
10. 构建和热重载完成后再次核对与 Halo 官方文档一致。

## 14. 暂不实现内容

1. 后台配置页。
2. 角色模板。
3. 写入接口。
4. 本插件自有网格数据接口。
5. 本插件自有数据缓存。
6. MapBox Token 配置。
7. 私有统计接口。
8. 独立 QSO、ADIF 或 QSL 数据管理。

上述能力如需增加，必须先更新本文档并重新确认设计基线。
