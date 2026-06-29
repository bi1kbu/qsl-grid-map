# QSL 通联网格地图项目文档索引

本文档是 `qsl-grid-map` 插件的结构化文档入口。

## 约束性文档

1. `agents.md`：仓库协作、Halo2 插件开发、安全、构建与文档同步约定。
2. `docs/spec/QSL网格地图插件开发方案.md`：第一版产品边界、API 合同、安全设计、短码方案与验收标准。

## 输出性文档

1. `docs/spec/项目信息结构化清单.md`：当前工程、接口、短码、依赖与验证状态清单。

## 当前实现摘要

1. 插件标识：`qsl-grid-map`
2. 插件显示名称：`QSL 通联网格地图`
3. 当前版本：`0.0.13`
4. Halo 目标版本：`>=2.25.0`
5. 强依赖插件：`qsl-management >=2.3.21`
6. 唯一插件 API：`GET /apis/qsl-grid-map.bi1kbu.com/v1alpha1/map/page`
7. 数据来源：`GET /apis/api.qsl-management.bi1kbu.com/v1alpha1/qso-public/grids`
8. 短码：`[qsl-grid-map]`

## 最近验证

验证日期：2026-06-29

```powershell
.\gradlew.bat test
.\gradlew.bat build
.\gradlew.bat reloadPlugin
```

三项均已通过。

运行联动状态：`qsl-management` 公开网格接口匿名访问已通过；本插件页面接口匿名公开访问已通过，浏览器页面固定请求 `sceneType=QSO` 的数据接口并返回 200。当前已配置天地图应用 key，天地图矢量底图与注记瓦片均返回 200，默认中心网格为 `OM89`、默认缩放级别为 `3`，缩放范围由后台配置控制。截图已确认页面仅保留全窗口地图面板，无筛选面板、右侧清单或前台缩放测试控件。
