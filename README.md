# 业余无线电 GardMap 通联网格地图

业余无线电 GardMap 通联网格地图是一个 Halo 2 前台展示插件，用于把 `qsl-management` 已公开的通联网格数据展示为交互地图。

## 简介

本插件提供公开地图页面、短码嵌入能力和地图服务后台配置，支持天地图优先、失败后自动切换 OpenFreeMap，也可直接选择 OpenFreeMap。不维护独立通联数据，不创建角色模板，不提供自定义写入接口。

公开页面短链接：

```text
/gridmap
```

访问短链接会跳转到同站点的完整地图页面地址。若 Halo 部署在子路径下，请在短链接前保留该部署前缀，例如 `/blog/gridmap`。短链接不透传查询参数。

完整页面地址：

```text
/apis/qsl-grid-map.bi1kbu.com/v1alpha1/map/page
```

短码：

```text
[qsl-grid-map]
```

数据来源：

```text
/apis/api.qsl-management.bi1kbu.com/v1alpha1/qso-public/grids
```

插件依赖：

```text
qsl-management >=2.3.21
```

## 配置

安装并启用插件后，后台插件设置按顺序分为三个菜单：

| 菜单 | 配置内容 |
| --- | --- |
| 地图显示 | `provider（首选地图服务）`、`defaultCenterGrid（默认中心网格）`、`showHomeGrid（显示本台网格位置）`、`homeGridBorderColor（本台网格边框颜色）`，两种地图共用 |
| 天地图服务 | `appKey（应用 key）`、`secretKey（密钥）`，以及天地图自己的默认、最小、最大缩放级别 |
| OpenFreeMap 服务 | OFM 自己的默认、最小、最大缩放级别，无需密钥 |

首选服务默认 `tianditu（天地图）`，可改为 `openfreemap（OpenFreeMap）`。共用中心网格默认 `OM89`，本台网格默认显示，边框颜色默认 `#dc2626`。

两种服务分别保存以下缩放参数，互不覆盖：

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `defaultZoom（默认缩放级别）` | `3` | 页面首次打开的缩放级别 |
| `minZoom（最小缩放级别）` | `3` | 允许缩小到的最小级别 |
| `maxZoom（最大缩放级别）` | `8` | 允许放大到的最大级别 |

缩放级别允许 `1～18`，默认缩放会限制在该服务的有效范围内。未配置的服务使用自身默认值，不读取另一服务或旧 `map（地图显示）` 组中的缩放字段。升级后请在对应服务菜单重新保存需要的缩放参数；共用中心网格和本台网格设置保留。设置由 Halo 持久化，保存后重新打开地图即可生效。

天地图 `appKey（应用 key）` 可在[天地图服务中心](https://lbs.tianditu.gov.cn/)申请，用作瓦片服务的 `tk（应用令牌）` 参数。`secretKey（密钥）` 为可选，不输出到前台页面，也不发送给 OpenFreeMap。

直接首选 OpenFreeMap，或未配置天地图应用令牌时，初始视图使用共用中心网格及 OFM 的默认缩放。首选天地图时，底图或注记任一瓦片报错，或任一轮瓦片加载超过 15 秒，页面自动切换到 OpenFreeMap 的 Liberty 样式。切换保留当前中心点、通联网格与本台网格；当前缩放超出 OFM 范围时调整到最近边界。数据加载完成或返回空数据均不会重置当前视图。当前页面不会反复切回天地图，刷新后重新按后台首选服务加载。

OpenFreeMap 通过 MapLibre GL Leaflet 绘制，需要浏览器支持 WebGL，并能访问 `cdn.jsdelivr.net` 和 `tiles.openfreemap.org`。相关脚本和样式按需加载，每项组件资源加载限时 15 秒。地图首次绘制超过 20 秒时提示加载较慢并继续等待，完成绘制后自动清除慢加载提示。

地图初始化、样式、瓦片和 WebGL 故障使用分类中文提示；单个资源失败独立记录，只有对应资源恢复后才清除，不会因为其他瓦片成功而掩盖问题。地图服务提示与通联网格数据提示分别维护，数据加载成功不会清除地图故障提示。这些状态处理不改变外部地图服务的网络速度或可用性。

## 开发环境

- Java 21+
- Node.js 18+
- pnpm
- Halo >=2.25.0

## 开发

```bash
# 启用插件
./gradlew haloServer
```

开发服务器默认使用独立容器 `halo-qsl-grid-map-development`，访问地址：

```text
http://localhost:8091
```

## 构建

```bash
./gradlew build
```

构建完成后，可以在 `build/libs` 目录找到插件 jar 文件。

当前产物示例：

```text
build/libs/plugin-qsl-grid-map-0.0.24.jar
```

## 许可证

[GPL-3.0](./LICENSE) © bi1kbu 
